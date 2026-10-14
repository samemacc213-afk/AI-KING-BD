package com.aikingbd.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.widget.Toast
import java.util.Locale

object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var forceLocal = false
    private val main = Handler(Looper.getMainLooper())

    fun speak(context: Context, text: String) {
        val app = context.applicationContext
        OnlineVoice.speak(app, text) {
            main.post {
                toast(app, "Internet voice not available, using phone voice")
                speakLocal(app, text)
            }
        }
    }

    fun speakLocal(context: Context, text: String) {
        val app = context.applicationContext
        forceLocal = false
        if (tts == null) {
            pending = text
            create(app, true)
        } else if (ready) {
            say(app, text)
        } else {
            pending = text
        }
    }

    private fun create(app: Context, google: Boolean) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                pending?.let { say(app, it) }
                pending = null
            } else if (google) {
                tts?.shutdown()
                tts = null
                ready = false
                create(app, false)
            } else {
                toast(app, "Text-to-speech engine not available")
            }
        }
        tts = if (google) {
            TextToSpeech(app, listener, "com.google.android.tts")
        } else {
            TextToSpeech(app, listener)
        }
    }

    private fun say(ctx: Context, text: String) {
        val t = tts ?: return
        val isBangla = text.any { it in '\u0980'..'\u09FF' }
        val locale = if (isBangla) Locale("bn", "BD") else Locale.US

        val result = t.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            toast(ctx, "Install Bangla voice in Google Text-to-speech settings")
        }
        pickVoice(t, locale)

        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!forceLocal) {
                    forceLocal = true
                    main.post { say(ctx, text) }
                }
            }
        })

        val p = Prefs(ctx)
        t.setPitch(p.pitch)
        t.setSpeechRate(p.speed)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aikb")
    }

    private fun pickVoice(t: TextToSpeech, locale: Locale) {
        try {
            val list: List<Voice> = t.voices
                ?.filter {
                    it.locale.language == locale.language &&
                        !it.features.contains("notInstalled") &&
                        (!forceLocal || !it.isNetworkConnectionRequired)
                } ?: return
            val best = list.sortedWith(
                compareByDescending<Voice> { it.locale.country == locale.country }
                    .thenByDescending { it.quality }
                    .thenByDescending { it.isNetworkConnectionRequired }
            ).firstOrNull() ?: return
            t.voice = best
        } catch (_: Exception) {
        }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
