package com.aikingbd.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors

// Voice typing from the floating buttons:
//   mic button  -> starts recording
//   text button -> stops, turns the voice into Bengali text (Sarvam) and copies it
object VoiceCapture {
    private const val RATE = 16000
    private const val CHUNK_BYTES = RATE * 2 * 28
    private const val MAX_BYTES = RATE * 2 * 300
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    var recording = false
    var onState: ((Boolean) -> Unit)? = null
    private var host: Activity? = null
    private var rec: AudioRecord? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()
    private var lastError = ""

    private fun toast(ctx: Context, msg: String, long: Boolean = false) {
        main.post {
            Toast.makeText(ctx, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyState(on: Boolean) {
        main.post { onState?.invoke(on) }
    }

    // Called by the mic button
    fun begin(ctx: Context) {
        val app = ctx.applicationContext
        if (recording) {
            toast(app, "Already recording. Tap the text button when you finish")
            return
        }
        if (Prefs(app).sarvamKey.trim().isEmpty()) {
            toast(app, "Paste your Sarvam key in the AI KING BD app first", true)
            return
        }
        val i = Intent(app, VoiceCaptureActivity::class.java)
        i.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )
        try {
            app.startActivity(i)
        } catch (e: Exception) {
            toast(app, "Could not start recording: " + e.javaClass.simpleName, true)
        }
    }

    // Called by VoiceCaptureActivity once it has the microphone permission
    fun startRecording(activity: Activity): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            toast(activity, "Microphone is not available", true)
            return false
        }
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192) * 2
            )
        } catch (e: Exception) {
            null
        }
        if (r == null || r.state != AudioRecord.STATE_INITIALIZED) {
            r?.release()
            toast(activity, "Microphone is busy. Try again", true)
            return false
        }
        try {
            r.startRecording()
        } catch (e: Exception) {
            r.release()
            toast(activity, "Could not start the microphone", true)
            return false
        }
        synchronized(pcm) { pcm.reset() }
        rec = r
        host = activity
        recording = true
        notifyState(true)
        val appCtx = activity.applicationContext
        val t = Thread {
            val buf = ByteArray(4096)
            var total = 0
            while (recording && total < MAX_BYTES) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    synchronized(pcm) { pcm.write(buf, 0, n) }
                    total += n
                } else if (n < 0) {
                    break
                }
            }
            if (recording && total >= MAX_BYTES) {
                main.post { stopAndConvert(appCtx) }
            }
        }
        worker = t
        t.start()
        return true
    }

    // Called by the text button
    fun stopAndConvert(ctx: Context) {
        val app = ctx.applicationContext
        if (!recording) {
            toast(app, "Tap the mic button first and speak")
            return
        }
        recording = false
        try { worker?.join(1500) } catch (e: Exception) { }
        try { rec?.stop() } catch (e: Exception) { }
        try { rec?.release() } catch (e: Exception) { }
        rec = null
        notifyState(false)
        val a = host
        host = null
        a?.finish()
        val data = synchronized(pcm) { pcm.toByteArray() }
        if (data.size < RATE * 2 / 3) {
            toast(app, "Too short. Tap the mic button and speak again")
            return
        }
        toast(app, "Converting your voice to text...")
        val key = Prefs(app).sarvamKey.trim()
        executor.execute {
            val text = transcribe(data, key)
            main.post {
                if (text.isBlank()) {
                    val why = if (lastError.isEmpty()) "" else ": " + lastError
                    toast(app, "Could not turn the voice into text" + why, true)
                } else {
                    val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("AI KING BD", text))
                    toast(app, "Copied: " + text.take(60), true)
                }
            }
        }
    }

    // The recording screen was closed by the system or the user: drop the recording
    fun hostGone(a: Activity) {
        if (host !== a) return
        host = null
        if (recording) {
            recording = false
            try { rec?.stop() } catch (e: Exception) { }
            try { rec?.release() } catch (e: Exception) { }
            rec = null
            notifyState(false)
        }
    }

    // Sarvam accepts up to 30 seconds per request, so long speech is cut in 28 second pieces
    private fun transcribe(data: ByteArray, key: String): String {
        lastError = ""
        val sb = StringBuilder()
        var off = 0
        while (off < data.size) {
            val len = minOf(CHUNK_BYTES, data.size - off)
            if (len >= RATE / 2) {
                val part = postChunk(wav(data, off, len), key)
                if (part == null) {
                    if (sb.isEmpty()) return ""
                    break
                }
                if (part.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(part.trim())
                }
            }
            off += len
        }
        return sb.toString()
    }

    private fun postChunk(wav: ByteArray, key: String): String? {
        return try {
            val boundary = "----aikb" + UUID.randomUUID().toString().replace("-", "")
            val c = URL("https://api.sarvam.ai/speech-to-text").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("api-subscription-key", key)
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            c.outputStream.use { out ->
                val head = "--" + boundary + "\r\n"
                out.write((head + "Content-Disposition: form-data; name=\"model\"\r\n\r\nsaarika:v2.5\r\n").toByteArray())
                out.write((head + "Content-Disposition: form-data; name=\"language_code\"\r\n\r\nbn-IN\r\n").toByteArray())
                out.write(
                    (head + "Content-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\n" +
                        "Content-Type: audio/wav\r\n\r\n").toByteArray()
                )
                out.write(wav)
                out.write(("\r\n--" + boundary + "--\r\n").toByteArray())
            }
            val code = c.responseCode
            if (code == 200) {
                val body = c.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("transcript", "")
            } else {
                val err = try {
                    c.errorStream?.bufferedReader()?.use { it.readText() }?.take(160) ?: ""
                } catch (e: Exception) {
                    ""
                }
                lastError = "Sarvam (" + code + ") " + err
                null
            }
        } catch (e: Exception) {
            lastError = "Sarvam " + e.javaClass.simpleName
            null
        }
    }

    private fun wav(pcm: ByteArray, off: Int, len: Int): ByteArray {
        val out = ByteArray(44 + len)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + len)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(RATE)
        bb.putInt(RATE * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(len)
        System.arraycopy(pcm, off, out, 44, len)
        return out
    }
}
