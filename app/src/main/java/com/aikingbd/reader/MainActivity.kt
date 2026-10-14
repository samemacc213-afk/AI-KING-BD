package com.aikingbd.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var switchFloat: SwitchCompat
    private lateinit var tvStatus: TextView
    private lateinit var tvPermission: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        switchFloat = findViewById(R.id.switchFloat)
        tvStatus = findViewById(R.id.tvStatus)
        tvPermission = findViewById(R.id.tvPermission)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        setupSlider(R.id.seekPitch, R.id.tvPitch, prefs.pitch) { prefs.pitch = it }
        setupSlider(R.id.seekSpeed, R.id.tvSpeed, prefs.speed) { prefs.speed = it }

        switchFloat.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(this)) {
                    switchFloat.isChecked = false
                    Toast.makeText(this, "Allow display over screen first", Toast.LENGTH_LONG).show()
                    openOverlaySettings()
                } else {
                    ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
                    prefs.enabled = true
                    renderStatus(true)
                }
            } else {
                stopService(Intent(this, FloatingService::class.java))
                prefs.enabled = false
                renderStatus(false)
            }
        }

        findViewById<LinearLayout>(R.id.btnTest).setOnClickListener {
            Speaker.speak(this, "আমি এআই কিং বিডি। আপনার কপি করা লেখা আমি পড়ে শোনাব।")
        }
        findViewById<LinearLayout>(R.id.btnPermission).setOnClickListener { openOverlaySettings() }
    }

    override fun onResume() {
        super.onResume()
        val granted = Settings.canDrawOverlays(this)
        tvPermission.text = if (granted) "Overlay Permission • Granted" else "Overlay Permission"
        val on = prefs.enabled && granted
        if (switchFloat.isChecked != on) switchFloat.isChecked = on
        renderStatus(on)
    }

    private fun renderStatus(on: Boolean) {
        tvStatus.text = if (on) "Currently Enabled" else "Currently Disabled"
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun setupSlider(seekId: Int, labelId: Int, value: Float, onChange: (Float) -> Unit) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(labelId)
        seek.progress = ((value - 0.5f) / 0.05f).roundToInt().coerceIn(0, 30)
        label.text = String.format(Locale.US, "%.1f", value)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = 0.5f + progress * 0.05f
                label.text = String.format(Locale.US, "%.1f", v)
                if (fromUser) onChange(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }
}
