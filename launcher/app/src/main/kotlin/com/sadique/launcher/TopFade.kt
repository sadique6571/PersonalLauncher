package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * fade_rows: the rows fade out as they scroll up under the clock.
 */
internal class TopFade private constructor(private val length: Float) : RecyclerView.ItemDecoration() {
    private val paint = Paint()
    private var shaderHeight = -1f

    init {
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val fade = height(parent.computeVerticalScrollOffset(), length)
        if (fade <= 0f) return
        if (fade != shaderHeight) {
            paint.shader = LinearGradient(0f, 0f, 0f, fade, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            shaderHeight = fade
        }
        c.drawRect(0f, 0f, parent.width.toFloat(), fade, paint)
    }

    companion object {
        @JvmStatic
        fun attach(grid: RecyclerView, length: Float) {
            grid.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            grid.addItemDecoration(TopFade(length))
        }

        @JvmStatic
        fun height(scrolled: Int, length: Float): Float {
            return Math.max(0f, Math.min(length, scrolled.toFloat()))
        }
    }
}
