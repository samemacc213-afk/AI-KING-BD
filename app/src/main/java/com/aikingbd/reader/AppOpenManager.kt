package com.aikingbd.reader
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
object AppOpenManager {
    private const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    private const val RETRY_MS = 15_000L
    private val handler = Handler(Looper.getMainLooper())
    private var cached: AppOpenAd? = null
    private var cachedAt = 0L
    private var loading = false
    private var showing = false
    private var lastClosedAt = 0L
    private var retrying = false
    private fun fresh() = cached != null && System.currentTimeMillis() - cachedAt < MAX_AGE_MS
    private fun usable(a: Activity): Boolean {
        if (a.isFinishing || a.isDestroyed) return false
        val owner = a as? LifecycleOwner ?: return true
        return owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    fun showOnLaunch(activity: Activity) {
        val now = System.currentTimeMillis()
        if (!showing && now - lastClosedAt >= 3000L && fresh()) display(activity)
        else if (!loading) request(activity.applicationContext)
        ensureRetry(activity.applicationContext)
    }
    fun keepForNext(ctx: Context) {
        if (!fresh() && !loading) request(ctx.applicationContext)
        ensureRetry(ctx.applicationContext)
    }
    private fun ensureRetry(app: Context) {
        if (retrying) return
        retrying = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!fresh() && !loading) request(app)
                handler.postDelayed(this, RETRY_MS)
            }
        }, RETRY_MS)
    }
    private fun request(app: Context) {
        if (loading) return
        loading = true
        AppOpenAd.load(app, AdIds.appOpen(), AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading = false; cached = ad; cachedAt = System.currentTimeMillis()
                    AdLog.set(false, "loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    AdLog.set(false, "failed " + error.code + ": " + error.message)
                }
            })
    }
    private fun display(activity: Activity) {
        val ad = cached ?: return
        if (!usable(activity)) return
        cached = null; showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false; lastClosedAt = System.currentTimeMillis(); request(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false; lastClosedAt = System.currentTimeMillis()
            }
        }
        ad.show(activity)
    }
}
