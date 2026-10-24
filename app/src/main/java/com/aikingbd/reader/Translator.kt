package com.aikingbd.reader

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Translator {
    @Volatile var lastError: String = ""

    fun toBangla(text: String): String? {
        lastError = ""
        val chunks = chunk(text, 1200)
        if (chunks.isEmpty()) return null
        val out = StringBuilder()
        for (c in chunks) {
            val r = google(c) ?: myMemory(c)
            if (r == null) return null
            if (out.isNotEmpty()) out.append("\n")
            out.append(r)
        }
        return out.toString()
    }

    private fun chunk(text: String, max: Int): List<String> {
        val res = mutableListOf<String>()
        var cur = StringBuilder()
        for (line in text.replace("\r", "").split("\n")) {
            var l = line.trim()
            while (l.length > max) {
                var cut = l.lastIndexOf(' ', max)
                if (cut < 50) cut = max
                if (cur.isNotEmpty()) {
                    res.add(cur.toString())
                    cur = StringBuilder()
                }
                res.add(l.substring(0, cut))
                l = l.substring(cut).trim()
            }
            if (cur.isNotEmpty() && cur.length + l.length + 1 > max) {
                res.add(cur.toString())
                cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append("\n")
            cur.append(l)
        }
        if (cur.isNotEmpty()) res.add(cur.toString())
        return res.filter { it.isNotBlank() }
    }

    private fun google(text: String): String? {
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val c = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=bn&dt=t&q=$q")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            if (c.responseCode != 200) {
                lastError = "Google translate (" + c.responseCode + ")"
                return null
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val parts = JSONArray(body).getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONArray(i) ?: continue
                sb.append(p.optString(0, ""))
            }
            val r = sb.toString().trim()
            if (r.isEmpty()) null else r
        } catch (e: Exception) {
            lastError = "Google translate " + e.javaClass.simpleName
            null
        }
    }

    private fun myMemory(text: String): String? {
        if (text.length > 480) return null
        return try {
            val q = URLEncoder.encode(text, "UTF-8")
            val c = URL("https://api.mymemory.translated.net/get?q=$q&langpair=en%7Cbn")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            if (c.responseCode != 200) {
                lastError = lastError + " | backup (" + c.responseCode + ")"
                return null
            }
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val r = j.getJSONObject("responseData").optString("translatedText", "").trim()
            if (r.isEmpty()) null else r
        } catch (e: Exception) {
            lastError = lastError + " | backup " + e.javaClass.simpleName
            null
        }
    }
}
