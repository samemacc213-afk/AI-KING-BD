package com.aikingbd.reader

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ElVoice(val id: String, val name: String, val female: Boolean)
class SharedVoice(val voiceId: String, val name: String, val ownerId: String, val description: String)

object ElevenVoice {
    @Volatile var lastError: String = ""

    private fun errBody(c: HttpURLConnection): String {
        return try {
            (c.errorStream?.bufferedReader()?.use { it.readText() } ?: "").take(400)
        } catch (e: Exception) {
            ""
        }
    }

    // Checks the key directly against ElevenLabs and returns the exact result for display.
    fun testKey(key: String): Pair<Boolean, String> {
        return try {
            val c = URL("https://api.elevenlabs.io/v1/user").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("xi-api-key", key)
            val code = c.responseCode
            if (code == 200) {
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val j = JSONObject(body)
                val tier = j.optJSONObject("subscription")?.optString("tier", "") ?: ""
                Pair(true, "The key works.\nPlan: " + tier.ifEmpty { "unknown" })
            } else {
                Pair(false, "HTTP " + code + "\n" + errBody(c))
            }
        } catch (e: Exception) {
            Pair(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    fun listVoices(key: String): List<ElVoice>? {
        return try {
            val path = "https://api.elevenlabs.io/v1/voices"
            val c = URL(path).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("xi-api-key", key)
            val code = c.responseCode
            if (code != 200) {
                lastError = "ElevenLabs (" + code + ") " + errBody(c)
                return null
            }
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("voices")
            val out = mutableListOf<ElVoice>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                val gender = v.optJSONObject("labels")?.optString("gender", "") ?: ""
                out.add(ElVoice(v.getString("voice_id"), v.optString("name", "Voice"), gender.equals("female", true)))
            }
            out
        } catch (e: Exception) {
            lastError = "ElevenLabs " + e.javaClass.simpleName
            null
        }
    }

    // Searches ElevenLabs' public Voice Library for Bengali female voices.
    fun searchLibrary(key: String): List<SharedVoice>? {
        return try {
            val c = URL("https://api.elevenlabs.io/v1/shared-voices?page_size=40&gender=female&language=bn")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("xi-api-key", key)
            val code = c.responseCode
            if (code != 200) {
                lastError = "Voice library (" + code + ") " + errBody(c)
                return null
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).optJSONArray("voices") ?: JSONArray()
            val out = mutableListOf<SharedVoice>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                out.add(
                    SharedVoice(
                        v.optString("voice_id"),
                        v.optString("name", "Voice"),
                        v.optString("public_owner_id", ""),
                        v.optString("description", "")
                    )
                )
            }
            out
        } catch (e: Exception) {
            lastError = "Voice library " + e.javaClass.simpleName
            null
        }
    }

    // Adds a Voice Library voice into the user's own account.
    fun addVoice(key: String, ownerId: String, voiceId: String, name: String): Boolean {
        return try {
            val body = JSONObject().put("new_name", name)
            val c = URL("https://api.elevenlabs.io/v1/voices/add/$ownerId/$voiceId")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("xi-api-key", key)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code in 200..299) {
                true
            } else {
                lastError = "Add voice (" + code + ") " + errBody(c)
                false
            }
        } catch (e: Exception) {
            lastError = "Add voice " + e.javaClass.simpleName
            false
        }
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
                lastError = "ElevenLabs (" + code + ") " + errBody(c)
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
