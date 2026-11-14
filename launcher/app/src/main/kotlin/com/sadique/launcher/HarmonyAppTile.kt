package com.sadique.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Harmony dock tile using the app's native icon and a fixed slot. */
class HarmonyAppTile(context: Context, label: String, artwork: Drawable?, private var accent: Int,
    plateSizeDp: Int, private val dock: Boolean) : FrameLayout(context) {
    private val plate = FrameLayout(context)
    private val dockPlate = if (dock) GradientDrawable() else null
    private val icon = ImageView(context)
    private val name = TextView(context)
    private val plateSize = dp(plateSizeDp.toFloat())
    private var focusedPlate = false
    private var launching = false
    private var selectHeld = false

    init {
        clipChildren = false
        clipToPadding = false
        background = null
        foreground = null
        tooltipText = label
        contentDescription = label
        if (dock) defaultFocusHighlightEnabled = false
        plate.id = R.id.card
        dockPlate?.let {
            it.cornerRadius = dp(12f).toFloat()
            plate.background = it
        }
        plate.clipToOutline = true
        addView(plate, LayoutParams(plateSize, plateSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            if (dock) topMargin = dp(HarmonyVisionLayout.DOCK_HEADROOM_DP.toFloat())
        })
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        icon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val pad = if (dock) Math.round(plateSize * .15f) else dp(9f)
        icon.setPadding(pad, pad, pad, pad)
        plate.addView(icon, LayoutParams(-1, -1))
        name.text = label
        name.textSize = if (dock) 14f else 11f
        name.setTextColor(Color.WHITE)
        name.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        name.setShadowLayer(dp(3f).toFloat(), 0f, dp(1f).toFloat(), 0xbf071a29.toInt())
        name.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val caption = if (dock) HarmonyVisionLayout.DOCK_CAPTION_HEIGHT_DP.toFloat() else 21f
        addView(name, LayoutParams(-1, dp(caption), Gravity.BOTTOM).apply {
            leftMargin = dp(3f)
            rightMargin = dp(3f)
        })
        artwork(artwork, accent)
    }

    fun launchEffect(open: Runnable) {
        if (launching) return
        launching = true
        animate().scaleX(.94f).scaleY(.94f).setDuration(55).withEndAction {
            animate().scaleX(1.045f).scaleY(1.045f).alpha(.76f).setDuration(105).withEndAction {
                resetLaunch()
                open.run()
            }.start()
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dock && isSelect(event.keyCode)) {
            val held = event.action == KeyEvent.ACTION_DOWN && !event.isCanceled
            if (held != selectHeld) {
                selectHeld = held
                updatePlateTransform(if (held) 90 else 140)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isSelect(key: Int): Boolean = when (key) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> true
        else -> false
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        resetLaunch()
        if (dock) {
            selectHeld = false
            updatePlateTransform(0)
        }
        super.onDetachedFromWindow()
    }

    private fun resetLaunch() {
        launching = false
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
    }

    private fun dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    fun artwork(image: Drawable?, accent: Int) {
        this.accent = accent
        icon.setImageDrawable(image ?: TvSymbols.icon(context, R.drawable.ic_apps))
        appearance(isFocused)
    }

    fun appearance(focused: Boolean) {
        plate.elevation = dp(if (focused) 7f else 2f).toFloat()
        val surface = dockPlate
        if (surface == null) {
            plate.background = HarmonyVisionSurface.app(context, accent, focused, dp(19f))
            name.visibility = if (focused) VISIBLE else INVISIBLE
            return
        }
        HarmonyVisionSurface.applyDockPlate(surface, context, focused)
        name.setTextColor(if (focused) Color.WHITE else 0x99ffffff.toInt())
        if (focusedPlate != focused) {
            focusedPlate = focused
            if (!focused) selectHeld = false
            updatePlateTransform(if (focused) 220 else 160)
        }
    }

    private fun updatePlateTransform(duration: Long) {
        var scale = if (focusedPlate) HarmonyVisionLayout.DOCK_FOCUS_SCALE else 1f
        if (selectHeld) scale *= .96f
        val lift = if (focusedPlate) -dp(HarmonyVisionLayout.DOCK_FOCUS_LIFT_DP.toFloat()).toFloat() else 0f
        plate.animate().cancel()
        if (duration == 0L || MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) == MemoryProfile.TIER_LIGHT) {
            plate.scaleX = scale
            plate.scaleY = scale
            plate.translationY = lift
            return
        }
        plate.animate().scaleX(scale).scaleY(scale).translationY(lift)
            .setDuration(duration).setInterpolator(FOCUS_EASE).start()
    }

    private companion object {
        val FOCUS_EASE = PathInterpolator(.4f, 0f, .2f, 1f)
    }
}
