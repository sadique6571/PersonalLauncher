package com.sadique.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Mildly wide at rest, wider on focus. Every child is measured inside the actual current bounds. */
internal class GlassIconTile(
    context: Context,
    name: String,
    artwork: Drawable?,
    accent: Int,
    height: Int,
    labelHeight: Int,
    radius: Int,
    private val moving: Boolean
) : FrameLayout(context) {
    private val panel: View
    private val logo: ImageView
    private val caption: TextView
    private val title: TextView
    private val preferredHeight: Int = height
    private val captionHeight: Int = labelHeight
    private val radius: Int = radius
    private var focused = false
    private var inline = false
    private var inset = 0

    init {
        setClipChildren(false)
        setClipToPadding(false)
        background = null
        foreground = null
        tooltipText = name

        panel = View(context).apply {
            id = R.id.card
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(panel, LayoutParams(-1, height))

        logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(logo, LayoutParams(-1, height))

        val prefs = ThemePreferences.get(context)
        val size = Math.max(12, Math.min(22, prefs.getInt("app_label_size", 16)))
        caption = label(context, name, size).apply {
            isSingleLine = true
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        addView(caption, LayoutParams(-1, labelHeight))

        title = label(context, name, size + 3).apply {
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            setAutoSizeTextTypeUniformWithConfiguration(12, Math.min(25, size + 3), 1, TypedValue.COMPLEX_UNIT_SP)
        }
        addView(title, LayoutParams(-1, height))

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, out: Outline) {
                out.setRoundRect(panel.left, panel.top, panel.right, panel.bottom, radius.toFloat())
            }
        }
        artwork(artwork, accent)
    }

    private fun label(c: Context, name: String, size: Int): TextView {
        return TextView(c).apply {
            text = name
            setTextSize(size.toFloat())
            setTextColor(Color.WHITE)
            typeface = Typeface.create(ThemeStyle.fontFamily(ThemePreferences.get(c)), Typeface.BOLD)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setShadowLayer(dp(3f).toFloat(), 0f, dp(1f).toFloat(), 0xee000000.toInt())
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun dp(n: Float): Int = Math.round(n * resources.displayMetrics.density)

    fun artwork(artwork: Drawable?, accent: Int) {
        logo.setImageDrawable(artwork ?: TvSymbols.icon(context, R.drawable.ic_apps))
        appearance(accent, isFocused || moving)
    }

    fun appearance(accent: Int, selected: Boolean) {
        val next = selected && !moving
        if (focused != next) {
            focused = next
            requestLayout()
        }
        panel.background = GlassAppCard.surface(context, accent, selected || moving, radius)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(GlassDockLayout.wideBodyWidth(Integer.MAX_VALUE, preferredHeight), widthMeasureSpec)
        val height = resolveSize(preferredHeight + captionHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
        val bodyHeight = Math.min(preferredHeight, Math.max(1, height - captionHeight))
        val compactWidth = GlassDockLayout.wideBodyWidth(width, bodyHeight)
        val bodyWidth = if (focused) width else compactWidth
        inset = Math.min(dp(12f), Math.max(dp(8f), radius / 2 + 1))
        inset = Math.min(inset, Math.max(0, (Math.min(bodyWidth, bodyHeight) - 1) / 3))
        inline = focused && bodyWidth >= Math.round(bodyHeight * 1.45f * 1.32f) && bodyWidth >= dp(130f)
        var logoWidth = if (inline) Math.min(bodyHeight - inset * 2, Math.round(bodyHeight * .78f)) else bodyWidth - inset * 2
        val logoHeight = Math.max(1, bodyHeight - inset * 2)
        logoWidth = Math.max(1, logoWidth)
        panel.measure(MeasureSpec.makeMeasureSpec(bodyWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY))
        logo.measure(MeasureSpec.makeMeasureSpec(logoWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(logoHeight, MeasureSpec.EXACTLY))
        title.visibility = if (inline) VISIBLE else GONE
        caption.visibility = if (inline) INVISIBLE else VISIBLE
        title.measure(MeasureSpec.makeMeasureSpec(Math.max(1, bodyWidth - logoWidth - inset * 3), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(logoHeight, MeasureSpec.EXACTLY))
        caption.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, captionHeight - dp(6f)), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = (width - panel.measuredWidth) / 2
        val y = (preferredHeight - panel.measuredHeight) / 2
        panel.layout(x, y, x + panel.measuredWidth, y + panel.measuredHeight)
        val logoX = if (inline) x + inset else x + (panel.measuredWidth - logo.measuredWidth) / 2
        logo.layout(logoX, y + inset, logoX + logo.measuredWidth, y + inset + logo.measuredHeight)
        val textX = logoX + logo.measuredWidth + inset
        title.layout(textX, y + inset, textX + title.measuredWidth, y + inset + title.measuredHeight)
        caption.layout(0, preferredHeight + dp(6f), width, Math.min(height, preferredHeight + captionHeight))
        invalidateOutline()
    }
}
