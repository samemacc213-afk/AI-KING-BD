package com.aikingbd.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

// Which floating buttons are shown (saved on the phone)
object FloatPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("fbtn", Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String): Boolean =
        sp(ctx).getBoolean(key, key == "read" || key == "select")

    fun set(ctx: Context, key: String, v: Boolean) {
        sp(ctx).edit().putBoolean(key, v).apply()
    }

    fun anyOn(ctx: Context): Boolean =
        get(ctx, "read") || get(ctx, "select") || get(ctx, "rec") || get(ctx, "text")
}

// A card with four switches, one for each floating button
object ButtonSwitches {
    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun findScroll(v: View): ScrollView? {
        if (v is ScrollView) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = findScroll(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }

    fun attach(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val sv = findScroll(root) ?: return
        val col = sv.getChildAt(0) as? LinearLayout ?: return
        if (col.findViewWithTag<View>("fbtn_card") != null) return

        val card = LinearLayout(activity)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundResource(R.drawable.bg_card)
        card.tag = "fbtn_card"
        val pad = dp(activity, 18)
        card.setPadding(pad, pad, pad, pad)

        val title = TextView(activity)
        title.text = "FLOATING BUTTONS"
        title.textSize = 18f
        title.typeface = Typeface.SERIF
        title.setTextColor(ContextCompat.getColor(activity, R.color.navy_text))
        card.addView(title)

        val sub = TextView(activity)
        sub.text = "Turn on only the buttons you want. They stay together."
        sub.textSize = 12f
        sub.setTextColor(Color.parseColor("#B8860B"))
        card.addView(sub)

        addRow(activity, card, R.drawable.ic_emoji_button, "Read aloud \u2022 round button", "read")
        addRow(activity, card, R.drawable.ic_select_button, "Select text \u2022 scan box", "select")
        addRow(activity, card, R.drawable.ic_rec_button, "Voice record \u2022 mic", "rec")
        addRow(activity, card, R.drawable.ic_text_button, "Voice to text \u2022 copy", "text")

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(activity, 14)
        col.addView(card, minOf(2, col.childCount), lp)
    }

    private fun addRow(act: Activity, card: LinearLayout, iconRes: Int, label: String, key: String) {
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val icon = ImageView(act)
        icon.setImageResource(iconRes)
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        row.addView(icon, LinearLayout.LayoutParams(dp(act, 32), dp(act, 32)))

        val tv = TextView(act)
        tv.text = label
        tv.textSize = 15f
        tv.typeface = Typeface.SERIF
        tv.setTextColor(ContextCompat.getColor(act, R.color.navy_text))
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.marginStart = dp(act, 12)
        row.addView(tv, tlp)

        val sw = SwitchCompat(act)
        sw.isChecked = FloatPrefs.get(act, key)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Color.parseColor("#C9A227"), Color.parseColor("#9E9E9E")))
        sw.trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#E9D88A"), Color.parseColor("#D0D0D0")))
        sw.setOnCheckedChangeListener { _, checked ->
            FloatPrefs.set(act, key, checked)
            sync(act)
        }
        row.addView(sw)

        val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(act, 12)
        card.addView(row, rlp)
    }

    // Keeps the main floating switch in step: any button on -> floating group on
    private fun sync(act: Activity) {
        try {
            val master = act.findViewById<SwitchCompat>(R.id.switchFloat)
            val any = FloatPrefs.anyOn(act)
            if (master != null && any && !master.isChecked) {
                master.isChecked = true
            } else if (master != null && !any && master.isChecked) {
                master.isChecked = false
            } else if (Prefs(act).enabled) {
                ContextCompat.startForegroundService(
                    act,
                    Intent(act, FloatingService::class.java).setAction(FloatingService.ACTION_REFRESH)
                )
            }
        } catch (e: Exception) {
        }
    }
}
