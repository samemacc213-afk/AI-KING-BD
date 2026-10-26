package com.aikingbd.reader

import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class PinLock(private val act: AppCompatActivity, private val prefs: Prefs) {

    private var taps = 0
    private var lastTap = 0L
    private var lockDialog: AlertDialog? = null

    fun attachTitleTap(title: TextView) {
        title.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap > 1500) taps = 0
            lastTap = now
            taps++
            if (taps >= 20) {
                taps = 0
                onSecretOpened()
            }
        }
    }

    fun showIfLocked() {
        if (!prefs.pinLocked || prefs.pinHash.isBlank()) return
        if (act.isFinishing || act.isDestroyed) return
        if (lockDialog?.isShowing == true) return
        val f = pinField("পিন")
        val d = AlertDialog.Builder(act)
            .setTitle("অ্যাপ লক করা আছে")
            .setMessage("চালু করতে পিন দিন")
            .setView(box(f))
            .setCancelable(false)
            .setPositiveButton("আনলক", null)
            .create()
        d.setCanceledOnTouchOutside(false)
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (sha(f.text.toString()) == prefs.pinHash) {
                prefs.pinLocked = false
                d.dismiss()
            } else {
                f.setText("")
                toast("ভুল পিন")
            }
        }
        lockDialog = d
    }

    private fun onSecretOpened() {
        if (prefs.pinHash.isBlank()) showSetPin() else askCurrent { showMenu() }
    }

    private fun askCurrent(onOk: () -> Unit) {
        val f = pinField("বর্তমান পিন")
        AlertDialog.Builder(act)
            .setTitle("পিন দিন")
            .setView(box(f))
            .setPositiveButton("OK") { _, _ ->
                if (sha(f.text.toString()) == prefs.pinHash) onOk() else toast("ভুল পিন")
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showMenu() {
        AlertDialog.Builder(act)
            .setTitle("AI KING BD")
            .setItems(arrayOf("অ্যাপ লক করুন", "পিন পরিবর্তন করুন")) { _, which ->
                if (which == 0) {
                    prefs.pinLocked = true
                    showIfLocked()
                } else {
                    showSetPin()
                }
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun showSetPin() {
        val a = pinField("নতুন পিন (৪-৮ সংখ্যা)")
        val b = pinField("আবার লিখুন")
        val d = AlertDialog.Builder(act)
            .setTitle("নতুন পিন সেট করুন")
            .setView(box(a, b))
            .setPositiveButton("সেভ", null)
            .setNegativeButton("বাতিল", null)
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val p = a.text.toString()
            if (p.length < 4) {
                toast("কমপক্ষে ৪ সংখ্যা দিন")
            } else if (p != b.text.toString()) {
                toast("দুইটা পিন মেলেনি")
            } else {
                prefs.pinHash = sha(p)
                toast("পিন সেভ হয়েছে")
                d.dismiss()
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(act, m, Toast.LENGTH_SHORT).show()

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun pinField(h: String) = EditText(act).apply {
        hint = h
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(8))
    }

    private fun box(vararg v: View) = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        val p = (20 * act.resources.displayMetrics.density).toInt()
        setPadding(p, p / 2, p, 0)
        v.forEach { addView(it) }
    }
}
