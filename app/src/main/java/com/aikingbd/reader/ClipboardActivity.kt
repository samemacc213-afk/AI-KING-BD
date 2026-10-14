package com.aikingbd.reader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ClipboardActivity : AppCompatActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
            } else null

            if (text.isNullOrEmpty()) {
                Toast.makeText(this, "Nothing copied yet", Toast.LENGTH_SHORT).show()
            } else {
                Speaker.speak(this, text)
            }
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
