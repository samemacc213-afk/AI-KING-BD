package com.aikingbd.reader

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var started = 0L
    private var finishedFlow = false
    private var adStarted = false

    private val tick = object : Runnable {
        override fun run() {
            if (finishedFlow) return
            val waited = SystemClock.uptimeMillis() - started
            if (!adStarted && AppOpenManager.isReady() && waited >= 400) {
                adStarted = true
                AppOpenManager.showIfReady(this@SplashActivity) { openMain() }
                return
            }
            if (!adStarted && waited >= 3500) {
                openMain()
                return
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.navy))

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo)
        logo.layoutParams = LinearLayout.LayoutParams((110 * dp).toInt(), (110 * dp).toInt())

        val name = TextView(this)
        name.text = "AI KING BD"
        name.setTextColor(Color.WHITE)
        name.textSize = 28f
        name.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        name.letterSpacing = 0.08f
        name.gravity = Gravity.CENTER
        name.setPadding(0, (18 * dp).toInt(), 0, 0)

        root.addView(logo)
        root.addView(name)
        setContentView(root)

        started = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    private fun openMain() {
        if (finishedFlow) return
        finishedFlow = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
