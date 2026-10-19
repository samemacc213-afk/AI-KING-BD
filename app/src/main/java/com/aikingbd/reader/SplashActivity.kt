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
            if (!adStarted && AppOpenManager.isReady() && waited >= 500) {
                adStarted = true
                AppOpenManager.showIfReady(this@SplashActivity) { openMain() }
                return
            }
            if (!adStarted && waited >= 3500) {
                openMain()
                return
            }
            handler.postDelayed(this, 120)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@SplashActivity, R.color.navy))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams((110 * dp).toInt(), (110 * dp).toInt())
        }
        val name = TextView(this).apply {
            text = "AI KING BD"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setPadding(0, (18 * dp).toInt(), 0, 0)
        }
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
