package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedManager {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: RewardedAd? = null
    private var loading = false
    private var attempt = 0

    fun preload(ctx: Context) {
        if (loading || ad != null) return
        loading = true
        val app = ctx.applicationContext
        RewardedAd.load(
            app,
            AdIds.rewarded(),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
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
