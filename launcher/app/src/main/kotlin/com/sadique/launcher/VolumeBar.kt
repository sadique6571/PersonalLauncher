package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/** Bottom-filled volume track with a readable output symbol. */
class VolumeBar(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val track = RectF()
    private val capsule = if (TvGlassStyle.enabled(ThemePreferences.get(context))) GlassCapsule(context, true) else null
    private var fraction = 0f
    private var accent = 0
    private var symbolResource = 0
    private var symbolTint = 0
    private var symbol: Drawable? = null

    init {
        isFocusable = false
    }

    fun level(fraction: Float, color: Int) {
        if (this.fraction == fraction && accent == color) return
        this.fraction = fraction
        accent = color
        invalidate()
    }

    fun symbol(resource: Int) {
        if (symbolResource == resource) return
        symbolResource = resource
        symbolTint = 0
        symbol = TvSymbols.icon(context, resource)
        positionSymbol()
        invalidate()
    }

    private fun positionSymbol() {
        val icon = symbol ?: return
        val density = resources.displayMetrics.density
        val size = Math.round(27 * density)
        val bottom = height - Math.round(18 * density)
        val left = (width - size) / 2
        icon.setBounds(left, bottom - size, left + size, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        capsule?.size(w, h)
        positionSymbol()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (capsule != null) {
            capsule.draw(canvas, fraction, true, false)
            val icon = symbol ?: return
            val boundary = height * (1 - fraction)
            // Draw both colors through disjoint clips so the fill can cross the glyph.
            var save = canvas.save()
            canvas.clipRect(0f, 0f, width.toFloat(), boundary)
            tint(icon, capsule.textColor())
            icon.draw(canvas)
            canvas.restoreToCount(save)
            save = canvas.save()
            canvas.clipRect(0f, boundary, width.toFloat(), height.toFloat())
            val filled = if (ThemeStyle.custom(ThemePreferences.get(context))) capsule.filledTextColor() else 0xff159fe8.toInt()
            tint(icon, filled)
            icon.draw(canvas)
            canvas.restoreToCount(save)
            return
        }
        track.set(1f, 1f, width - 1f, height - 1f)
        val radius = width / 2f
        val opacity = PanelTransparency.alpha(181, ThemePreferences.get(context), false)
        paint.color = (opacity shl 24) or 0x1b2a3c
        canvas.drawRoundRect(track, radius, radius, paint)
        if (fraction <= 0) return
        clip.reset()
        clip.addRoundRect(track, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        paint.color = accent
        canvas.drawRect(track.left, track.bottom - track.height() * fraction, track.right, track.bottom, paint)
        canvas.restoreToCount(save)
    }

    private fun tint(icon: Drawable, color: Int) {
        if (symbolTint == color) return
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        symbolTint = color
    }
}
