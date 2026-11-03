package com.aikingbd.reader

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SarvamVoice {
    @Volatile var lastError: String = ""

    private val VALID_V3 = setOf("shubh","aditya","rahul","rohan","amit","dev","ratan","varun","manan","sumit","kabir","aayan","ashutosh","advait","anand","tarun","sunny","mani","gokul","vijay","mohit","rehan","soham","ritu","priya","neha","pooja","simran","kavya","ishita","shreya","roopa","tanya","shruti","suhani","kavitha","rupali")

    fun synth(text: String, speaker: String, key: String): ByteArray? {
        lastError = ""
        val safeSpeaker = speaker.trim().lowercase().let { if (it in VALID_V3) it else "shubh" }
        for (langField in listOf("language_code", "target_language_code")) {
            try {
                val body = JSONObject()
                    .put("text", text)
                    .put(langField, "bn-IN")
                    .put("speaker", safeSpeaker)
                    .put("model", "bulbul:v3")

                val c = URL("https://api.sarvam.ai/text-to-speech").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 60000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("api-subscription-key", key)
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = c.responseCode
                if (code == 200) {
                    val resp = c.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONObject(resp).getJSONArray("audios")
                    return Base64.decode(arr.getString(0), Base64.DEFAULT)
                }
                val err = try {
                    (c.errorStream?.bufferedReader()?.use { it.readText() } ?: "").take(160)
                } catch (e: Exception) {
                    ""
                }
                lastError = "Sarvam ($code) $err"
                if (code == 422 || code == 400) continue
                return null
            } catch (e: Exception) {
                lastError = "Sarvam " + e.javaClass.simpleName
                return null
            }
        }
        return null
    }
}
