package com.aikingbd.reader

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

// requested only when the user taps WATCH AD and the normal rewarded ad is not available
object RewardedInterstitialManager {
    private val handler = Handler(Looper.getMainLooper())
    private var ad: RewardedInterstitialAd? = null
    private var loading = false

    private fun load(activity: Activity) {
        if (loading || ad != null) return
        loading = true
        RewardedInterstitialAd.load(
            activity.applicationContext,
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

    fun loadAndShow(activity: Activity, waitMs: Long, onReward: () -> Unit, onFail: () -> Unit) {
        if (AdState.showing) {
            onFail()
            return
        }
        load(activity)
        val end = System.currentTimeMillis() + waitMs
        val poll = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                val a = ad
                if (a != null && !AdState.showing) {
                    ad = null
                    AdState.showing = true
                    a.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            AdState.closed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            AdState.closed()
                            onFail()
                        }
                    }
                    a.show(activity) { onReward() }
                    return
                }
                if (System.currentTimeMillis() >= end || (!loading && ad == null)) {
                    onFail()
                    return
                }
                handler.postDelayed(this, 300)
            }
        }
        poll.run()
    }
}
