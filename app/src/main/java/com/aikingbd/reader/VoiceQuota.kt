package com.aikingbd.reader

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoiceQuota {
    const val FREE_PER_DAY = 3
    const val BONUS = 3

    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun roll(p: Prefs) {
        val t = today()
        if (p.voiceDay != t) {
            p.voiceDay = t
            p.voiceUsed = 0
            p.voiceBonus = 0
        }
    }

    fun left(ctx: Context): Int {
        val p = Prefs(ctx)
        roll(p)
        return (FREE_PER_DAY + p.voiceBonus - p.voiceUsed).coerceAtLeast(0)
    }

    fun use(ctx: Context) {
        val p = Prefs(ctx)
        roll(p)
        p.voiceUsed = p.voiceUsed + 1
    }

    fun addBonus(ctx: Context) {
        val p = Prefs(ctx)
        roll(p)
        p.voiceBonus = p.voiceBonus + BONUS
    }
}
