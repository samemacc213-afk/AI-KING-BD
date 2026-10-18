package com.aikingbd.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

class VoiceResult(val file: File, val mime: String, val note: String = "")

object VoiceMaker {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val MAX_CHARS = 1500

    fun make(ctx: Context, text: String, onDone: (VoiceResult?) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val result = try { build(app, text.take(MAX_CHARS)) } catch (e: Exception) { null }
            main.post { onDone(result) }
        }
    }

    private fun build(ctx: Context, text: String): VoiceResult? {
        val prefs = Prefs(ctx)
        val key = prefs.sarvamKey.trim()
        val speaker = Voices.findMsg(prefs.msgVoiceId).id
        val dir = File(ctx.cacheDir, "voices").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = System.currentTimeMillis()
        var note = ""

        if (key.isNotEmpty()) {
            val wav = SarvamVoice.synth(text, speaker, key)
            if (wav != null && wav.size > 100) {
                val out = File(dir, "AI_KING_voice_$stamp.wav")
                out.writeBytes(wav)
                return VoiceResult(out, "audio/wav")
            }
            note = SarvamVoice.lastError.ifEmpty { "Sarvam voice failed" }
        } else {
            note = "No Sarvam key added"
        }

        val lang = if (text.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
        val bos = ByteArrayOutputStream()
        for (chunk in OnlineVoice.split(text, 180)) {
            val b = OnlineVoice.downloadBytes(chunk, lang) ?: return null
            bos.write(b)
        }
        if (bos.size() == 0) return null
        val out = File(dir, "AI_KING_voice_$stamp.mp3")
        out.writeBytes(bos.toByteArray())
        return VoiceResult(out, "audio/mpeg", note)
    }
}
