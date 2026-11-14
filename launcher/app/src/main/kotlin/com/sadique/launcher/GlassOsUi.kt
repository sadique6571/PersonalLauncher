package com.sadique.launcher

import android.app.Activity
import android.graphics.drawable.GradientDrawable

object GlassOsUi {

    @JvmStatic
    fun controls(host: Activity): GradientDrawable {
        if (ThemeStyle.harmony(ThemePreferences.get(host))) return HarmonyVisionSurface.sheet(host)
        val d = panel(host, 26)
        val prefs = ThemePreferences.get(host)
        val alpha = PanelTransparency.alpha(Math.min(250, Math.round(235 * alpha(prefs) / 168f)), prefs, false)
        d.colors = if (ThemeStyle.harmony(prefs)) {
            intArrayOf((alpha shl 24) or 0x203f62, (alpha shl 24) or 0x112c4a)
        } else {
            intArrayOf((alpha shl 24) or 0x0a0e18, (alpha shl 24) or 0x0c111e)
        }
        return d
    }

    @JvmStatic
    fun panel(host: Activity, radius: Int): GradientDrawable {
        val prefs = ThemePreferences.get(host)
        if (ThemeStyle.harmony(prefs)) {
            val material = HarmonyVisionSurface.control(host, false)
            material.cornerRadius = GlassSettings.dp(host, radius.toFloat()).toFloat()
            return material
        }
        val alpha = PanelTransparency.alpha(alpha(prefs), prefs, false)
        val d = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (ThemeStyle.harmony(prefs)) {
                intArrayOf((alpha shl 24) or 0x305c7b, (Math.min(255, alpha + 16) shl 24) or 0x10263e)
            } else {
                intArrayOf((alpha shl 24) or 0x132539, (Math.min(255, alpha + 25) shl 24) or 0x09182a)
            }
        )
        d.cornerRadius = GlassSettings.dp(host, radius.toFloat()).toFloat()
        val edge = Math.round((TvGlassStyle.edge(prefs) ushr 24) * 0.47f)
        d.setStroke(GlassSettings.dp(host, 1f), (edge shl 24) or 0xffffff)
        return d
    }

    private fun alpha(prefs: android.content.SharedPreferences): Int {
        val finish = if (TvGlassStyle.finish(prefs) == 1) 0.65f else if (TvGlassStyle.finish(prefs) == 2) 1.2f else 1f
        return Math.min(245, Math.round(194 * TvGlassStyle.intensity(prefs) / 70f * finish))
    }
}
