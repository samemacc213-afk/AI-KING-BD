package com.aikingbd.reader

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenTextService : AccessibilityService() {

    companion object {
        @Volatile var instance: ScreenTextService? = null
    }

    @Volatile var lastSource: String = ""

    private class Item(val text: String, val top: Int, val left: Int)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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

    private fun appName(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    // ---------- best available text for the box: Bangla from the app text, English/code by OCR ----------

    fun extractBest(box: Rect, done: (String, String) -> Unit) {
        val acc = try { extract(box) } catch (e: Exception) { "" }

        var answered = false
        val finish: (String, String) -> Unit = { t, m ->
            if (!answered) {
                answered = true
                done(t, m)
            }
        }

        val hasBangla = acc.any { it in '\u0980'..'\u09FF' }
        if (hasBangla || Build.VERSION.SDK_INT < 30) {
            finish(acc, "text")
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({ finish(acc, "text") }, 8000)

        screenshotText(box) { ocr ->
            if (ocr.isNotBlank()) finish(ocr, "OCR") else finish(acc, "text")
        }
    }

    private fun screenshotText(box: Rect, cb: (String) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                            val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hw?.recycle()
                            hb.close()
                            if (soft == null) {
                                cb("")
                                return
                            }
                            val l = box.left.coerceIn(0, soft.width - 1)
                            val t = box.top.coerceIn(0, soft.height - 1)
                            val r = box.right.coerceIn(l + 1, soft.width)
                            val b = box.bottom.coerceIn(t + 1, soft.height)
                            var crop = Bitmap.createBitmap(soft, l, t, r - l, b - t)
                            if (crop.width < 1000) {
                                crop = Bitmap.createScaledBitmap(crop, crop.width * 2, crop.height * 2, true)
                            }
                            crop = maybeInvert(crop)
                            recognizer.process(InputImage.fromBitmap(crop, 0))
                                .addOnSuccessListener { text -> cb(text.text.trim()) }
                                .addOnFailureListener { cb("") }
                        } catch (e: Exception) {
                            cb("")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        cb("")
                    }
                }
            )
        } catch (e: Exception) {
            cb("")
        }
    }

    // dark backgrounds are easier to read after inverting the colors
    private fun maybeInvert(bmp: Bitmap): Bitmap {
        return try {
            val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
            var sum = 0L
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val c = small.getPixel(x, y)
                    sum += (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
                }
            }
            if (sum / 256 > 110) return bmp
            val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val p = Paint()
            p.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            Canvas(out).drawBitmap(bmp, 0f, 0f, p)
            out
        } catch (e: Exception) {
            bmp
        }
    }

    // ---------- app text (works well for Telegram, Facebook, browsers' normal pages) ----------

    fun extract(box: Rect): String {
        lastSource = ""
        val items = mutableListOf<Item>()
        var root: AccessibilityNodeInfo? = try { rootInActiveWindow } catch (e: Exception) { null }
        if (root == null || root.packageName?.toString() == packageName) {
            root = null
            try {
                val wins = windows
                    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .sortedByDescending { if (it.isActive) 2 else if (it.isFocused) 1 else 0 }
                for (win in wins) {
                    val r = win.root ?: continue
                    if (r.packageName?.toString() == packageName) continue
                    root = r
                    break
                }
            } catch (_: Exception) {
            }
        }
        val rt = root ?: return ""
        lastSource = appName(rt.packageName?.toString() ?: "")
        walk(rt, box, items, 0)

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
