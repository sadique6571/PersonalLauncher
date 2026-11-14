package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** A raised capsule edge from cached gradients, with no shadow bitmap or blur pass. */
internal class CapsuleRelief(density: Float) {
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = RectF()
    private val lip = RectF()
    private val inset = RectF()
    private val stroke: Float
    private var radius = 0f

    init {
        stroke = Math.max(1f, density)
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = stroke
        inner.style = Paint.Style.STROKE
        inner.strokeWidth = stroke
    }

    fun size(width: Int, height: Int) {
        body.set(0f, 0f, width.toFloat(), height.toFloat())
        radius = Math.min(width, height) / 2f
        lip.set(body)
        lip.inset(stroke / 2, stroke / 2)
        inset.set(body)
        inset.inset(stroke * 2, stroke * 2)
        sheen.shader = LinearGradient(
            0f, 0f, 0f, Math.max(1, height).toFloat(),
            intArrayOf(0x26ffffff, 0x00ffffff, 0x19000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
        )
        edge.shader = LinearGradient(
            0f, 0f, Math.max(1, width).toFloat(), Math.max(1, height).toFloat(),
            intArrayOf(0xb0ffffff.toInt(), 0x18ffffff, 0x70000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        inner.shader = LinearGradient(
            0f, 0f, Math.max(1, width).toFloat(), Math.max(1, height).toFloat(),
            0x33000000, 0x66ffffff, Shader.TileMode.CLAMP
        )
    }

    fun draw(canvas: Canvas) {
        canvas.drawRoundRect(body, radius, radius, sheen)
        val outside = HomeGeometry.insetRadius(radius, stroke / 2)
        canvas.drawRoundRect(lip, outside, outside, edge)
        val inside = HomeGeometry.insetRadius(radius, stroke * 2)
        canvas.drawRoundRect(inset, inside, inside, inner)
    }
}
