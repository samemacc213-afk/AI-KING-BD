package com.aikingbd.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

class SelectionBox(
    private val ctx: Context,
    private val wm: WindowManager,
    private val onCopy: (Rect) -> Unit,
    private val onClosed: () -> Unit
) {
    private enum class Kind { CLOSE, COPY, RESIZE, MOVE, LEFT, RIGHT, TOP, BOTTOM }

    private val dm = ctx.resources.displayMetrics
    private val d = dm.density
    private val screenW = dm.widthPixels
    private val screenH = dm.heightPixels
    private val rect = Rect()
    private var offX = 0
    private var offY = 0
    private var border: BorderView? = null
    private var borderLp: WindowManager.LayoutParams? = null
    private val handles = mutableListOf<Pair<HandleView, WindowManager.LayoutParams>>()
    private val handleSize = (40 * d).toInt()
    private val minSize = (80 * d).toInt()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    var isOpen = false
        private set

    private inner class BorderView(c: Context) : View(c) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2 * d
            color = Color.WHITE
        }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * d
            color = Color.parseColor("#66000000")
        }
        private val fill = Paint().apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#18FFFFFF")
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, fill)
            canvas.drawRect(2 * d, 2 * d, w - 2 * d, h - 2 * d, shadow)
            canvas.drawRect(2 * d, 2 * d, w - 2 * d, h - 2 * d, line)
        }
    }

    private inner class HandleView(c: Context, val kind: Kind) : View(c) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            color = Color.parseColor("#55000000")
        }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.5f * d
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val big = kind == Kind.CLOSE || kind == Kind.COPY || kind == Kind.RESIZE
            val r = if (big) width / 2f - 2 * d else width / 5f
            bg.color = if (kind == Kind.CLOSE) Color.parseColor("#E53935") else Color.WHITE
            if (kind == Kind.MOVE) {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, bg)
                return
            }
            canvas.drawCircle(cx, cy, r, bg)
            canvas.drawCircle(cx, cy, r, ring)
            val s = r * 0.5f
            when (kind) {
                Kind.CLOSE -> {
                    ink.color = Color.WHITE
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, ink)
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, ink)
                }
                Kind.COPY -> {
                    ink.color = Color.parseColor("#0B1B3F")
                    ink.strokeWidth = 3.5f * d
                    canvas.drawLine(cx - s * 0.5f, cy + s * 0.5f, cx + s, cy - s, ink)
                    ink.color = Color.parseColor("#FB8C00")
                    canvas.drawLine(cx - s, cy + s, cx - s * 0.5f, cy + s * 0.5f, ink)
                    ink.strokeWidth = 2.5f * d
                }
                Kind.RESIZE -> {
                    ink.color = Color.parseColor("#0B1B3F")
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, ink)
                    canvas.drawLine(cx - s, cy - s, cx - s + s * 0.7f, cy - s, ink)
                    canvas.drawLine(cx - s, cy - s, cx - s, cy - s + s * 0.7f, ink)
                    canvas.drawLine(cx + s, cy + s, cx + s - s * 0.7f, cy + s, ink)
                    canvas.drawLine(cx + s, cy + s, cx + s, cy + s - s * 0.7f, ink)
                }
                else -> {}
            }
        }
    }

    private fun baseParams(w: Int, h: Int, notTouchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        flags = if (notTouchable) {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun open() {
        if (isOpen) return
        val w = (screenW * 0.8f).toInt()
        val h = (screenH * 0.18f).toInt()
        val top = (screenH * 0.35f).toInt()
        rect.set((screenW - w) / 2, top, (screenW + w) / 2, top + h)

        val b = BorderView(ctx)
        val bl = baseParams(rect.width(), rect.height(), true)
        bl.alpha = 0.8f
        wm.addView(b, bl)
        border = b
        borderLp = bl

        for (k in Kind.values()) {
            val hv = HandleView(ctx, k)
            val lp = baseParams(handleSize, handleSize, false)
            hv.setOnTouchListener(makeTouch(k))
            wm.addView(hv, lp)
            handles.add(Pair(hv, lp))
        }
        isOpen = true
        layoutAll()
        b.post {
            val loc = IntArray(2)
            b.getLocationOnScreen(loc)
            val lp = borderLp
            if (lp != null) {
                offX = loc[0] - lp.x
                offY = loc[1] - lp.y
            }
            layoutAll()
        }
    }

    private fun centerOf(k: Kind): Pair<Int, Int> = when (k) {
        Kind.CLOSE -> Pair(rect.left, rect.top)
        Kind.COPY -> Pair(rect.right, rect.top)
        Kind.RESIZE -> Pair(rect.right, rect.bottom)
        Kind.MOVE -> Pair(rect.centerX(), rect.centerY())
        Kind.LEFT -> Pair(rect.left, rect.centerY())
        Kind.RIGHT -> Pair(rect.right, rect.centerY())
        Kind.TOP -> Pair(rect.centerX(), rect.top)
        Kind.BOTTOM -> Pair(rect.centerX(), rect.bottom)
    }

    private fun layoutAll() {
        val b = border ?: return
        val bl = borderLp ?: return
        bl.x = rect.left - offX
        bl.y = rect.top - offY
        bl.width = rect.width()
        bl.height = rect.height()
        try { wm.updateViewLayout(b, bl) } catch (_: Exception) {}
        for ((hv, lp) in handles) {
            val p = centerOf(hv.kind)
            lp.x = p.first - handleSize / 2 - offX
            lp.y = p.second - handleSize / 2 - offY
            try { wm.updateViewLayout(hv, lp) } catch (_: Exception) {}
        }
    }

    private fun applyDrag(k: Kind, s: Rect, dx: Int, dy: Int) {
        when (k) {
            Kind.MOVE -> {
                val w = s.width()
                val h = s.height()
                val l = (s.left + dx).coerceIn(0, screenW - w)
                val t = (s.top + dy).coerceIn(0, screenH - h)
                rect.set(l, t, l + w, t + h)
            }
            Kind.RESIZE -> {
                rect.right = (s.right + dx).coerceIn(s.left + minSize, screenW)
                rect.bottom = (s.bottom + dy).coerceIn(s.top + minSize, screenH)
            }
            Kind.LEFT -> rect.left = (s.left + dx).coerceIn(0, s.right - minSize)
            Kind.RIGHT -> rect.right = (s.right + dx).coerceIn(s.left + minSize, screenW)
            Kind.TOP -> rect.top = (s.top + dy).coerceIn(0, s.bottom - minSize)
            Kind.BOTTOM -> rect.bottom = (s.bottom + dy).coerceIn(s.top + minSize, screenH)
            else -> return
        }
        layoutAll()
    }

    private fun makeTouch(k: Kind): View.OnTouchListener {
        return object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            val start = Rect()
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX
                        downY = e.rawY
                        start.set(rect)
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (moved) applyDrag(k, start, dx, dy)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            if (k == Kind.CLOSE) {
                                close()
                            } else if (k == Kind.COPY) {
                                onCopy(Rect(rect))
                            }
                        }
                        return true
                    }
                }
                return false
            }
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        border?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        border = null
        for ((hv, _) in handles) {
            try { wm.removeView(hv) } catch (_: Exception) {}
        }
        handles.clear()
        onClosed()
    }
}
