package com.aikingbd.reader

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private var wm: WindowManager? = null
    private var button: ImageView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (button == null) showButton()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showButton() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val density = resources.displayMetrics.density
        val size = (44 * density).toInt()
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

        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_float_button)
            contentDescription = "Read copied text"
        }
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
                            lp.y = (startY + dy).toInt()
                            manager.updateViewLayout(iv, lp)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            readClipboardAloud()
                        } else {
                            val screenW = resources.displayMetrics.widthPixels
                            lp.x = if (lp.x + size / 2 < screenW / 2) 0 else screenW - size
                            manager.updateViewLayout(iv, lp)
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
        button = iv
    }

    private fun readClipboardAloud() {
        val i = Intent(this, ClipboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(i)
    }

    override fun onDestroy() {
        button?.let { wm?.removeView(it) }
        button = null
        super.onDestroy()
    }
}
