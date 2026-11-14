package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.widget.SeekBar

/** Native touch, remote and accessibility semantics with a broad thumb-free glass fill. */
internal class GlassSeekBar(context: Context) : SeekBar(context) {
    private val capsule: GlassCapsule? = if (TvGlassStyle.enabled(ThemePreferences.get(context))) GlassCapsule(context) else null

    init {
        if (capsule != null) {
            background = null
            thumb = null
            thumbOffset = 0
            setPadding(0, 0, 0, 0)
            setSplitTrack(false)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        capsule?.size(w, h)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        if (capsule == null) {
            super.onDraw(canvas)
            return
        }
        capsule.draw(
            canvas,
            (progress - min).toFloat() / Math.max(1, max - min).toFloat(),
            false,
            layoutDirection == LAYOUT_DIRECTION_RTL
        )
    }
}
