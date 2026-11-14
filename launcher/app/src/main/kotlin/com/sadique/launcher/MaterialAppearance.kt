package com.sadique.launcher

/** Testable material controls shared by Dune and Pearl. Finish and intensity affect both glass layers. */
object MaterialAppearance {

    @JvmStatic
    fun alpha(finish: Int, intensity: Int, drawer: Boolean, focused: Boolean): Int {
        val value = Math.max(0, Math.min(100, intensity))
        if (drawer) return Math.min(255, GlassTransparency.tintAlpha(finish, value) + (if (focused) Math.round(30 * value / 100f) else 0))
        val max = if (finish == 1) 190 else if (finish == 2) 255 else 230
        return Math.min(255, Math.round(max * value / 100f) + (if (focused) Math.round(12 * value / 100f) else 0))
    }

    @JvmStatic
    fun tint(base: Int, accent: Int, focused: Boolean): Int {
        return mix(base, accent, if (focused) 0.22f else 0.12f)
    }

    @JvmStatic
    fun mix(a: Int, b: Int, fraction: Float): Int {
        val red = Math.round(((a shr 16) and 255) * (1 - fraction) + ((b shr 16) and 255) * fraction)
        val green = Math.round(((a shr 8) and 255) * (1 - fraction) + ((b shr 8) and 255) * fraction)
        val blue = Math.round((a and 255) * (1 - fraction) + (b and 255) * fraction)
        return 0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
    }
}
