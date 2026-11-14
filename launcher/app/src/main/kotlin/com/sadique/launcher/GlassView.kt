package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Draws the hardware-cached glass highlight that moves between focused tiles. */
open class GlassView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specular = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private var corner = 20f * density
    private var glowRgb = 0xffffff
    private var glowAlpha = 0x30
    private var flat = false
    private var halo = false

    init {
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = 1.5f * density
        rim.color = 0xccffffff.toInt()
        glow.style = Paint.Style.STROKE
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAccent(argb: Int) {
        if (argb == 0) {
            glowRgb = 0xffffff
            glowAlpha = 0x30
            rim.color = 0xccffffff.toInt()
        } else {
            glowRgb = argb and 0xffffff
            glowAlpha = 0x70
            fun lift(shift: Int) = (((glowRgb ushr shift) and 255) + 510) / 3
            rim.color = 0xe6000000.toInt() or (lift(16) shl 16) or (lift(8) shl 8) or lift(0)
        }
        invalidate()
    }

    fun setStyle(style: String?) {
        flat = style == Config.GLASS_RIM || style == Config.GLASS_HALO
        halo = style == Config.GLASS_HALO
        rim.strokeWidth = (if (flat) 2f else 1.5f) * density
        invalidate()
    }

    fun setRadius(px: Float) {
        corner = px
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x3dffffff, 0x12ffffff, Shader.TileMode.CLAMP)
        specular.shader = LinearGradient(0f, 0f, 0f, h * .45f, 0x66ffffff, 0x00ffffff, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = 6f * density
        rect.set(inset, inset, width - inset, height - inset)
        if (!flat || halo) {
            val rings = 12
            val step = inset / rings
            val alpha = if (flat) glowAlpha / 2 else glowAlpha
            glow.strokeWidth = step + .5f
            repeat(rings) { index ->
                val offset = step * (index + .5f)
                val strength = 1f - index.toFloat() / rings
                glow.color = (Math.round(alpha * strength) shl 24) or glowRgb
                canvas.drawRoundRect(rect.left - offset, rect.top - offset, rect.right + offset,
                    rect.bottom + offset, corner + offset, corner + offset, glow)
            }
        }
        if (!flat) {
            canvas.drawRoundRect(rect, corner, corner, fill)
            canvas.save()
            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * .45f)
            canvas.drawRoundRect(rect, corner, corner, specular)
            canvas.restore()
        }
        canvas.drawRoundRect(rect, corner, corner, rim)
    }
}
