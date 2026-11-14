package com.sadique.launcher

/** Small, deterministic layout policy. Indices are Wi-Fi, Bluetooth, inputs, display, sound, screensaver. */
object ControlCenterLayout {
    @JvmStatic
    fun rows(style: Int): Array<IntArray> {
        return when (style) {
            1 -> arrayOf(intArrayOf(0), intArrayOf(1), intArrayOf(2), intArrayOf(3), intArrayOf(4), intArrayOf(5))
            2 -> arrayOf(intArrayOf(3), intArrayOf(0, 1), intArrayOf(2, 4, 5))
            3 -> arrayOf(intArrayOf(0, 1), intArrayOf(3), intArrayOf(2, 4, 5))
            4 -> arrayOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5))
            5 -> arrayOf(intArrayOf(0, 1), intArrayOf(2, 3, 4), intArrayOf(5))
            6 -> arrayOf(intArrayOf(5), intArrayOf(0, 1, 2), intArrayOf(3, 4))
            7 -> arrayOf(intArrayOf(0, 1, 2), intArrayOf(4), intArrayOf(3, 5))
            8 -> arrayOf(intArrayOf(0, 1), intArrayOf(2, 4), intArrayOf(3, 5))
            else -> arrayOf(intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5))
        }
    }

    @JvmStatic
    fun stacked(style: Int): Boolean = style == 0 || style == 3 || style == 5

    @JvmStatic
    fun radius(style: Int): Int = intArrayOf(22, 10, 6, 24, 8, 14, 12, 6, 28)[Math.max(0, Math.min(8, style))]

    /** Align vertical D-pad movement by the centres of equal-width cards. */
    @JvmStatic
    fun neighbour(column: Int, count: Int, targetCount: Int): Int {
        return Math.min(targetCount - 1, (2 * column + 1) * targetCount / (2 * count))
    }
}
