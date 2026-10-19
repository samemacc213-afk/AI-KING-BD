package com.aikingbd.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.graphics.Paint
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
import android.media.MediaPlayer
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.widget.ImageView
import androidx.core.content.FileProvider
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
        
        prefs = Prefs(this)
        setupFlashCard()

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

        setupVoiceMessage()

        val sk = findViewById<EditText>(R.id.etSarvamKey)
        sk.setText(prefs.sarvamKey)
        sk.doAfterTextChanged { prefs.sarvamKey = it?.toString()?.trim() ?: "" }

        setupLinks()
        setupEleven()

        setupVoicePickers()
    }

    override fun onStart() {
        super.onStart()
        AppOpenManager.showIfReady(this)
    }

    override fun onResume() {
        super.onResume()
        refreshFlashStatus()
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

    private var voicePlayer: MediaPlayer? = null

    private fun setupVoicePickers() {
        val floatBtn = findViewById<TextView>(R.id.btnFloatVoice)
        val msgBtn = findViewById<TextView>(R.id.btnMsgVoice)
        if (prefs.voiceId.isEmpty()) prefs.voiceId = Voices.list[0].id
        if (prefs.msgVoiceId.isEmpty() || Voices.msgList.none { it.id == prefs.msgVoiceId }) prefs.msgVoiceId = Voices.msgList[0].id
        floatBtn.text = Voices.find(prefs.voiceId).label + "  ▼"
        msgBtn.text = Voices.findMsg(prefs.msgVoiceId).label + "  ▼"

        floatBtn.setOnClickListener {
            pickVoice("Floating button voice", prefs.voiceId) { id ->
                prefs.voiceId = id
                floatBtn.text = Voices.find(id).label + "  ▼"
                if (prefs.apiKey.isBlank()) {
                    Toast.makeText(this, "Add your free Gemini key to hear this voice", Toast.LENGTH_LONG).show()
                } else {
                    Speaker.speak(this, SAMPLE)
                }
            }
        }
        msgBtn.setOnClickListener {
            pickVoice("Voice message voice", prefs.msgVoiceId, Voices.msgList) { id ->
                prefs.msgVoiceId = id
                msgBtn.text = Voices.findMsg(id).label + "  ▼"
            }
        }
        findViewById<TextView>(R.id.btnPlayVoice).setOnClickListener { playLastVoice() }
    }

    private fun pickVoice(title: String, current: String, opts: List<VoiceOption> = Voices.list, onPicked: (String) -> Unit) {
        val labels = opts.map { it.label }.toTypedArray()
        val checked = opts.indexOfFirst { it.id == current }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                onPicked(opts[which].id)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playLastVoice() {
        val v = lastVoice ?: return
        val btn = findViewById<TextView>(R.id.btnPlayVoice)
        val playing = voicePlayer
        if (playing != null) {
            try { playing.stop() } catch (_: Exception) {}
            try { playing.release() } catch (_: Exception) {}
            voicePlayer = null
            btn.text = "▶ PLAY VOICE"
            return
        }
        try {
            val mp = MediaPlayer()
            mp.setDataSource(v.file.absolutePath)
            mp.setOnCompletionListener {
                try { it.release() } catch (_: Exception) {}
                voicePlayer = null
                btn.text = "▶ PLAY VOICE"
            }
            mp.prepare()
            mp.start()
            voicePlayer = mp
            btn.text = "■ STOP"
        } catch (e: Exception) {
            Toast.makeText(this, "Could not play this voice", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try { voicePlayer?.release() } catch (_: Exception) {}
        voicePlayer = null
        super.onDestroy()
    }

    private var lastVoice: VoiceResult? = null

    private fun setupVoiceMessage() {
        val msg = findViewById<EditText>(R.id.etMessage)
        val btn = findViewById<TextView>(R.id.btnCreateVoice)
        val status = findViewById<TextView>(R.id.tvVoiceStatus)
        val row = findViewById<LinearLayout>(R.id.shareRow)

        btn.setOnClickListener {
            val text = msg.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Type a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btn.isEnabled = false
            btn.alpha = 0.5f
            row.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.text = "Making voice, please wait..."
            findViewById<TextView>(R.id.btnPlayVoice).visibility = View.GONE
            VoiceMaker.make(this, text) { result ->
                btn.isEnabled = true
                btn.alpha = 1f
                if (result == null) {
                    status.text = "Could not make voice. Check internet and try again."
                } else {
                    lastVoice = result
                    findViewById<TextView>(R.id.btnPlayVoice).visibility = View.VISIBLE
                    status.text = if (result.note.isNotEmpty()) "Backup voice used - " + result.note else "Voice ready. Tap an app to send:"
                    showShareRow(row, result)
                }
            }
        }
    }

    private fun appIcon(pkg: String) = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    private fun showShareRow(row: LinearLayout, result: VoiceResult) {
        row.removeAllViews()
        row.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density
        val size = (52 * dp).toInt()
        val gap = (14 * dp).toInt()

        val apps = listOf("com.whatsapp", "com.imo.android.imoim", "com.facebook.orca")
        for (pkg in apps) {
            val icon = appIcon(pkg) ?: continue
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            iv.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
            iv.setOnClickListener { sendVoice(result, pkg) }
            row.addView(iv)
        }

        val more = TextView(this)
        more.text = "MORE"
        more.setTextColor(ContextCompat.getColor(this, R.color.navy_text))
        more.textSize = 14f
        more.typeface = Typeface.SERIF
        more.gravity = Gravity.CENTER
        more.setBackgroundResource(R.drawable.bg_gold_button)
        more.layoutParams = LinearLayout.LayoutParams((84 * dp).toInt(), (44 * dp).toInt())
        more.setOnClickListener { sendVoice(result, null) }
        row.addView(more)
    }

    private fun sendVoice(result: VoiceResult, pkg: String?) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", result.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = result.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (pkg != null) setPackage(pkg)
            }
            if (pkg == null) {
                startActivity(Intent.createChooser(intent, "Send voice"))
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open that app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectVoice(id: String) {
        prefs.voiceId = id
        radios.forEach { (k, rb) -> rb.isChecked = (k == id) }
    }

    private fun setupLinks() {
        for ((linkId, url) in listOf(
            R.id.btnGeminiLink to "https://aistudio.google.com/apikey",
            R.id.btnSarvamLink to "https://indus.sarvam.ai",
            R.id.btnElLink to "https://elevenlabs.io"
        )) {
            val tv = findViewById<TextView>(linkId)
            tv.paintFlags = tv.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            tv.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupEleven() {
        val keyBox = findViewById<EditText>(R.id.etElevenKey)
        val voiceBtn = findViewById<TextView>(R.id.btnElVoice)
        keyBox.setText(prefs.elevenKey)
        keyBox.doAfterTextChanged { prefs.elevenKey = it?.toString()?.trim() ?: "" }

        fun label() {
            voiceBtn.text = if (prefs.elVoiceName.isEmpty()) "ElevenLabs voice: tap to choose  ▼"
            else "ElevenLabs: " + prefs.elVoiceName + "  ▼"
        }
        label()

        voiceBtn.setOnClickListener {
            val key = prefs.elevenKey
            if (key.isBlank()) {
                Toast.makeText(this, "Paste your ElevenLabs key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            voiceBtn.text = "Loading voices..."
            Thread {
                val all = ElevenVoice.listVoices(key)
                runOnUiThread {
                    label()
                    if (all == null) {
                        Toast.makeText(this, ElevenVoice.lastError.ifEmpty { "Could not load voices" }, Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val female = all.filter { it.female }
                    val list = if (female.isNotEmpty()) female else all
                    if (list.isEmpty()) {
                        Toast.makeText(this, "No voices found", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val names = list.map { it.name }.toTypedArray()
                    val checked = list.indexOfFirst { it.id == prefs.elVoiceId }.coerceAtLeast(0)
                    AlertDialog.Builder(this)
                        .setTitle("ElevenLabs voices")
                        .setSingleChoiceItems(names, checked) { d, which ->
                            prefs.elVoiceId = list[which].id
                            prefs.elVoiceName = list[which].name
                            label()
                            d.dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }.start()
        }
    }

    private fun flashAccessGranted(): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openFlashAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshFlashStatus() {
        val tv = findViewById<TextView>(R.id.tvFlashStatus)
        tv.text = if (flashAccessGranted()) "Notification access: allowed" else "Notification access: not allowed"
    }

    private fun setupFlashCard() {
        val sw = findViewById<SwitchCompat>(R.id.switchFlash)
        sw.isChecked = prefs.flashOn
        sw.setOnCheckedChangeListener { _, on ->
            prefs.flashOn = on
            if (on) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 21)
                }
                if (!flashAccessGranted()) {
                    Toast.makeText(this, "Turn on AI KING BD in the list", Toast.LENGTH_LONG).show()
                    openFlashAccess()
                }
            } else {
                Flash.stop()
            }
            refreshFlashStatus()
        }
        findViewById<TextView>(R.id.btnFlashAccess).setOnClickListener { openFlashAccess() }
        findViewById<TextView>(R.id.btnFlashTest).setOnClickListener { Flash.blink(this, 5) }
        refreshFlashStatus()
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
