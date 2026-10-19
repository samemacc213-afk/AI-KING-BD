package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

object NativeAdHelper {

    fun attach(activity: Activity, holder: ViewGroup, index: Int) {
        if (holder !is LinearLayout) return
        val dp = activity.resources.displayMetrics.density
        val frame = FrameLayout(activity)
        frame.visibility = View.GONE
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (14 * dp).toInt()
        try {
            holder.addView(frame, index, lp)
        } catch (e: Exception) {
            return
        }

        AdLoader.Builder(activity, AdIds.native())
            .forNativeAd { ad ->
                if (activity.isDestroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                frame.removeAllViews()
                frame.addView(build(activity, ad))
                frame.visibility = View.VISIBLE
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    frame.visibility = View.GONE
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun build(ctx: Context, ad: NativeAd): NativeAdView {
        val dp = ctx.resources.displayMetrics.density
        val navy = Color.parseColor("#0B1B3F")
        val view = NativeAdView(ctx)

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundResource(R.drawable.bg_card)
        box.setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())

        val badge = TextView(ctx)
        badge.text = "Ad"
        badge.textSize = 11f
        badge.setTextColor(Color.WHITE)
        badge.setBackgroundColor(Color.parseColor("#C9A227"))
        badge.setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
        box.addView(badge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val headline = TextView(ctx)
        headline.text = ad.headline
        headline.textSize = 16f
        headline.setTextColor(navy)
        headline.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        headline.setPadding(0, (8 * dp).toInt(), 0, 0)
        view.headlineView = headline
        box.addView(headline)

        val bodyText = ad.body
        if (bodyText != null) {
            val body = TextView(ctx)
            body.text = bodyText
            body.textSize = 13f
            body.setTextColor(navy)
            body.setPadding(0, (4 * dp).toInt(), 0, 0)
            view.bodyView = body
            box.addView(body)
        }

        if (ad.mediaContent != null) {
            val mv = MediaView(ctx)
            val mlp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (170 * dp).toInt())
            mlp.topMargin = (10 * dp).toInt()
            box.addView(mv, mlp)
            view.mediaView = mv
        }

        val ctaText = ad.callToAction
        if (ctaText != null) {
            val cta = TextView(ctx)
            cta.text = ctaText
            cta.textSize = 14f
            cta.setTextColor(navy)
            cta.gravity = Gravity.CENTER
            cta.setBackgroundColor(Color.parseColor("#E8EEF8"))
            val clp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (44 * dp).toInt())
            clp.topMargin = (10 * dp).toInt()
            box.addView(cta, clp)
            view.callToActionView = cta
        }

        view.addView(box)
        view.setNativeAd(ad)
        return view
    }
}
