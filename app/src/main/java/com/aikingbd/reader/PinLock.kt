package com.aikingbd.reader

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class PinLock(private val act: AppCompatActivity, private val prefs: Prefs) {

    private sealed class Fetch {
        object Fail : Fetch()
        class Ok(val pw: String) : Fetch()
    }

    private val urlPw =
        "https://aikingbd-default-rtdb.europe-west1.firebasedatabase.app/ctl_5f7dfe7785193a2608f6/password.json"
    private val recheckMs = 15_000L
    private val graceMs = 48L * 60 * 60 * 1000

    private var dialog: AlertDialog? = null
    private var checking = false
    private var lastGood = 0L

    // এখন আর ২০ বার ট্যাপ লাগে না
    @Suppress("UNUSED_PARAMETER")
    fun attachTitleTap(title: TextView) {
    }

    fun showIfLocked() {
        if (act.isFinishing || act.isDestroyed) return
        if (dialog?.isShowing == true || checking) return
        val okHash = gate().getString("ok_hash", "") ?: ""
        if (okHash.isEmpty()) {
            lockdown()
            showDialog("অ্যাপ খুলতে পাসওয়ার্ড দিন")
            return
        }
        val now = System.currentTimeMillis()
        if (lastGood != 0L && now - lastGood < recheckMs) return
        checking = true
        Thread {
            val r = fetchPassword()
            act.runOnUiThread {
                checking = false
                if (!act.isFinishing && !act.isDestroyed) handleSilent(r)
            }
        }.start()
    }

    private fun handleSilent(r: Fetch) {
        val sp = gate()
        val okHash = sp.getString("ok_hash", "") ?: ""
        val okTime = sp.getLong("ok_time", 0L)
        val now = System.currentTimeMillis()
        when (r) {
            is Fetch.Fail -> {
                if (now - okTime > graceMs) showDialog("ইন্টারনেট চালু করে পাসওয়ার্ড দিন")
            }
            is Fetch.Ok -> {
                if (r.pw.isEmpty()) {
                    lockdown()
                    showDialog("অ্যাপ এখন লক করা আছে। নতুন পাসওয়ার্ডের জন্য অপেক্ষা করুন")
                } else if (sha(r.pw) == okHash) {
                    sp.edit().putLong("ok_time", now).apply()
                    lastGood = now
                } else {
                    lockdown()
                    showDialog("পাসওয়ার্ড বদলানো হয়েছে। নতুন পাসওয়ার্ড দিন")
                }
            }
        }
    }

    private fun showDialog(msg: String) {
        if (dialog?.isShowing == true) return
        val f = EditText(act).apply {
            hint = "পাসওয়ার্ড"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val d = AlertDialog.Builder(act)
            .setTitle("AI KING BD")
            .setMessage(msg)
            .setView(box(f))
            .setCancelable(false)
            .setPositiveButton("ঢুকুন", null)
            .setNegativeButton("বন্ধ করুন", null)
            .create()
        d.setCanceledOnTouchOutside(false)
        d.show()
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { act.finishAffinity() }
        val ok = d.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.setOnClickListener {
            val typed = f.text.toString().trim()
            if (typed.isEmpty()) {
                toast("পাসওয়ার্ড লিখুন")
                return@setOnClickListener
            }
            ok.isEnabled = false
            Thread {
                val r = fetchPassword()
                act.runOnUiThread {
                    if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                    ok.isEnabled = true
                    if (r is Fetch.Ok) {
                        if (r.pw.isEmpty()) {
                            toast("এখন অ্যাপ লক করা আছে। নতুন পাসওয়ার্ডের জন্য অপেক্ষা করুন")
                        } else if (typed == r.pw) {
                            val now = System.currentTimeMillis()
                            gate().edit().putString("ok_hash", sha(r.pw)).putLong("ok_time", now).apply()
                            lastGood = now
                            d.dismiss()
                            act.recreate()
                        } else {
                            f.setText("")
                            toast("ভুল পাসওয়ার্ড")
                        }
                    } else {
                        toast("ইন্টারনেট চালু করে আবার চেষ্টা করুন")
                    }
                }
            }.start()
        }
        dialog = d
    }

    private fun lockdown() {
        gate().edit().remove("ok_hash").apply()
        lastGood = 0L
        try {
            act.stopService(Intent(act, FloatingService::class.java))
        } catch (e: Exception) {
        }
        prefs.enabled = false
    }

    private fun fetchPassword(): Fetch {
        return try {
            val c = URL(urlPw).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.requestMethod = "GET"
            if (c.responseCode != 200) {
                Fetch.Fail
            } else {
                val body = c.inputStream.bufferedReader().use { it.readText() }.trim()
                val v = JSONTokener(body).nextValue()
                val pw = when (v) {
                    is String -> v.trim()
                    is Number -> v.toString()
                    else -> ""
                }
                Fetch.Ok(pw)
            }
        } catch (e: Exception) {
            Fetch.Fail
        }
    }

    private fun gate() = act.getSharedPreferences("gate", Context.MODE_PRIVATE)

    private fun toast(m: String) = Toast.makeText(act, m, Toast.LENGTH_SHORT).show()

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun box(vararg v: View) = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        val p = (20 * act.resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, 0)
        v.forEach { addView(it) }
    }
}
