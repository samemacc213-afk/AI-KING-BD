package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialManager {
    private const val COOLDOWN_MS = 3 * 60 * 1000L

    private var ad: InterstitialAd? = null
    private var loading = false
    private var showing = false
    private var lastShownAt = 0L

    fun preload(ctx: Context) {
        if (loading || ad != null) return
        loading = true
        InterstitialAd.load(
            ctx.applicationContext,
            AdIds.interstitial(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            }
        )
    }

    fun showIfAllowed(activity: Activity) {
        if (showing) return
        val now = System.currentTimeMillis()
        if (lastShownAt != 0L && now - lastShownAt < COOLDOWN_MS) return
        val openAt = AppOpenManager.lastShown()
        if (openAt != 0L && now - openAt < 60_000L) return

        val a = ad
        if (a == null) {
            preload(activity)
            return
        }
        ad = null
        showing = true
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastShownAt = System.currentTimeMillis()
            }

            override fun onAdDismissedFullScreenContent() {
                showing = false
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                preload(activity)
            }
        }
        a.show(activity)
    }
}
