package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

object RewardedInterstitialManager {
    private var ad: RewardedInterstitialAd? = null
    private var loading = false

    fun preload(ctx: Context) {
        if (loading || ad != null) return
        loading = true
        RewardedInterstitialAd.load(
            ctx.applicationContext,
            AdIds.rewardedInterstitial(),
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedInterstitialAd) {
                    loading = false
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            }
        )
    }

    fun show(activity: Activity, onReward: () -> Unit, onNotReady: () -> Unit) {
        val a = ad
        if (a == null || AdState.showing) {
            preload(activity)
            onNotReady()
            return
        }
        ad = null
        AdState.showing = true
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdState.closed()
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdState.closed()
                preload(activity)
                onNotReady()
            }
        }
        a.show(activity) { onReward() }
    }
}
