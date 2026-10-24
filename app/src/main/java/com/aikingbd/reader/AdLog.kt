package com.aikingbd.reader

import android.os.Handler
import android.os.Looper

object AdLog {
    @Volatile var banner = "waiting"
    @Volatile var appOpen = "waiting"
    @Volatile var onChange: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun set(isBanner: Boolean, text: String) {
        val t = text.take(70)
        if (isBanner) banner = t else appOpen = t
        main.post { onChange?.invoke() }
    }

    fun line(): String = "Banner: " + banner + " | Open: " + appOpen
}
