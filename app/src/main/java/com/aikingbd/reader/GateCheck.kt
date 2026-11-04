package com.aikingbd.reader

import android.content.Context
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GateCheck {
    private const val URL_PW =
        "https://aikingbd-default-rtdb.europe-west1.firebasedatabase.app/ctl_5f7dfe7785193a2608f6/password.json"
    private const val GRACE_MS = 48L * 60 * 60 * 1000

    // true = this phone may keep running, false = locked
    fun verify(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences("gate", Context.MODE_PRIVATE)
        val okHash = sp.getString("ok_hash", "") ?: ""
        if (okHash.isEmpty()) return false
        val now = System.currentTimeMillis()
        val pw = fetch()
        if (pw == null) {
            return now - sp.getLong("ok_time", 0L) <= GRACE_MS
        }
        if (pw.isNotEmpty() && sha(pw) == okHash) {
            sp.edit().putLong("ok_time", now).apply()
            return true
        }
        sp.edit().remove("ok_hash").apply()
        return false
    }

    // null = could not reach the server, empty = no password set
    private fun fetch(): String? {
        return try {
            val c = URL(URL_PW).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.requestMethod = "GET"
            if (c.responseCode != 200) {
                null
            } else {
                val body = c.inputStream.bufferedReader().use { it.readText() }.trim()
                val v = JSONTokener(body).nextValue()
                when (v) {
                    is String -> v.trim()
                    is Number -> v.toString()
                    else -> ""
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
