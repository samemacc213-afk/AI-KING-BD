package com.aikingbd.reader

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import java.util.ArrayDeque

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
    const val MAX_RETRIES = 3

    // wait before the next try after a failed ad request: 10s, 30s, then 60s
    fun delayFor(attempt: Int): Long = when (attempt) {
        0 -> 10_000L
        1 -> 30_000L
        else -> 60_000L
    }
}

// at most 6 ad requests per minute in total, so Google is never flooded
object AdGate {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun allow(): Boolean {
        val now = System.currentTimeMillis()
        while (times.isNotEmpty() && now - times.peekFirst() > 60_000L) {
            times.pollFirst()
        }
        if (times.size >= 6) return false
        times.addLast(now)
        return true
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
                    AppOpenManager.preload(app)
                }
            }
        }
    }

    // only the app-open ad is requested when the app starts or comes back
    fun start(ctx: Context) {
        if (AdState.showing) return
        if (!initDone) {
            pending = true
            return
        }
        AppOpenManager.preload(ctx.applicationContext)
    }
}
