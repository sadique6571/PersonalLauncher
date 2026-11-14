package com.sadique.launcher

/** Geometry shared by the tile shell, clipped artwork and focus rim. Values are dp. */
object HomeGeometry {

    @JvmStatic
    fun tileRadius(choice: Int): Int {
        return intArrayOf(4, 16, 28)[Math.max(0, Math.min(2, choice))]
    }

    @JvmStatic
    fun insetRadius(radius: Float, inset: Float): Float {
        return Math.max(0f, radius - inset)
    }

    @JvmStatic
    fun clockSize(requested: Int, compact: Boolean): Int {
        return if (compact) Math.round(requested * 0.72f) else requested
    }

    @JvmStatic
    fun avatarRadius(size: Int): Float {
        return Math.max(0, size) / 4f
    }
}
