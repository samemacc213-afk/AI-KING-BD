package com.aikingbd.reader

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("aikb", Context.MODE_PRIVATE)

    var pitch: Float
        get() = sp.getFloat("pitch", 1.0f)
        set(v) = sp.edit().putFloat("pitch", v).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1.0f)
        set(v) = sp.edit().putFloat("speed", v).apply()

    var btnX: Int
        get() = sp.getInt("btnX", 0)
        set(v) = sp.edit().putInt("btnX", v).apply()

    var btnY: Int
        get() = sp.getInt("btnY", 120)
        set(v) = sp.edit().putInt("btnY", v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var hasPhoto: Boolean
        get() = sp.getBoolean("hasPhoto", false)
        set(v) = sp.edit().putBoolean("hasPhoto", v).apply()

    var voiceId: String
        get() = sp.getString("voiceId", "") ?: ""
        set(v) = sp.edit().putString("voiceId", v).apply()

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v).apply()
}
