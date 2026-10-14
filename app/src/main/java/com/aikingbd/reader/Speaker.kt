package com.aikingbd.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import java.util.Locale

object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())

    fun speak(context: Context, text: String) {
        val app = context.applicationContext
        if (tts == null) {
            pending = text
            tts = TextToSpeech(app) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    pending?.let { say(app, it) }
                    pending = null
                } else {
                    toast(app, "Text-to-speech engine not available")
                }
            }
        } else if (ready) {
            say(app, text)
        } else {
            pending = text
        }
    }

    private fun say(ctx: Context, text: String) {
        val t = tts ?: return
        val isBangla = text.any { it in '\u0980'..'\u09FF' }
        val locale = if (isBangla) Locale("bn", "BD") else Locale.US

        val result = t.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(ctx, "Install this language voice in Google Text-to-speech settings")
        }
        pickFemaleVoice(t, locale)

        val p = Prefs(ctx)
        t.setPitch(p.pitch)
        t.setSpeechRate(p.speed)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aikb")
    }

    private fun pickFemaleVoice(t: TextToSpeech, locale: Locale) {
        try {
            val candidates: List<Voice> = t.voices
                ?.filter { it.locale.language == locale.language && !it.features.contains("notInstalled") }
                ?: return
            if (candidates.isEmpty()) return
            val chosen = candidates.firstOrNull { it.name.contains("female", true) }
                ?: candidates.firstOrNull { !it.name.contains("male", true) }
                ?: candidates.first()
            t.voice = chosen
        } catch (_: Exception) {
        }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
