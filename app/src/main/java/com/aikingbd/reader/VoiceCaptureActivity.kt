package com.aikingbd.reader

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

// Invisible screen. It only exists so Android lets us use the microphone while the
// floating button is tapped over another app. It never takes touches or focus.
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
        if (VoiceCapture.startRecording(this)) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
            Toast.makeText(this, "Recording... speak now, then tap the text button", Toast.LENGTH_SHORT).show()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        VoiceCapture.hostGone(this)
        super.onDestroy()
    }
}
