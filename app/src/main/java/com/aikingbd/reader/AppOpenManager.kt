package com.aikingbd.reader

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

// Simple version: when the user enters the app, ask for one app-open ad and show it as soon as it arrives.
object AppOpenManager {
    private var loading = false
    private var showing = false
    private var lastClosedAt = 0L

    fun showOnLaunch(activity: Activity) {
        val now = System.currentTimeMillis()
        if (loading || showing || now - lastClosedAt < 5000L) return
        loading = true
        val asked = now
        AppOpenAd.load(
            activity.applicationContext,
            AdIds.appOpen(),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading = false
                    if (activity.isFinishing || activity.isDestroyed) return
                    // do not pop up in the middle of use if it arrived very late
                    if (System.currentTimeMillis() - asked > 15000L) return
                    val owner = activity as? LifecycleOwner
                    if (owner != null && !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                    showing = true
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            showing = false
                            lastClosedAt = System.currentTimeMillis()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            showing = false
                            lastClosedAt = System.currentTimeMillis()
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
