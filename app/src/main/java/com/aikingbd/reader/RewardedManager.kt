package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedManager {
    private var ad: RewardedAd? = null
    private var loading = false

    fun preload(ctx: Context) {
        if (loading || ad != null) return
        loading = true
        RewardedAd.load(
            ctx.applicationContext,
            AdIds.rewarded(),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
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
        if (a == null) {
            preload(activity)
            onNotReady()
            return
        }
        ad = null
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload(activity)
                onNotReady()
            }
        }
        a.show(activity) { onReward() }
    }
}
