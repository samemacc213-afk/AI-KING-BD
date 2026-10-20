package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialManager {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: InterstitialAd? = null
    private var loading = false
    private var attempt = 0

    fun preload(ctx: Context) {
        if (loading || ad != null) return
        loading = true
        val app = ctx.applicationContext
        InterstitialAd.load(
            app,
            AdIds.interstitial(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    attempt = 0
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    attempt++
                    handler.postDelayed({
                        if (AdState.foreground) preload(app)
                    }, AdRetry.delayFor(attempt - 1))
                }
            }
        )
    }

    private fun display(activity: Activity, onDone: () -> Unit) {
        val a = ad
        if (a == null) {
            onDone()
            return
        }
        ad = null
        AdState.showing = true
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdState.closed()
                preload(activity)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdState.closed()
                preload(activity)
                onDone()
            }
        }
        a.show(activity)
    }

    // Shows the ad now if ready. If not ready, waits up to waitMs for it to arrive.
    fun showNow(activity: Activity, waitMs: Long = 0L, onDone: () -> Unit): Boolean {
        if (ad == null) preload(activity)
        if (ad != null && !AdState.showing) {
            display(activity, onDone)
            return true
        }
        if (waitMs <= 0L || AdState.showing) {
            onDone()
            return false
        }
        val end = System.currentTimeMillis() + waitMs
        val poll = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                if (ad != null && !AdState.showing) {
                    display(activity, onDone)
                    return
                }
                if (System.currentTimeMillis() >= end || AdState.showing) {
                    onDone()
                    return
                }
                handler.postDelayed(this, 250)
            }
        }
        handler.postDelayed(poll, 250)
        return false
    }
}
