package com.aikingbd.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.MobileAds
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private companion object {
        const val SAMPLE = "আমি আপনার কপি করা লেখা পড়ে শোনাব।"
    }

    private lateinit var prefs: Prefs
    private lateinit var switchFloat: SwitchCompat
    private lateinit var tvStatus: TextView
    private lateinit var tvPermission: TextView
    private val radios = mutableMapOf<String, RadioButton>()

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) savePhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        MobileAds.initialize(this) { runOnUiThread { AppOpenManager.showOnLaunch(this) } }
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
            if (prefs.apiKey.isBlank()) {
                Toast.makeText(this, "Add your free Gemini key in card 7 for the female voices", Toast.LENGTH_LONG).show()
            }
            Speaker.speak(this, SAMPLE)
        }
        findViewById<LinearLayout>(R.id.btnPermission).setOnClickListener { openOverlaySettings() }

        findViewById<TextView>(R.id.btnPhoto).setOnClickListener { pickImage.launch("image/*") }
        findViewById<TextView>(R.id.btnPhotoReset).setOnClickListener {
            File(filesDir, "button_photo.png").delete()
            prefs.hasPhoto = false
            refreshButton()
            Toast.makeText(this, "Emoji button restored", Toast.LENGTH_SHORT).show()
        }

        val et = findViewById<EditText>(R.id.etApiKey)
        et.setText(prefs.apiKey)
        et.doAfterTextChanged { prefs.apiKey = it?.toString()?.trim() ?: "" }

        buildVoiceList()
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

    private fun refreshButton() {
        if (prefs.enabled && Settings.canDrawOverlays(this)) {
            val i = Intent(this, FloatingService::class.java).setAction(FloatingService.ACTION_REFRESH)
            ContextCompat.startForegroundService(this, i)
        }
    }

    private fun savePhoto(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) {
                Toast.makeText(this, "Could not use this photo", Toast.LENGTH_SHORT).show()
                return
            }
            val side = min(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
            File(filesDir, "button_photo.png").outputStream().use {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            prefs.hasPhoto = true
            refreshButton()
            Toast.makeText(this, "Photo set on your floating button", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not use this photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildVoiceList() {
        val box = findViewById<LinearLayout>(R.id.voiceList)
        val gold = ContextCompat.getColor(this, R.color.gold)
        val navy = ContextCompat.getColor(this, R.color.navy_text)
        val dp = resources.displayMetrics.density
        if (prefs.voiceId.isEmpty()) prefs.voiceId = Voices.list[0].id

        for (v in Voices.list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
            }
            val rb = RadioButton(this).apply {
                isChecked = (v.id == prefs.voiceId)
                buttonTintList = ColorStateList.valueOf(gold)
                setOnClickListener { selectVoice(v.id) }
            }
            val label = TextView(this).apply {
                text = v.label
                setTextColor(navy)
                textSize = 15f
                typeface = Typeface.SERIF
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { selectVoice(v.id) }
            }
            val play = TextView(this).apply {
                text = "▶ PLAY"
                setTextColor(navy)
                textSize = 13f
                typeface = Typeface.SERIF
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_gold_button)
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener {
                    selectVoice(v.id)
                    if (prefs.apiKey.isBlank()) {
                        Toast.makeText(this@MainActivity, "Add your free Gemini key below to hear this voice", Toast.LENGTH_LONG).show()
                    }
                    Speaker.speak(this@MainActivity, SAMPLE)
                }
            }
            row.addView(rb)
            row.addView(label)
            row.addView(play)
            box.addView(row)
            radios[v.id] = rb
        }
    }

    private fun selectVoice(id: String) {
        prefs.voiceId = id
        radios.forEach { (k, rb) -> rb.isChecked = (k == id) }
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
