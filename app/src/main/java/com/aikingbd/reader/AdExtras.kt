package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

// Native, Rewarded and Rewarded Interstitial ads.
// Rewarded ads only play when the user taps; the rewarded interstitial is offered with a
// "No thanks" option, as AdMob policy requires.
object AdExtras {
    private val handler = Handler(Looper.getMainLooper())
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var rewardedInt: RewardedInterstitialAd? = null
    private var rewardedIntLoading = false
    private var nativeAd: NativeAd? = null
    private var lastOffer = 0L
    private const val OFFER_GAP_MS = 120_000L

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    // Adds the native ad card and the "watch ad" button at the bottom of the main page
    fun attach(activity: Activity, scroll: View) {
        val sv = scroll as? ScrollView ?: return
        val col = sv.getChildAt(0) as? LinearLayout ?: return
        preloadRewarded(activity)
        preloadRewardedInt(activity)

        val slot = FrameLayout(activity)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(activity, 14)
        col.addView(slot, lp)
        loadNative(activity, slot)

        val btn = TextView(activity)
        btn.text = "WATCH AD \u2022 SUPPORT US"
        btn.setTextColor(ContextCompat.getColor(activity, R.color.navy_text))
        btn.textSize = 15f
        btn.typeface = Typeface.SERIF
        btn.gravity = Gravity.CENTER
        btn.setBackgroundResource(R.drawable.bg_gold_button)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52))
        blp.topMargin = dp(activity, 14)
        blp.bottomMargin = dp(activity, 18)
        col.addView(btn, blp)
        btn.setOnClickListener { showRewarded(activity) }
    }

    // ---------- Native ----------
    private fun loadNative(activity: Activity, slot: FrameLayout) {
        val loader = AdLoader.Builder(activity, AdIds.native())
            .forNativeAd { ad ->
                if (activity.isDestroyed || activity.isFinishing) {
                    ad.destroy()
                } else {
                    nativeAd?.destroy()
                    nativeAd = ad
                    slot.removeAllViews()
                    slot.addView(buildNativeView(activity, ad))
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    handler.postDelayed({
                        if (!activity.isDestroyed && !activity.isFinishing) loadNative(activity, slot)
                    }, 30_000L)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    private fun buildNativeView(ctx: Context, ad: NativeAd): NativeAdView {
        val navy = ContextCompat.getColor(ctx, R.color.navy_text)
        val view = NativeAdView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundResource(R.drawable.bg_card)
        val pad = dp(ctx, 16)
        col.setPadding(pad, pad, pad, pad)

        val tag = TextView(ctx)
        tag.text = "Ad"
        tag.textSize = 11f
        tag.setTextColor(navy)
        col.addView(tag)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val icon = ImageView(ctx)
        val isz = dp(ctx, 44)
        row.addView(icon, LinearLayout.LayoutParams(isz, isz))
        val headline = TextView(ctx)
        headline.textSize = 16f
        headline.typeface = Typeface.SERIF
        headline.setTextColor(navy)
        val hlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        hlp.marginStart = dp(ctx, 10)
        row.addView(headline, hlp)
        col.addView(row)

        val media = MediaView(ctx)
        val mlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 170))
        mlp.topMargin = dp(ctx, 10)
        col.addView(media, mlp)

        val body = TextView(ctx)
        body.textSize = 13f
        body.setTextColor(navy)
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.topMargin = dp(ctx, 8)
        col.addView(body, blp)

        val cta = TextView(ctx)
        cta.textSize = 14f
        cta.typeface = Typeface.SERIF
        cta.gravity = Gravity.CENTER
        cta.setTextColor(navy)
        cta.setBackgroundResource(R.drawable.bg_gold_button)
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 46))
        clp.topMargin = dp(ctx, 10)
        col.addView(cta, clp)

        view.addView(col)
        view.iconView = icon
        view.headlineView = headline
        view.mediaView = media
        view.bodyView = body
        view.callToActionView = cta

        headline.text = ad.headline
        val ic = ad.icon
        if (ic != null) icon.setImageDrawable(ic.drawable) else icon.visibility = View.GONE
        val mc = ad.mediaContent
        if (mc != null) media.setMediaContent(mc) else media.visibility = View.GONE
        if (ad.body != null) body.text = ad.body else body.visibility = View.GONE
        if (ad.callToAction != null) cta.text = ad.callToAction else cta.visibility = View.GONE
        view.setNativeAd(ad)
        return view
    }

    // ---------- Rewarded ----------
    private fun preloadRewarded(ctx: Context) {
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        val app = ctx.applicationContext
        RewardedAd.load(app, AdIds.rewarded(), AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                rewardedLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewarded = null
                rewardedLoading = false
                handler.postDelayed({ preloadRewarded(app) }, 30_000L)
            }
        })
    }

    private fun showRewarded(activity: Activity) {
        val ad = rewarded
        if (ad == null) {
            Toast.makeText(activity, "The ad is not ready yet. Try again in a moment.", Toast.LENGTH_SHORT).show()
            preloadRewarded(activity)
            return
        }
        rewarded = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewarded(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadRewarded(activity)
            }
        }
        ad.show(activity) { _ ->
            Toast.makeText(activity, "Thank you for supporting us!", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Rewarded interstitial ----------
    private fun preloadRewardedInt(ctx: Context) {
        if (rewardedInt != null || rewardedIntLoading) return
        rewardedIntLoading = true
        val app = ctx.applicationContext
        RewardedInterstitialAd.load(app, AdIds.rewardedInterstitial(), AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInt = ad
                    rewardedIntLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInt = null
                    rewardedIntLoading = false
                    handler.postDelayed({ preloadRewardedInt(app) }, 30_000L)
                }
            })
    }

    // Offered after a voice message is made (never forced, at most every 2 minutes)
    fun offerAfterVoice(activity: Activity) {
        val ad = rewardedInt
        val now = System.currentTimeMillis()
        if (ad == null || now - lastOffer < OFFER_GAP_MS || activity.isFinishing || activity.isDestroyed) {
            preloadRewardedInt(activity)
            return
        }
        lastOffer = now
        AlertDialog.Builder(activity)
            .setTitle("Support us")
            .setMessage("Watch a short ad to support AI KING BD?")
            .setPositiveButton("Watch") { _, _ -> showRewardedInt(activity) }
            .setNegativeButton("No thanks", null)
            .show()
    }

    private fun showRewardedInt(activity: Activity) {
        val ad = rewardedInt
        if (ad == null) {
            preloadRewardedInt(activity)
            return
        }
        rewardedInt = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewardedInt(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadRewardedInt(activity)
            }
        }
        ad.show(activity) { _ ->
            Toast.makeText(activity, "Thank you for supporting us!", Toast.LENGTH_LONG).show()
        }
    }
}
