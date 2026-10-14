package com.aikingbd.reader

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object OnlineVoice {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    @Volatile private var player: MediaPlayer? = null

    fun speak(ctx: Context, text: String, onFail: () -> Unit) {
        val gen = ++generation
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
        val lang = if (text.any { it in '\u0980'..'\u09FF' }) "bn" else "en"
        val chunks = split(text)
        if (chunks.isEmpty()) return

        var current: File? = download(ctx, chunks[0], lang)
        if (current == null) {
            if (gen == generation) onFail()
            return
        }
        val prefs = Prefs(ctx)

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

            current = if (i + 1 < chunks.size) download(ctx, chunks[i + 1], lang) else null

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

    private fun download(ctx: Context, text: String, lang: String): File? {
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
            val f = File(ctx.cacheDir, "tts_${System.nanoTime()}.mp3")
            c.inputStream.use { input -> f.outputStream().use { out -> input.copyTo(out) } }
            if (f.length() < 200) { f.delete(); null } else f
        } catch (e: Exception) {
            null
        }
    }

    private fun split(text: String): List<String> {
        val clean = text.replace("\\s+".toRegex(), " ").trim()
        val pieces = mutableListOf<String>()
        for (p in clean.split(Regex("(?<=[।.!?])\\s*"))) {
            var s = p.trim()
            while (s.length > 180) {
                var cut = s.lastIndexOf(' ', 180)
                if (cut < 20) cut = 180
                pieces.add(s.substring(0, cut).trim())
                s = s.substring(cut).trim()
            }
            if (s.isNotEmpty()) pieces.add(s)
        }
        val out = mutableListOf<String>()
        var cur = ""
        for (p in pieces) {
            if (cur.isEmpty()) cur = p
            else if (cur.length + 1 + p.length <= 180) cur = "$cur $p"
            else { out.add(cur); cur = p }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}
