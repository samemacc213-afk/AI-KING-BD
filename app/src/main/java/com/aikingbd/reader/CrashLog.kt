package com.aikingbd.reader

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.PrintWriter
import java.io.StringWriter

// Remembers why the app closed, so the reason can be shown (and copied) on the next start.
object CrashLog {
    private const val FILE = "crash"
    private const val KEY = "last"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                save(app, e)
            } catch (x: Throwable) {
            }
            if (previous != null) previous.uncaughtException(t, e)
        }
    }

    private fun save(ctx: Context, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, sw.toString().take(3500)).commit()
    }

    private fun take(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val t = sp.getString(KEY, null)
        if (t != null) sp.edit().remove(KEY).apply()
        return t
    }

    fun showIfAny(activity: Activity) {
        val text = take(activity) ?: return
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            try {
                AlertDialog.Builder(activity)
                    .setTitle("AI KING BD closed last time")
                    .setMessage(text.take(1400))
                    .setPositiveButton("Copy") { _, _ ->
                        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AI KING BD crash", text))
                        Toast.makeText(activity, "Copied. Send it to get a fix.", Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            } catch (e: Exception) {
            }
        }, 1200)
    }
}
