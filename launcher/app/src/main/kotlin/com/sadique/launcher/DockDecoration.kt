package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Draws a band under the dock and reserves space around its tiles. */
open class DockDecoration(
    private val dockSize: Int, private val density: Float, translucent: Boolean, names: Boolean,
    opacity: Int, private val lightGlass: Boolean, private val evenPadding: Boolean,
    private val bandVisible: Boolean, private val singleRow: Boolean, bandAll: Boolean
) : RecyclerView.ItemDecoration() {
    private val bandAll = singleRow && bandAll
    private val namesRoom = if (names) 22 * density else 0f
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (translucent) 0x8c121014.toInt() else (Math.round(opacity * 2.55f) shl 24) or 0x141416
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = if (lightGlass) 0x38ffffff else if (translucent) 0x26ffffff else 0x1fffffff
    }
    private val rect = RectF()
    private var shaderTop = Float.NaN
    private var shaderBottom = Float.NaN

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position >= 0 && (singleRow || position < dockSize)) {
            outRect.top = (6 * density).toInt()
            outRect.bottom = (12 * density).toInt()
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!bandVisible) return
        var any = false
        var cardWidth = 0
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position < 0 || (!bandAll && position >= dockSize)) continue
            val holder = parent.getChildViewHolder(child)
            val card = (if (holder is AppAdapter.Holder) holder.card else child.findViewById<View>(R.id.card)) ?: continue
            val left = (child.left + card.left).toFloat()
            val top = child.top + card.top + child.translationY
            val right = left + card.width
            val bottom = top + card.height
            cardWidth = card.width
            if (any) rect.union(left, top, right, bottom)
            else rect.set(left, top, right, bottom)
            any = true
        }
        if (!any) return
        val radius = pad(parent, cardWidth)
        rect.bottom += namesRoom
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            band.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0x24ffffff, 0x0dffffff, Shader.TileMode.CLAMP)
            shaderTop = rect.top
            shaderBottom = rect.bottom
        }
        canvas.drawRoundRect(rect, radius, radius, band)
        canvas.drawRoundRect(rect, radius, radius, edge)
    }

    private fun pad(parent: RecyclerView, cardWidth: Int): Float {
        val sidePad = 22 * density
        val sideRoom = minOf(rect.left, parent.width - rect.right)
        val radius = if (evenPadding) {
            val inner = parent.width - parent.paddingLeft - parent.paddingRight
            val room = (if (singleRow) rect.top else minOf(rect.top, sideRoom)) - density
            val even = evenPaddingPx(inner, dockSize, cardWidth, room)
            rect.inset(-even, -even)
            radiusPx(AppAdapter.cornerRadius(cardWidth, density), even)
        } else {
            val side = minOf(sidePad, sideRoom - density).coerceAtLeast(0f)
            rect.inset(if (singleRow) -sidePad else -side, -14 * density)
            26 * density
        }
        if (singleRow) {
            rect.left = maxOf(rect.left, density)
            rect.right = minOf(rect.right, parent.width - density)
        }
        return radius
    }

    class Reflections : RecyclerView.ItemDecoration() {
        private val mask = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blend = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        private val area = RectF()
        private var fade: Shader? = null
        private var lastHeight = -1f

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val density = parent.resources.displayMetrics.density
            val harmony = ThemeStyle.harmony(ThemePreferences.get(parent.context))
            val columns = (parent as? HomeAppGrid<*>)?.columnCount ?: 4
            val viewport = canvas.save()
            if (parent.clipToPadding) canvas.clipRect(parent.paddingLeft, parent.paddingTop,
                parent.width - parent.paddingRight, parent.height - parent.paddingBottom)
            else canvas.clipRect(0, 0, parent.width, parent.height)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i) as? ViewGroup ?: continue
                if (child.tag == null || child.childCount == 0) continue
                if (harmony && parent.getChildAdapterPosition(child) >= columns) continue
                val card = child.getChildAt(0)
                if (card.width <= 0 || card.height <= 0) continue
                drawCard(canvas, parent, child, card, density, harmony)
            }
            canvas.restoreToCount(viewport)
        }

        private fun drawCard(canvas: Canvas, parent: RecyclerView, child: View, card: View, density: Float, harmony: Boolean) {
            val sx = card.scaleX
            val sy = card.scaleY
            val left = child.left + child.translationX + card.left + card.translationX + card.pivotX * (1 - sx)
            val top = child.top + child.translationY + card.top + card.translationY + card.pivotY * (1 - sy)
            val height = GlassDockLayout.reflectionHeight(Math.round(card.height / density)) * density * sy
            val start = top + card.height * sy + 6 * density
            if (start >= parent.height || start + height <= 0) return
            area.set(left, start, left + card.width * sx, start + height)
            val layer = canvas.saveLayer(area, null)
            val upright = canvas.save()
            canvas.translate(left, start)
            canvas.scale(sx, (if (harmony) -height else height) / card.height)
            if (harmony) canvas.translate(0f, -card.height.toFloat())
            card.draw(canvas)
            canvas.restoreToCount(upright)
            if (height != lastHeight) {
                lastHeight = height
                fade = LinearGradient(0f, 0f, 0f, height, intArrayOf(0xbf000000.toInt(), 0x40000000, 0),
                    floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            }
            canvas.translate(0f, start)
            mask.shader = fade
            mask.alpha = Math.round((if (harmony) 160 else 255) * card.alpha * child.alpha)
            mask.xfermode = blend
            canvas.drawRect(area.left, 0f, area.right, height, mask)
            mask.xfermode = null
            mask.shader = null
            canvas.restoreToCount(layer)
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (view.tag == null || view !is ViewGroup || view.childCount == 0) return
            val density = parent.resources.displayMetrics.density
            val height = Math.round(view.getChildAt(0).layoutParams.height / density)
            outRect.bottom = Math.round((GlassDockLayout.reflectionHeight(height) + 10) * density)
        }
    }

    companion object {
        @JvmStatic fun radiusPx(tileRadiusPx: Float, paddingPx: Float): Float = tileRadiusPx + paddingPx

        @JvmStatic
        fun evenPaddingPx(innerWidthPx: Int, columns: Int, tileWidthPx: Int, roomPx: Float): Float {
            val gap = if (columns > 0) innerWidthPx.toFloat() / columns - tileWidthPx else 0f
            return maxOf(0f, minOf(gap, roomPx))
        }
    }
}
