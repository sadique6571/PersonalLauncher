package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.widget.FrameLayout

/** Clip only artwork/labels; leave the card's own folded edge and focus marker visible. */
internal class DemoTile(context: Context, private val style: Int, private val radius: Float) : FrameLayout(context) {
    private val clip = Path()
    private val footer: Float = DemoGeometry.footer(style, Math.max(1, Math.round(resources.displayMetrics.density)).toFloat())

    init {
        clipToOutline = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        DemoPaths.set(clip, style, w.toFloat(), Math.max(1f, h - footer), radius)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
