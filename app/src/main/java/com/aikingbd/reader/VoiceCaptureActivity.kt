package com.aikingbd.reader

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat

// Invisible screen that lives for less than a second. Android only allows starting the
// microphone while one of our screens is visible, so this screen starts the recorder and
// closes itself at once. It never stays on top, so it cannot block typing or touches.
class VoiceCaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            go()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            go()
        } else {
            Toast.makeText(this, "Microphone permission is needed to record", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun go() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, VoiceRecordService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start the recorder", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (VoiceCapture.startRecording(this)) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            } catch (e: Exception) {
            }
            Toast.makeText(this, "Recording... speak now, then tap the text button", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 450)
        } else {
            try {
                stopService(Intent(this, VoiceRecordService::class.java))
            } catch (e: Exception) {
            }
            finish()
        }
    }
}
