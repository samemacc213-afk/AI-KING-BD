package com.aikingbd.reader

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class ScreenTextService : AccessibilityService() {

    companion object {
        @Volatile var instance: ScreenTextService? = null
    }

    private class Item(val text: String, val top: Int, val left: Int)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun extract(box: Rect): String {
        val items = mutableListOf<Item>()
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            for (win in windows) {
                if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = win.root ?: continue
                roots.add(r)
            }
        } catch (_: Exception) {
        }
        if (roots.isEmpty()) {
            rootInActiveWindow?.let { roots.add(it) }
        }
        for (r in roots) {
            if (r.packageName?.toString() == packageName) continue
            walk(r, box, items, 0)
        }

        items.sortWith(compareBy<Item>({ it.top / 12 }, { it.left }))
        val seen = mutableListOf<String>()
        val sb = StringBuilder()
        for (item in items) {
            val t = item.text.trim()
            if (t.isEmpty()) continue
            if (seen.any { s -> s.contains(t) }) continue
            seen.add(t)
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(t)
        }
        return sb.toString().trim()
    }

    private fun walk(node: AccessibilityNodeInfo?, box: Rect, out: MutableList<Item>, depth: Int) {
        if (node == null || depth > 60 || out.size > 400) return
        val b = Rect()
        node.getBoundsInScreen(b)
        if (!b.isEmpty && !Rect.intersects(b, box)) return

        if (node.isVisibleToUser && !node.isPassword) {
            val t = node.text?.toString()
            if (!t.isNullOrBlank() && !b.isEmpty) {
                val picked = pick(node, t, b, box)
                if (!picked.isNullOrBlank()) out.add(Item(picked, b.top, b.left))
            }
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), box, out, depth + 1)
        }
    }

    private fun pick(node: AccessibilityNodeInfo, text: String, b: Rect, box: Rect): String? {
        val inter = Rect()
        if (!inter.setIntersect(b, box)) return null
        val nodeArea = maxOf(1L, b.width().toLong() * b.height().toLong())
        val frac = (inter.width().toLong() * inter.height().toLong()).toFloat() / nodeArea.toFloat()
        if (frac >= 0.9f) return text
        val part = partial(node, text, b, box)
        if (part != null) return if (part.isBlank()) null else part
        return if (frac >= 0.4f) text else null
    }

    // picks only the characters that lie inside the box; null when the app does not report character positions
    private fun partial(node: AccessibilityNodeInfo, text: String, b: Rect, box: Rect): String? {
        if (text.length > 5000) return null
        try {
            val args = Bundle()
            args.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            args.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
            if (!node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) {
                return null
            }
            @Suppress("DEPRECATION")
            val arr = node.extras.getParcelableArray(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)
                ?: return null
            if (arr.size != text.length) return null

            var sane = false
            for (p in arr) {
                val r = p as? RectF ?: continue
                sane = r.centerX() >= b.left - 80 && r.centerX() <= b.right + 80 &&
                    r.centerY() >= b.top - 80 && r.centerY() <= b.bottom + 80
                break
            }
            if (!sane) return null

            val sb = StringBuilder()
            var prevY = Float.NaN
            for (i in text.indices) {
                val r = arr[i] as? RectF ?: continue
                if (r.isEmpty) continue
                if (box.contains(r.centerX().toInt(), r.centerY().toInt())) {
                    if (!prevY.isNaN() && r.centerY() - prevY > r.height() * 0.6f &&
                        sb.isNotEmpty() && !sb.last().isWhitespace()
                    ) {
                        sb.append('\n')
                    }
                    sb.append(text[i])
                    prevY = r.centerY()
                }
            }
            return sb.toString().trim()
        } catch (e: Exception) {
            return null
        }
    }
}
