package com.sadique.launcher

import android.content.SharedPreferences
import android.graphics.Color
import android.widget.TextView

/** Fixed label style for all wallpaper-backed text (user 2026-11-01). Pure move from PersonalHomeActivity. */
internal class HomeLabelScheme(
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val dp: (Int) -> Int,
    private val glassOs: () -> Boolean,
    private val clockContrast: HomeClockContrast,
    private val sky: () -> HomeWallpaperView?,
    private val liquidBackdrop: () -> LiquidBackdrop?,
    private val headerBackdrop: () -> LiquidBackdrop?,
    private val userName: () -> TextView?,
    private val greeting: () -> TextView?,
    private val homeWeather: () -> TextView?,
    private val networkStatus: () -> TextView?,
    private val time: () -> TextView?,
    private val ampm: () -> TextView?,
    private val date: () -> TextView?,
    private val islamicDate: () -> TextView?,
    private val appHeading: () -> TextView?,
) {
    /** Fixed label style for all wallpaper-backed text (user 2026-11-01). */
    fun adaptVisible() {
        // Fixed style for every label (user 2026-11-01): white + black outline + drop shadow.
        applyLabelScheme(userName(), false, false)
        applyLabelScheme(greeting(), false, true)
        applyLabelScheme(homeWeather(), false, true)
        applyLabelScheme(networkStatus(), false, true)
        applyLabelScheme(time(), false, false)
        applyLabelScheme(ampm(), false, false)
        applyLabelScheme(date(), false, true)
        applyLabelScheme(islamicDate(), false, true)
        applyLabelScheme(appHeading(), false, false)
    }

    /** Fixed label style (2026-11-01): white text with a black outline halo and drop shadow,
     *  readable on any wallpaper. Replaces the old per-label adaptive colors. */
    fun applyLabelScheme(label: TextView?, dark: Boolean, muted: Boolean) {
        if (label == null) return
        if (glassOs() && label === networkStatus()) return
        if (glassOs() && label === ampm()) { label.setTextColor(Color.WHITE); return }
        label.setTextColor(if (ThemeStyle.custom(prefs)) (if (muted) ThemeStyle.muted(prefs) else ThemeStyle.foreground(prefs)) else (if (muted) 0xE6FFFFFF.toInt() else 0xFFFFFFFF.toInt()))
        val clockLabel = label === time() || label === ampm() || label === date() || label === islamicDate()
        val headerLabel = label === userName() || label === greeting() || label === homeWeather() || label === networkStatus()
        if (headerLabel) label.setTextColor(HeaderAppearance.foreground(prefs))
        if (glassOs() && label === networkStatus()) return
        if (clockLabel) label.setTextColor(clockContrast.ink)
        if (clockLabel && !prefs.getBoolean("clock_shadow", true)) label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        else {
            val darkInk = if (clockLabel) clockContrast.ink != 0xffffffff.toInt() else headerLabel && ThemeStyle.light(prefs) && HeaderAppearance.mode(prefs) != 0
            label.setShadowLayer(dp(if (clockLabel) 5 else 2).toFloat(), 0f, dp(if (clockLabel) 2 else 1).toFloat(), if (darkInk) 0x99ffffff.toInt() else 0xe6000000.toInt())
        }
    }

    fun adaptTextColors() {
        liquidBackdrop()?.refresh()
        headerBackdrop()?.refresh()
        clockContrast.update(sky(), prefs, tv, dp(150).toFloat())
        adaptVisible()
    }
}
