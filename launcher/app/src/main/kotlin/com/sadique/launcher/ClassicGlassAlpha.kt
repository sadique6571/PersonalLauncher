package com.sadique.launcher

/** Keep both Classic layers translucent; transparent cards over an opaque shell are not glass. */
object ClassicGlassAlpha {
    private fun clamp(value: Int): Int = Math.max(0, Math.min(100, value))

    @JvmStatic
    fun card(intensity: Int, focused: Boolean): Int {
        return Math.round(150 * clamp(intensity) / 100f) + if (focused) 48 else 0
    }

    @JvmStatic
    fun panel(intensity: Int): Int {
        return clamp(intensity)
    }

    @JvmStatic
    fun drawer(intensity: Int): Int {
        return Math.round(70 * clamp(intensity) / 100f)
    }
}
