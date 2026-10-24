package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

// When the user enters the app: show the saved ad at once if there is one,
// otherwise ask for one and show it as soon as it arrives (up to 30 seconds).
// One ad is kept ready for the next entry (a single request, never repeated in a loop).
object AppOpenManager {
    private const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    private const val PATIENCE_MS = 30_000L
    private const val MIN_GAP_MS = 20_000L

    private var cached: AppOpenAd? = null
    private var cachedAt = 0L
    private var loading = false
    private var showing = false
    private var lastClosedAt = 0L
    private var lastRequestAt = 0L
    private var askedAt = 0L
    private var pending: Activity? = null

    private fun fresh(): Boolean =
        cached != null && System.currentTimeMillis() - cachedAt < MAX_AGE_MS

    private fun usable(a: Activity): Boolean {
        if (a.isFinishing || a.isDestroyed) return false
        val owner = a as? LifecycleOwner ?: return true
        return owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    fun showOnLaunch(activity: Activity) {
        val now = System.currentTimeMillis()
        if (showing || now - lastClosedAt < 5000L) return
        if (fresh()) {
            display(activity)
            return
        }
        pending = activity
        askedAt = now
        request(activity.applicationContext)
    }

    // called when the user leaves the app: keep one ad ready for the next entry
    fun keepForNext(ctx: Context) {
        if (!fresh()) request(ctx.applicationContext)
    }

    private fun request(app: Context) {
        val now = System.currentTimeMillis()
        if (loading || now - lastRequestAt < MIN_GAP_MS) return
        loading = true
        lastRequestAt = now
        AppOpenAd.load(
            app,
            AdIds.appOpen(),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading = false
                    AdLog.set(false, "loaded")
                    cached = ad
                    cachedAt = System.currentTimeMillis()
                    val w = pending
                    pending = null
                    if (w != null && System.currentTimeMillis() - askedAt <= PATIENCE_MS &&
                        usable(w) && !showing
                    ) {
                        display(w)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    pending = null
                    AdLog.set(false, "failed " + error.code + ": " + error.message)
                }
            }
        )
    }

    private fun display(activity: Activity) {
        val ad = cached ?: return
        cached = null
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                lastClosedAt = System.currentTimeMillis()
                request(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                lastClosedAt = System.currentTimeMillis()
            }
        }
        ad.show(activity)
    }
}
