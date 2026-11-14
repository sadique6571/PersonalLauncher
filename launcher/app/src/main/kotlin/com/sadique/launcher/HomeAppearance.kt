package com.sadique.launcher

/** Wallpaper framing and contrast rules shared by Home and its settings cards. */
object HomeAppearance {
    @JvmStatic
    fun daylight(mode: String?, minute: Int): Float = when (mode) {
        "light" -> 1f
        "auto" -> PersonalHomeModel.daylight(minute)
        else -> 0f
    }

    @JvmStatic
    fun shade(mode: String?, minute: Int, extra: Int): Int {
        return (Math.round(24 * (1 - daylight(mode, minute))) + maxOf(0, extra)).coerceIn(0, 150)
    }

    @JvmStatic
    fun fitFrame(width: Int, height: Int, imageWidth: Int, imageHeight: Int): FloatArray {
        val scale = minOf(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val photoWidth = imageWidth * scale
        val photoHeight = imageHeight * scale
        val left = (width - photoWidth) / 2f
        val top = (height - photoHeight) / 2f
        return floatArrayOf(left, top, left + photoWidth, top + photoHeight)
    }

    @JvmStatic
    fun frame(width: Int, height: Int, imageWidth: Int, imageHeight: Int, clockY: Float, mountains: Boolean): FloatArray {
        val areaHeight = if (mountains && height > width) {
            minOf(height.toFloat(), maxOf(width * .75f, clockY * 1.8f))
        } else {
            height.toFloat()
        }
        val scale = maxOf(width.toFloat() / imageWidth, areaHeight / imageHeight)
        val photoWidth = imageWidth * scale
        val photoHeight = imageHeight * scale
        val left = (width - photoWidth) / 2f
        val top = if (mountains) 0f else (height - photoHeight) / 2f
        return floatArrayOf(left, top, left + photoWidth, top + photoHeight)
    }
}
