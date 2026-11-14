package com.sadique.launcher

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable

/** Lightweight translucent lens with cached gradients and a reflective rim. */
open class GlassSurface(width: Float, private val edge: Int) : GradientDrawable() {
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backdropMatrix = Matrix()
    private var backdropImage: Bitmap? = null
    private var backdropOpacity = 255
    private val strokeWidth = maxOf(1f, width)
    private val rim = strokePaint()
    private val inner = strokePaint()
    private val outer = RectF()
    private val inset = RectF()

    private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@GlassSurface.strokeWidth
    }

    fun backdropOpacity(alpha: Int) {
        backdropOpacity = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    open fun backdrop(image: Bitmap?, sceneWidth: Int, sceneHeight: Int, x: Int, y: Int) {
        if (image == null) return
        if (backdropImage !== image) {
            backdropImage = image
            backdropPaint.shader = BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        backdropMatrix.setScale(sceneWidth.toFloat() / image.width, sceneHeight.toFloat() / image.height)
        backdropMatrix.postTranslate(-x.toFloat(), -y.toFloat())
        backdropPaint.shader.setLocalMatrix(backdropMatrix)
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        outer.set(bounds)
        outer.inset(strokeWidth / 2f, strokeWidth / 2f)
        inset.set(outer)
        inset.inset(strokeWidth * 1.5f, strokeWidth * 1.5f)
        val bottom = maxOf(bounds.top + 1f, bounds.bottom.toFloat())
        val a = Color.alpha(edge)
        rim.shader = LinearGradient(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bottom,
            intArrayOf((minOf(255, a * 2) shl 24) or 0xffffff, (a / 3 shl 24) or 0xffffff, (a shl 24) or 0xffffff),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        inner.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bottom,
            intArrayOf((a / 2 shl 24) or 0xffffff, 0x00ffffff, (a / 5 shl 24) or 0xffffff),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (backdropImage != null && backdropOpacity > 0) {
            backdropPaint.alpha = backdropOpacity * alpha / 255
            val radius = cornerRadius
            if (shape == OVAL) canvas.drawOval(outer, backdropPaint)
            else canvas.drawRoundRect(outer, radius, radius, backdropPaint)
        }
        super.draw(canvas)
        drawRim(canvas)
    }

    fun drawRim(canvas: Canvas) {
        rim.alpha = alpha
        inner.alpha = alpha
        if (shape == OVAL) {
            canvas.drawOval(outer, rim)
            canvas.drawOval(inset, inner)
        } else {
            val outerRadius = HomeGeometry.insetRadius(cornerRadius, strokeWidth / 2f)
            val innerRadius = HomeGeometry.insetRadius(cornerRadius, strokeWidth * 2f)
            canvas.drawRoundRect(outer, outerRadius, outerRadius, rim)
            canvas.drawRoundRect(inset, innerRadius, innerRadius, inner)
        }
    }
}
