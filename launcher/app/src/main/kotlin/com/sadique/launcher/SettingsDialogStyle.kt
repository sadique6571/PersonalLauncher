package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

internal object SettingsDialogStyle {
    fun styleDialog(host: Activity, dialog: AlertDialog) {
        if (dialog.window == null) return
        val shell = shellBackground(host)
        dialog.window!!.setBackgroundDrawable(shell)
        styleDialogText(host, dialog.window!!.decorView)
        GlassSettings.applyBlur(dialog)
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            val button = dialog.getButton(which)
            if (button != null) {
                GlassSettings.applyAppFont(host, button)
                button.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
            }
        }
    }

    private fun styleDialogText(host: Activity, view: View) {
        if (view is TextView) {
            val weight = view.typeface?.style ?: Typeface.NORMAL
            GlassSettings.applyAppFont(host, view)
            view.setTypeface(view.typeface, weight)
            view.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)))
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) styleDialogText(host, view.getChildAt(i))
        }
    }

    private fun prefsFor(host: Activity): SharedPreferences {
        return ThemePreferences.get(host)
    }

    fun styleShell(host: Activity, page: View) {
        val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
        page.background = shellBackground(host)
        page.clipToOutline = true
        page.elevation = GlassSettings.dp(host, if (glass) 6f else 0f).toFloat()
    }

    private fun shellBackground(host: Activity): GradientDrawable {
        if (ThemeStyle.harmony(ThemePreferences.get(host))) return HarmonyVisionSurface.sheet(host)
        if (ThemeStyle.id(ThemePreferences.get(host)) == ThemeStyle.GLASS) return GlassOsUi.controls(host)
        val glass = TvGlassStyle.enabled(ThemePreferences.get(host))
        val shell = if (glass) TvGlassStyle.surface(GlassSettings.dp(host, 28f), GlassSettings.dp(host, 1f), false, prefsFor(host))
            else GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xff122338.toInt(), 0xff09182a.toInt()))
        shell.cornerRadius = GlassSettings.dp(host, if (glass) 28f else 24f).toFloat()
        val prefs = ThemePreferences.get(host)
        if (!glass) {
            shell.setStroke(GlassSettings.dp(host, 1f), 0x55415a72)
            shell.alpha = PanelTransparency.alpha(ClassicGlassAlpha.panel(TvGlassStyle.intensity(prefs)), prefs, false)
        }
        return shell
    }
}
