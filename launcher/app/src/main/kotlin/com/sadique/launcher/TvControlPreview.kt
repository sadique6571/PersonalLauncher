package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast

/** Sample controls that leave the real TV settings to Android. */
object TvControlPreview {
    @JvmStatic
    fun show(host: Activity, picture: Boolean): AlertDialog {
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        fun label(text: String, size: Int, color: Int, pad: Float) {
            rows.addView(GlassSettings.label(host, text, size, color).apply {
                setPadding(0, 0, 0, GlassSettings.dp(host, pad))
            })
        }
        label("PREVIEW · TV settings are unchanged", 14, GlassSettings.themeAccent(host), 8f)
        label("Sample values only. Left / Right adjusts a slider; Up / Down selects a row. Real TV controls will be connected after device testing.",
            12, 0xffb7cbdf.toInt(), 12f)
        val sliders = ArrayList<SliderRow>()
        fun add(title: String, min: Int = 0, max: Int = 100, initial: Int = 50) {
            val row = SliderRow(host, title, min, max, initial)
            sliders.add(row)
            rows.addView(row)
        }
        if (picture) {
            for (title in listOf("Backlight", "Brightness", "Contrast", "Colour", "Sharpness")) add(title)
            add("Hue", -50, 50, 0)
        } else {
            add("Bass")
            add("Treble")
            add("Balance", -50, 50, 0)
        }
        val reset = GlassSettings.row(host, "Reset preview values", "Only the samples on this page",
            R.drawable.ic_settings, GlassSettings.themeAccent(host)) { sliders.forEach(SliderRow::reset) }
        rows.addView(reset)
        val dialog = GlassSettings.panel(host, if (picture) "Picture · Preview" else "Sound · Preview", rows)
        val system = GlassSettings.row(host, if (picture) "Open TV picture settings" else "Open TV sound settings",
            "Use the working system controls", if (picture) R.drawable.ic_picture else R.drawable.ic_volume,
            GlassSettings.themeAccent(host)) {
            try {
                val action = if (picture) Settings.ACTION_DISPLAY_SETTINGS else Settings.ACTION_SOUND_SETTINGS
                host.startActivity(SystemSettings.intent(host, action))
                dialog.dismiss()
            } catch (_: RuntimeException) {
                Toast.makeText(host, R.string.not_available, Toast.LENGTH_SHORT).show()
            }
        }
        rows.addView(system)
        for ((index, row) in sliders.withIndex()) {
            sliders.getOrNull(index - 1)?.let { row.nextFocusUpId = it.id }
            row.nextFocusDownId = sliders.getOrNull(index + 1)?.id ?: ensureId(reset)
        }
        reset.nextFocusUpId = sliders.last().id
        reset.nextFocusDownId = ensureId(system)
        system.nextFocusUpId = reset.id
        system.nextFocusDownId = system.id
        dialog.show()
        return dialog
    }

    private fun ensureId(view: View): Int {
        if (view.id == View.NO_ID) view.id = View.generateViewId()
        return view.id
    }

    private class SliderRow(host: Activity, private val title: String, private val minimum: Int,
        private val maximum: Int, private val initial: Int) : LinearLayout(host) {
        private val slider = GlassSeekBar(host)
        private val number = GlassSettings.label(host, "", 15, GlassSettings.themeAccent(host))

        init {
            fun dp(value: Float) = GlassSettings.dp(host, value)
            id = View.generateViewId()
            orientation = VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(12f), dp(8f), dp(12f), dp(4f))
            background = GlassSettings.background(host, false)
            setOnFocusChangeListener { _, focused -> background = GlassSettings.background(host, focused) }
            addView(LinearLayout(host).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(GlassSettings.label(host, title, 15, 0xfff1f6ff.toInt()), LayoutParams(0, -2, 1f))
                addView(number)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            })
            val accent = ColorStateList.valueOf(GlassSettings.themeAccent(host))
            slider.max = maximum - minimum
            slider.keyProgressIncrement = 1
            slider.progressTintList = accent
            slider.progressBackgroundTintList = ColorStateList.valueOf(0xff344b61.toInt())
            slider.thumbTintList = accent
            slider.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { display() }
                override fun onStartTrackingTouch(bar: SeekBar) { requestFocus() }
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
            val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
            val margin = dp(if (glass) 6f else 0f)
            addView(slider, LayoutParams(-1, dp(if (glass) 36f else 32f)).apply {
                topMargin = margin
                bottomMargin = margin
            })
            layoutParams = LayoutParams(-1, -2).apply { setMargins(0, dp(3f), 0, dp(3f)) }
            reset()
        }

        private fun value(): Int = slider.progress + minimum

        private fun display() {
            val value = value()
            number.text = if (value > 0 && minimum < 0) "+$value" else value.toString()
            contentDescription = "$title, preview value $value, TV unchanged"
        }

        fun reset() {
            slider.progress = initial - minimum
            display()
        }

        private fun adjust(step: Int) {
            slider.progress = (slider.progress + step).coerceIn(0, maximum - minimum)
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjust(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || super.onKeyUp(keyCode, event)

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = SeekBar::class.java.name
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                minimum.toFloat(), maximum.toFloat(), value().toFloat())
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }

        override fun performAccessibilityAction(action: Int, args: Bundle?): Boolean {
            when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                    adjust(if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) 1 else -1)
                    return true
                }
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> if (args != null) {
                    val requested = args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value().toFloat())
                    if (!requested.isFinite()) return false
                    slider.progress = (Math.round(requested) - minimum).coerceIn(0, maximum - minimum)
                    return true
                }
            }
            return super.performAccessibilityAction(action, args)
        }
    }
}
