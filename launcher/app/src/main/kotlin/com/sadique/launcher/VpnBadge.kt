package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.style.ReplacementSpan

/** Renders the VPN status as a colored badge inside the network pill. */
class VpnBadge(lock: Drawable?, private val density: Float) : ReplacementSpan() {
    private val fill = if (lock != null) GREEN else RED
    private val ink = if (lock != null) INK else INK_OFF
    private val lock = lock?.mutate()?.apply { setTint(ink) }
    private val rect = RectF()

    private fun textSize(paint: Paint) = paint.textSize * .78f
    private fun lockSize(paint: Paint) = if (lock != null) paint.textSize * .8f else 0f
    private fun lockGap() = (if (lock != null) 4 else 2) * density

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val old = paint.textSize
        paint.textSize = textSize(paint)
        val width = paint.measureText(text, start, end)
        paint.textSize = old
        return (8 * density + 7 * density + lockSize(paint) + lockGap() + width + 9 * density).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int,
        y: Int, bottom: Int, paint: Paint) {
        val base = paint.textSize
        val lockPx = lockSize(paint)
        val radius = base * 1.3f / 2f
        val center = (top + bottom) / 2f
        val left = x + 8 * density
        rect.set(left, center - radius, x + getSize(paint, text, start, end, null), center + radius)
        val oldColor = paint.color
        paint.color = fill
        canvas.drawRoundRect(rect, radius, radius, paint)
        val lockLeft = (left + 7 * density).toInt()
        lock?.let {
            it.setBounds(lockLeft, (center - lockPx / 2f).toInt(), (lockLeft + lockPx).toInt(),
                (center + lockPx / 2f).toInt())
            it.draw(canvas)
        }
        paint.color = ink
        paint.textSize = textSize(paint)
        val metrics = paint.fontMetrics
        canvas.drawText(text, start, end, lockLeft + lockPx + lockGap(),
            center - (metrics.ascent + metrics.descent) / 2f, paint)
        paint.textSize = base
        paint.color = oldColor
    }

    companion object {
        const val GREEN = 0xff30d158.toInt()
        const val INK = 0xff06290f.toInt()
        const val RED = 0xffff453a.toInt()
        const val INK_OFF = 0xffffffff.toInt()
        const val PILL_HIDDEN = 0
        const val PILL_UP = 1
        const val PILL_OFF = 2

        @JvmStatic
        fun pillState(vpnUp: Boolean, vpnAppInstalled: Boolean): Int =
            if (vpnUp) PILL_UP else if (vpnAppInstalled) PILL_OFF else PILL_HIDDEN

        @JvmStatic
        fun iconOnlyLock(up: Boolean): Int = if (up) R.drawable.ic_lock else R.drawable.ic_lock_open

        @JvmStatic
        fun pillBackground(up: Boolean, density: Float): StateListDrawable {
            val stateColor = if (up) GREEN else RED
            val focused = GradientDrawable().apply {
                setColor(stateColor)
                cornerRadius = 21 * density
                setStroke(Math.round(3 * density), INK_OFF)
            }
            val rest = GradientDrawable().apply {
                setColor(0)
                cornerRadius = 21 * density
                setStroke(Math.round(2 * density), stateColor)
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), rest)
            }
        }

        @JvmStatic
        fun pillInkColor(up: Boolean, focused: Boolean): Int =
            if (up) (if (focused) INK else GREEN) else (if (focused) INK_OFF else RED)
    }
}
