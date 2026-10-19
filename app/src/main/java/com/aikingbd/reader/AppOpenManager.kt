package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenManager {
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L

    private var ad: AppOpenAd? = null
    private var loadedAt = 0L
    private var loading = false

    private fun fresh(): Boolean =
        ad != null && System.currentTimeMillis() - loadedAt < MAX_AGE_MS

    fun isReady(): Boolean = fresh()

    fun preload(ctx: Context) {
        if (loading || fresh()) return
        loading = true
        AppOpenAd.load(
            ctx.applicationContext,
            AdIds.appOpen(),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    loading = false
                    ad = loaded
                    loadedAt = System.currentTimeMillis()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                }
            }
        )
    }

    fun showIfReady(activity: Activity, onDone: (() -> Unit)? = null): Boolean {
        val now = System.currentTimeMillis()
        if (AdState.showing || now - AdState.lastClosedAt < 3000) {
            onDone?.invoke()
            return false
        }
        val a = ad
        if (a == null || !fresh()) {
            preload(activity)
            onDone?.invoke()
            return false
        }
        ad = null
        AdState.showing = true
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdState.closed()
                preload(activity)
                onDone?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdState.closed()
                preload(activity)
                onDone?.invoke()
            }
        }
        a.show(activity)
        return true
    }
}
