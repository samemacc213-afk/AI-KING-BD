package com.aikingbd.reader

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object OnlineVoice {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val GEMINI_MODELS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts")

    @Volatile private var generation = 0
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var active = false

    fun isActive(): Boolean = active

    fun stop() {
        generation++
        active = false
        stopPlayer()
    }

    fun speak(ctx: Context, text: String, onFail: () -> Unit) {
        val gen = ++generation
        active = true
        stopPlayer()
        executor.execute { playAll(ctx.applicationContext, text, gen, onFail) }
    }

    private fun stopPlayer() {
        val p = player
        player = null
        if (p != null) {
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
    }

    private fun playAll(ctx: Context, text: String, gen: Int, onFail: () -> Unit) {
        try {
            playChunks(ctx, text, gen, onFail)
        } finally {
            if (gen == generation) active = false
        }
    }

    private fun notify(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    internal fun reason(code: Int): String = when (code) {
        429 -> "Gemini voice limit reached, using backup voice"
        400, 401, 403 -> "Gemini key problem, using backup voice"
        -1 -> "No internet for Gemini voice, using backup voice"
        else -> "Gemini voice error $code, using backup voice"
    }

    private fun playChunks(ctx: Context, text: String, gen: Int, onFail: () -> Unit) {
        val prefs = Prefs(ctx)
        val lang = if (text.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
        val key = prefs.apiKey.trim()
        val voice = Voices.find(prefs.voiceId).id
        var useGemini = key.isNotEmpty()

        val chunks = split(text, if (useGemini) 700 else 180)
        if (chunks.isEmpty()) return

        fun fetch(chunk: String): File? {
            if (useGemini) {
                val (f, code) = gemini(ctx, chunk, voice, key)
                if (f != null) return f
                useGemini = false
                notify(ctx, reason(code))
            }
            return translateAudio(ctx, chunk, lang)
        }

        var current: File? = fetch(chunks[0])
        if (current == null) {
            if (gen == generation) onFail()
            return
        }

        for (i in chunks.indices) {
            val file = current ?: return
            if (gen != generation) { file.delete(); return }

            val mp = MediaPlayer()
            val done = CountDownLatch(1)
            try {
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { done.countDown() }
                mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
                mp.prepare()
                mp.start()
                try {
                    mp.playbackParams = PlaybackParams().setSpeed(prefs.speed).setPitch(prefs.pitch)
                } catch (_: Exception) {}
                player = mp
            } catch (e: Exception) {
                try { mp.release() } catch (_: Exception) {}
                file.delete()
                return
            }

            current = if (i + 1 < chunks.size) fetch(chunks[i + 1]) else null

            while (!done.await(100, TimeUnit.MILLISECONDS)) {
                if (gen != generation) break
            }
            try { mp.stop() } catch (_: Exception) {}
            try { mp.release() } catch (_: Exception) {}
            if (player === mp) player = null
            file.delete()

            if (i + 1 < chunks.size && current == null) return
        }
    }

    internal fun gemini(ctx: Context, text: String, voice: String, key: String): Pair<File?, Int> {
        var lastCode = 0
        for (model in GEMINI_MODELS) {
            try {
                val body = JSONObject()
                    .put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
                    ))
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject()
                            .put("voiceConfig", JSONObject()
                                .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))

                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 60000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-goog-api-key", key)
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = c.responseCode
                lastCode = code
                if (code == 200) {
                    val resp = c.inputStream.bufferedReader().use { it.readText() }
                    val inline = JSONObject(resp)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0)
                        .getJSONObject("inlineData")
                    val pcm = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                    val mime = inline.optString("mimeType", "")
                    val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                    val f = File(ctx.cacheDir, "g_${System.nanoTime()}.wav")
                    f.writeBytes(pcmToWav(pcm, rate))
                    return Pair(f, 200)
                }
                if (code == 404 || code == 400) continue
                return Pair(null, code)
            } catch (e: Exception) {
                return Pair(null, -1)
            }
        }
        return Pair(null, lastCode)
    }

    internal fun pcmToWav(pcm: ByteArray, rate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun w32(v: Int) {
            out.write(v and 0xff); out.write((v shr 8) and 0xff)
            out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff)
        }
        fun w16(v: Int) {
            out.write(v and 0xff); out.write((v shr 8) and 0xff)
        }
        out.write("RIFF".toByteArray()); w32(pcm.size + 36); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); w32(16); w16(1); w16(1); w32(rate); w32(rate * 2); w16(2); w16(16)
        out.write("data".toByteArray()); w32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    private fun translateAudio(ctx: Context, text: String, lang: String): File? {
        val parts = split(text, 180)
        if (parts.isEmpty()) return null
        val bos = ByteArrayOutputStream()
        for (p in parts) {
            val b = downloadBytes(p, lang) ?: return null
            bos.write(b)
        }
        val f = File(ctx.cacheDir, "t_${System.nanoTime()}.mp3")
        f.writeBytes(bos.toByteArray())
        return f
    }

    internal fun downloadBytes(text: String, lang: String): ByteArray? {
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val url = URL(
                "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=$lang&q=$q&total=1&idx=0&textlen=${text.length}"
            )
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            c.setRequestProperty("Referer", "https://translate.google.com/")
            if (c.responseCode != 200) return null
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size < 200) null else bytes
        } catch (e: Exception) {
            null
        }
    }

    internal fun split(text: String, max: Int): List<String> {
        val clean = text.replace("\\s+".toRegex(), " ").trim()
        val pieces = mutableListOf<String>()
        for (p in clean.split(Regex("(?<=[।.!?])\\s*"))) {
            var s = p.trim()
            while (s.length > max) {
                var cut = s.lastIndexOf(' ', max)
                if (cut < 20) cut = max
                pieces.add(s.substring(0, cut).trim())
                s = s.substring(cut).trim()
            }
            if (s.isNotEmpty()) pieces.add(s)
        }
        val out = mutableListOf<String>()
        var cur = ""
        for (p in pieces) {
            if (cur.isEmpty()) cur = p
            else if (cur.length + 1 + p.length <= max) cur = "$cur $p"
            else { out.add(cur); cur = p }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}
