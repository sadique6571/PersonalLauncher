package com.sadique.launcher

/** All inputs that can change the adaptive wallpaper clock colour. */
internal class ClockSampleKey(
    @JvmField val photo: Any?,
    @JvmField val x: Int,
    @JvmField val y: Int,
    @JvmField val shade: Int,
    @JvmField val darkInk: Int
) {
    fun matches(photo: Any?, x: Int, y: Int, shade: Int, darkInk: Int): Boolean {
        return this.photo === photo && this.x == x && this.y == y && this.shade == shade && this.darkInk == darkInk
    }
}
