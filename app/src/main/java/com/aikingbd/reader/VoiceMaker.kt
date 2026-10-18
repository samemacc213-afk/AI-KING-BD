package com.aikingbd.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

class VoiceResult(val file: File, val mime: String)

object VoiceMaker {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val MAX_CHARS = 1500
    private const val STYLE = "Say this in the warm, soft, natural voice of a 19-year-old young woman speaking Bengali"

    fun make(ctx: Context, text: String, onDone: (VoiceResult?) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val result = try { build(app, text.take(MAX_CHARS)) } catch (e: Exception) { null }
            main.post { onDone(result) }
        }
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    private fun build(ctx: Context, text: String): VoiceResult? {
        val prefs = Prefs(ctx)
        val key = prefs.apiKey.trim()
        val voice = Voices.find(prefs.msgVoiceId).id
        val lang = if (text.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
        val dir = File(ctx.cacheDir, "voices").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = System.currentTimeMillis()

        if (key.isNotEmpty()) {
            val pcmAll = ByteArrayOutputStream()
            var rate = 24000
            var ok = true
            for (chunk in OnlineVoice.split(text, 700)) {
                val (f, code) = OnlineVoice.gemini(ctx, chunk, voice, key, STYLE)
                if (f == null) {
                    ok = false
                    toast(ctx, OnlineVoice.reason(code))
                    break
                }
                val bytes = f.readBytes()
                f.delete()
                if (bytes.size > 44) {
                    rate = (bytes[24].toInt() and 0xff) or
                        ((bytes[25].toInt() and 0xff) shl 8) or
                        ((bytes[26].toInt() and 0xff) shl 16) or
                        ((bytes[27].toInt() and 0xff) shl 24)
                    pcmAll.write(bytes, 44, bytes.size - 44)
                }
            }
            if (ok && pcmAll.size() > 0) {
                val out = File(dir, "AI_KING_voice_$stamp.wav")
                out.writeBytes(OnlineVoice.pcmToWav(pcmAll.toByteArray(), rate))
                return VoiceResult(out, "audio/wav")
            }
        }

        val bos = ByteArrayOutputStream()
        for (chunk in OnlineVoice.split(text, 180)) {
            val b = OnlineVoice.downloadBytes(chunk, lang) ?: return null
            bos.write(b)
        }
        if (bos.size() == 0) return null
        val out = File(dir, "AI_KING_voice_$stamp.mp3")
        out.writeBytes(bos.toByteArray())
        return VoiceResult(out, "audio/mpeg")
    }
}
