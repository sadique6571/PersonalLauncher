package com.sadique.launcher

/** Bounded decode sizes for full-screen wallpapers. */
object WallpaperSize {
    const val MAX_PIXELS = 8_400_000L

    @JvmStatic
    fun accepted(width: Int, height: Int): Boolean {
        return width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS
    }

    /** Largest power-of-two sample size that still covers both screen dimensions. */
    @JvmStatic
    fun sampleSize(width: Int, height: Int, screenW: Int, screenH: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2
        return sample
    }

    /** Density pair for reducing the decoded bitmap to the size CENTER_CROP needs. */
    @JvmStatic
    fun coverScale(width: Int, height: Int, screenW: Int, screenH: Int): IntArray? {
        if (screenW.toLong() * height >= screenH.toLong() * width) {
            return if (width > screenW) intArrayOf(width, screenW) else null
        }
        return if (height > screenH) intArrayOf(height, screenH) else null
    }
}
