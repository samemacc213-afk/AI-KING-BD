package com.aikingbd.reader

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenManager {
    private var loading = false
    private var shown = false

    fun showOnLaunch(activity: Activity) {
        if (loading || shown) return
        loading = true
        AppOpenAd.load(
            activity,
            AdIds.appOpen(),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading = false
                    if (activity.isFinishing || activity.isDestroyed) return
                    shown = true
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            shown = false
                        }
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            }
        )
    }
}
