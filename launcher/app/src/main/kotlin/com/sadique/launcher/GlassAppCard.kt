package com.sadique.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** App-tinted reference card. App logos are separate, so resizing never stretches a bitmap plate. */
internal object GlassAppCard {
    @JvmStatic
    fun surface(context: Context, accent: Int, focused: Boolean, radius: Int): GradientDrawable {
        val prefs = ThemePreferences.get(context)
        if (ThemeStyle.harmony(prefs)) return HarmonyVisionSurface.app(context, accent, focused, radius)
        val top = MaterialAppearance.mix(0xff132539.toInt(), accent, .24f)
        val bottom = MaterialAppearance.mix(0xff040916.toInt(), accent, .14f)
        val alpha = Math.min(250, 180 + Math.round(TvGlassStyle.intensity(prefs) * .65f))
        val d = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf((alpha shl 24) or (top and 0xffffff), (alpha shl 24) or (bottom and 0xffffff))
        )
        d.cornerRadius = radius.toFloat()
        val stroke = Math.max(1, Math.round(context.resources.displayMetrics.density * if (focused) 2.5f else 1f))
        d.setStroke(stroke, if (focused) 0xffffffff.toInt() else TvGlassStyle.edge(prefs))
        return d
    }
}
