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
import kotlin.math.hypot

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
    private val margin = (28 * d).toInt()
    private val minSize = (80 * d).toInt()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    // the box in screen coordinates
    private val rect = Rect()
    private var offX = 0
    private var offY = 0
    private var view: BoxView? = null
    private var lp: WindowManager.LayoutParams? = null

    var isOpen = false
        private set

    private inner class BoxView(c: Context) : View(c) {
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
            color = Color.parseColor("#14FFFFFF")
        }
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E53935")
        }
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

        private var active: Kind? = null
        private var downX = 0f
        private var downY = 0f
        private val start = Rect()
        private var moved = false

        private fun pt(k: Kind): Pair<Float, Float> {
            val l = margin.toFloat()
            val t = margin.toFloat()
            val r = (width - margin).toFloat()
            val b = (height - margin).toFloat()
            val cx = (l + r) / 2f
            val cy = (t + b) / 2f
            return when (k) {
                Kind.CLOSE -> Pair(l, t)
                Kind.COPY -> Pair(r, t)
                Kind.RESIZE -> Pair(r, b)
                Kind.LEFT -> Pair(l, cy)
                Kind.RIGHT -> Pair(r, cy)
                Kind.TOP -> Pair(cx, t)
                Kind.BOTTOM -> Pair(cx, b)
                Kind.MOVE -> Pair(cx, cy)
            }
        }

        private fun hit(x: Float, y: Float): Kind? {
            val reach = 26 * d
            for (k in listOf(Kind.CLOSE, Kind.COPY, Kind.RESIZE, Kind.LEFT, Kind.RIGHT, Kind.TOP, Kind.BOTTOM)) {
                val p = pt(k)
                if (hypot(x - p.first, y - p.second) <= reach) return k
            }
            // dragging the white border line moves the whole box
            val band = 14 * d
            val l = margin.toFloat()
            val t = margin.toFloat()
            val r = (width - margin).toFloat()
            val b = (height - margin).toFloat()
            val inOuter = x >= l - band && x <= r + band && y >= t - band && y <= b + band
            val inInner = x > l + band && x < r - band && y > t + band && y < b - band
            return if (inOuter && !inInner) Kind.MOVE else null
        }

        override fun onDraw(canvas: Canvas) {
            val l = margin.toFloat()
            val t = margin.toFloat()
            val r = (width - margin).toFloat()
            val b = (height - margin).toFloat()
            canvas.drawRect(l, t, r, b, fill)
            canvas.drawRect(l, t, r, b, shadow)
            canvas.drawRect(l, t, r, b, line)

            for (k in listOf(Kind.LEFT, Kind.RIGHT, Kind.TOP, Kind.BOTTOM)) {
                val p = pt(k)
                canvas.drawCircle(p.first, p.second, 5 * d, white)
                canvas.drawCircle(p.first, p.second, 5 * d, ring)
            }

            val hr = 16 * d
            val s = hr * 0.5f

            val pc = pt(Kind.CLOSE)
            canvas.drawCircle(pc.first, pc.second, hr, red)
            ink.color = Color.WHITE
            ink.strokeWidth = 2.5f * d
            canvas.drawLine(pc.first - s, pc.second - s, pc.first + s, pc.second + s, ink)
            canvas.drawLine(pc.first + s, pc.second - s, pc.first - s, pc.second + s, ink)

            val pp = pt(Kind.COPY)
            canvas.drawCircle(pp.first, pp.second, hr, white)
            canvas.drawCircle(pp.first, pp.second, hr, ring)
            ink.color = Color.parseColor("#0B1B3F")
            ink.strokeWidth = 3.5f * d
            canvas.drawLine(pp.first - s * 0.5f, pp.second + s * 0.5f, pp.first + s, pp.second - s, ink)
            ink.color = Color.parseColor("#FB8C00")
            canvas.drawLine(pp.first - s, pp.second + s, pp.first - s * 0.5f, pp.second + s * 0.5f, ink)

            val pr = pt(Kind.RESIZE)
            canvas.drawCircle(pr.first, pr.second, hr, white)
            canvas.drawCircle(pr.first, pr.second, hr, ring)
            ink.color = Color.parseColor("#0B1B3F")
            ink.strokeWidth = 2.5f * d
            canvas.drawLine(pr.first - s, pr.second - s, pr.first + s, pr.second + s, ink)
            canvas.drawLine(pr.first - s, pr.second - s, pr.first - s + s * 0.7f, pr.second - s, ink)
            canvas.drawLine(pr.first - s, pr.second - s, pr.first - s, pr.second - s + s * 0.7f, ink)
            canvas.drawLine(pr.first + s, pr.second + s, pr.first + s - s * 0.7f, pr.second + s, ink)
            canvas.drawLine(pr.first + s, pr.second + s, pr.first + s, pr.second + s - s * 0.7f, ink)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    val k = hit(e.x, e.y) ?: return false
                    active = k
                    downX = e.rawX
                    downY = e.rawY
                    start.set(rect)
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val k = active ?: return false
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) applyDrag(k, start, dx, dy)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val k = active
                    active = null
                    if (k != null && !moved && e.action == MotionEvent.ACTION_UP) {
                        if (k == Kind.CLOSE) {
                            this@SelectionBox.close()
                        } else if (k == Kind.COPY) {
                            this@SelectionBox.doCopy()
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    fun open() {
        if (isOpen) return
        val w = (screenW * 0.8f).toInt()
        val h = (screenH * 0.18f).toInt()
        val top = (screenH * 0.35f).toInt()
        rect.set((screenW - w) / 2, top, (screenW + w) / 2, top + h)

        val v = BoxView(ctx)
        val p = WindowManager.LayoutParams(
            rect.width() + 2 * margin,
            rect.height() + 2 * margin,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        p.x = rect.left - margin
        p.y = rect.top - margin
        wm.addView(v, p)
        view = v
        lp = p
        isOpen = true
        v.post { measureOffset() }
        v.postDelayed({ measureOffset() }, 150)
    }

    // finds the real difference between window position and screen position
    private fun measureOffset() {
        val v = view ?: return
        val p = lp ?: return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        offX = loc[0] - p.x
        offY = loc[1] - p.y
        layoutWindow()
    }

    private fun layoutWindow() {
        val v = view ?: return
        val p = lp ?: return
        p.x = rect.left - margin - offX
        p.y = rect.top - margin - offY
        p.width = rect.width() + 2 * margin
        p.height = rect.height() + 2 * margin
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
        v.invalidate()
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
        layoutWindow()
    }

    // the box position is measured from the real window at the moment of copying
    private fun doCopy() {
        val v = view ?: return
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val r = Rect(
            loc[0] + margin,
            loc[1] + margin,
            loc[0] + margin + rect.width(),
            loc[1] + margin + rect.height()
        )
        onCopy(r)
    }

    fun setHidden(hidden: Boolean) {
        view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        lp = null
        onClosed()
    }
}
