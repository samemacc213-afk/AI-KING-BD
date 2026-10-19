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
    private var ad: InterstitialAd? = null
    private var loading = false

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

    fun showNow(activity: Activity, onDone: () -> Unit): Boolean {
        val a = ad
        if (a == null || AdState.showing) {
            preload(activity)
            onDone()
            return false
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
        return true
    }
}
