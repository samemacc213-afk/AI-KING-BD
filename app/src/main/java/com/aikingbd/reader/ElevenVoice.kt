package com.aikingbd.reader

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ElVoice(val id: String, val name: String, val female: Boolean)

object ElevenVoice {
    @Volatile var lastError: String = ""

    private fun err(c: HttpURLConnection, code: Int): String {
        val body = try {
            (c.errorStream?.bufferedReader()?.use { it.readText() } ?: "").take(160)
        } catch (e: Exception) {
            ""
        }
        return "ElevenLabs ($code) $body"
    }

    fun listVoices(key: String): List<ElVoice>? {
        lastError = ""
        for (path in listOf(
            "https://api.elevenlabs.io/v1/voices",
            "https://api.elevenlabs.io/v2/voices?page_size=100"
        )) {
            try {
                val c = URL(path).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 20000
                c.setRequestProperty("xi-api-key", key)
                val code = c.responseCode
                if (code != 200) {
                    lastError = err(c, code)
                    if (code == 404) continue
                    return null
                }
                val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("voices")
                val out = mutableListOf<ElVoice>()
                for (i in 0 until arr.length()) {
                    val v = arr.getJSONObject(i)
                    val gender = v.optJSONObject("labels")?.optString("gender", "") ?: ""
                    out.add(ElVoice(v.getString("voice_id"), v.optString("name", "Voice"), gender.equals("female", true)))
                }
                return out
            } catch (e: Exception) {
                lastError = "ElevenLabs " + e.javaClass.simpleName
                return null
            }
        }
        return null
    }

    fun synth(text: String, voiceId: String, key: String): ByteArray? {
        lastError = ""
        for (withLang in listOf(true, false)) {
            try {
                val body = JSONObject().put("text", text).put("model_id", "eleven_v3")
                if (withLang) body.put("language_code", "bn")
                val c = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
                    .openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 60000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Accept", "audio/mpeg")
                c.setRequestProperty("xi-api-key", key)
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                if (code == 200) return c.inputStream.use { it.readBytes() }
                lastError = err(c, code)
                if (code == 400 || code == 422) continue
                return null
            } catch (e: Exception) {
                lastError = "ElevenLabs " + e.javaClass.simpleName
                return null
            }
        }
        return null
    }
}
