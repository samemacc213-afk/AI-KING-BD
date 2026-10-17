package com.aikingbd.reader

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.aikingbd.reader.REFRESH"
    }

    private var wm: WindowManager? = null
    private var button: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (button == null) {
            showButton()
        } else if (intent?.action == ACTION_REFRESH) {
            applyImage()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "aikb_float"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Floating button", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_float_button)
            .setContentTitle("AI KING BD")
            .setContentText("Floating button is on")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun applyImage() {
        val iv = button ?: return
        val prefs = Prefs(this)
        val f = File(filesDir, "button_photo.png")
        if (prefs.hasPhoto && f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) {
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                iv.clipToOutline = true
                return
            }
        }
        iv.clipToOutline = false
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setImageResource(R.drawable.ic_emoji_button)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showButton() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        val prefs = Prefs(this)

        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.btnX
            y = prefs.btnY
        }
        params = lp

        val iv = ImageView(this).apply {
            contentDescription = "Read copied text"
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        button = iv
        applyImage()

        val slop = ViewConfiguration.get(this).scaledTouchSlop

        iv.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var downX = 0f
            var downY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        downX = e.rawX; downY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) {
                            lp.x = (startX + dx).toInt()
                                .coerceIn(0, resources.displayMetrics.widthPixels - size)
                            lp.y = (startY + dy).toInt()
                                .coerceIn(0, resources.displayMetrics.heightPixels - size)
                            manager.updateViewLayout(iv, lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            onTap()
                        } else {
                            prefs.btnX = lp.x
                            prefs.btnY = lp.y
                        }
                        return true
                    }
                }
                return false
            }
        })

        manager.addView(iv, lp)
    }

    private fun onTap() {
        if (Speaker.isSpeaking()) {
            Speaker.stop()
        } else {
            readClipboardAloud()
        }
    }

    private fun readClip(): String? {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun readClipboardAloud() {
        val manager = wm ?: return
        val iv = button ?: return
        val lp = params ?: return

        var finished = false
        lateinit var listener: ViewTreeObserver.OnWindowFocusChangeListener

        fun finish(text: String?) {
            if (finished) return
            finished = true
            try { iv.viewTreeObserver.removeOnWindowFocusChangeListener(listener) } catch (_: Exception) {}
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            try { manager.updateViewLayout(iv, lp) } catch (_: Exception) {}
            if (text == null) {
                Toast.makeText(this, "Nothing copied yet", Toast.LENGTH_SHORT).show()
            } else {
                Speaker.speak(this, text)
            }
        }

        listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                val t = readClip()
                if (t != null) finish(t)
            }
        }
        iv.viewTreeObserver.addOnWindowFocusChangeListener(listener)

        // Briefly make the button window focusable so Android allows clipboard reading
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        manager.updateViewLayout(iv, lp)

        handler.postDelayed({ finish(readClip()) }, 700)
    }

    override fun onDestroy() {
        button?.let { wm?.removeView(it) }
        button = null
        super.onDestroy()
    }
}
