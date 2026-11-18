package com.aikingbd.reader

import android.os.Handler
import android.os.Looper

object AdLog {
    @Volatile var banner = "waiting"
    @Volatile var appOpen = "waiting"
    @Volatile var inter = "waiting"
    @Volatile var rewarded = "waiting"
    @Volatile var rewardedInt = "waiting"
    @Volatile var nativeAd = "waiting"
    @Volatile var onChange: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun set(isBanner: Boolean, text: String) {
        val t = text.take(70)
        if (isBanner) banner = t else appOpen = t
        main.post { onChange?.invoke() }
    }

    fun other(name: String, text: String) {
        val t = text.take(24)
        when (name) {
            "Inter" -> inter = t
            "Rew" -> rewarded = t
            "RewInt" -> rewardedInt = t
            "Native" -> nativeAd = t
        }
        main.post { onChange?.invoke() }
    }

    fun line(): String =
        "Banner: " + banner + " | Open: " + appOpen + " | Inter: " + inter +
            " | Rew: " + rewarded + " | RewInt: " + rewardedInt + " | Native: " + nativeAd
}
