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

object AdGate {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: InterstitialAd? = null
    private var loading = false
    private var lastShown = 0L
    private const val MIN_GAP_MS = 3000L

    fun preload(ctx: Context) {
        if (ad != null || loading) return
        loading = true
        val app = ctx.applicationContext
        InterstitialAd.load(app, AdIds.interstitial(), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                    handler.postDelayed({ preload(app) }, 20_000L)
                }
            })
    }

    // Shows an ad first when one is ready, then runs the action.
    // If no ad is ready, the action runs immediately.
    fun showThen(activity: Activity, action: () -> Unit) {
        val a = ad
        val now = System.currentTimeMillis()
        if (a == null || activity.isFinishing || activity.isDestroyed || now - lastShown < MIN_GAP_MS) {
            preload(activity)
            action()
            return
        }
        ad = null
        var done = false
        val finish = {
            if (!done) {
                done = true
                lastShown = System.currentTimeMillis()
                preload(activity)
                action()
            }
        }
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                finish()
            }
        }
        try {
            a.show(activity)
        } catch (e: Exception) {
            finish()
        }
    }
}
