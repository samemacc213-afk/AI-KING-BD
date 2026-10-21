package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenManager {
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    private const val LATE_WINDOW_MS = 20_000L
    private val RETRY = longArrayOf(3_000L, 15_000L, 60_000L)

    private val handler = Handler(Looper.getMainLooper())
    private var ad: AppOpenAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var attempt = 0
    private var waiting: Activity? = null
    private var waitUntil = 0L

    // set by the splash screen when it already handled the app-open ad
    @Volatile var skipNext = false

    private fun fresh(): Boolean =
        ad != null && System.currentTimeMillis() - loadedAt < MAX_AGE_MS

    fun isReady(): Boolean = fresh()

    private fun keyboardOpen(a: Activity): Boolean =
        ViewCompat.getRootWindowInsets(a.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun preload(ctx: Context) {
        if (loading || fresh()) return
        if (!AdGate.allow()) return
        loading = true
        val app = ctx.applicationContext
        AppOpenAd.load(
            app,
            AdIds.appOpen(),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    loading = false
                    attempt = 0
                    ad = loaded
                    loadedAt = System.currentTimeMillis()
                    val w = waiting
                    if (w != null && System.currentTimeMillis() <= waitUntil &&
                        !w.isFinishing && !w.isDestroyed && !keyboardOpen(w)
                    ) {
                        waiting = null
                        showIfReady(w)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    attempt++
                    if (attempt <= RETRY.size) {
                        handler.postDelayed({
                            if (AdState.foreground) preload(app)
                        }, RETRY[attempt - 1])
                    }
                }
            }
        )
    }

    fun showIfReady(activity: Activity, onDone: (() -> Unit)? = null, lateOk: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (AdState.showing || now - AdState.lastClosedAt < 3000) {
            onDone?.invoke()
            return false
        }
        val a = ad
        if (a == null || !fresh()) {
            preload(activity)
            if (lateOk) {
                waiting = activity
                waitUntil = now + LATE_WINDOW_MS
            }
            onDone?.invoke()
            return false
        }
        ad = null
        AdState.showing = true
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdState.closed()
                onDone?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdState.closed()
                onDone?.invoke()
            }
        }
        a.show(activity)
        return true
    }
}
