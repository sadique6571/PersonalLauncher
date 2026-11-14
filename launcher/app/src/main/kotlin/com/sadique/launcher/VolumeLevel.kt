package com.sadique.launcher

/** Display media volume while leaving its saved level intact when muted. */
object VolumeLevel {
    @JvmStatic
    fun fraction(volume: Int, maximum: Int, muted: Boolean): Float {
        if (muted || maximum <= 0) return 0f
        return (volume.toFloat() / maximum).coerceIn(0f, 1f)
    }

    @JvmStatic
    fun percent(volume: Int, maximum: Int, muted: Boolean): Int {
        return Math.round(fraction(volume, maximum, muted) * 100)
    }
}
