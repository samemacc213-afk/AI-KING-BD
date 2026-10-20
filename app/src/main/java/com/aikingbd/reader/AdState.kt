package com.aikingbd.reader

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds

object AdState {
    @Volatile var showing = false
    @Volatile var lastClosedAt = 0L
    private var visibleCount = 0

    val foreground: Boolean get() = visibleCount > 0

    fun started() {
        visibleCount++
    }

    fun stopped() {
        visibleCount = (visibleCount - 1).coerceAtLeast(0)
    }

    fun closed() {
        showing = false
        lastClosedAt = System.currentTimeMillis()
    }
}

object AdRetry {
    // wait before the next try after a failed ad request: 1s, 2s, 4s, 8s, 15s, then 30s
    fun delayFor(attempt: Int): Long = when (attempt) {
        0 -> 1000L
        1 -> 2000L
        2 -> 4000L
        3 -> 8000L
        4 -> 15000L
        else -> 30000L
    }
}

object AdKick {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var initDone = false
    @Volatile private var pending = false

    fun init(app: Application) {
        MobileAds.initialize(app) {
            initDone = true
            main.post {
                if (pending) {
                    pending = false
                    loadAll(app)
                }
            }
        }
    }

    fun start(ctx: Context) {
        if (!initDone) {
            pending = true
            return
        }
        loadAll(ctx.applicationContext)
    }

    private fun loadAll(ctx: Context) {
        AppOpenManager.preload(ctx)
        InterstitialManager.preload(ctx)
        RewardedManager.preload(ctx)
        RewardedInterstitialManager.preload(ctx)
    }
}
