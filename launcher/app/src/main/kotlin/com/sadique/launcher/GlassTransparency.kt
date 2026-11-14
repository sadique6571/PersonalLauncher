package com.sadique.launcher

/** Both glass layers must let the original wallpaper through; a cached blur is never an opaque replacement. */
object GlassTransparency {
    private fun strength(intensity: Int): Int = Math.max(0, Math.min(100, intensity))

    @JvmStatic
    fun backdropAlpha(finish: Int, intensity: Int): Int {
        val maximum = if (finish == 1) 96 else if (finish == 2) 210 else 150
        return Math.round(maximum * strength(intensity) / 100f)
    }

    @JvmStatic
    fun tintAlpha(finish: Int, intensity: Int): Int {
        val maximum = if (finish == 1) 36 else if (finish == 2) 112 else 72
        return Math.round(maximum * strength(intensity) / 100f)
    }

    @JvmStatic
    fun blurRadius(finish: Int, intensity: Int): Int {
        if (finish == 1 || strength(intensity) == 0) return 0
        return if (finish == 2) {
            if (strength(intensity) < 50) 2 else 4
        } else {
            if (strength(intensity) < 50) 1 else 2
        }
    }
}
