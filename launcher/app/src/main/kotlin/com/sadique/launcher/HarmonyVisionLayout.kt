package com.sadique.launcher

/** Geometry for the TV dock, watch and app drawer. */
object HarmonyVisionLayout {
    const val DOCK_CAPTION_HEIGHT_DP = 24
    const val DOCK_FOCUS_SCALE = 1.16f
    const val DOCK_FOCUS_LIFT_DP = 10
    const val DOCK_HEADROOM_DP = 22

    @JvmStatic
    fun clockDiameterDp(screenHeightDp: Int, requested: Int): Int {
        val sizes = intArrayOf(186, 216, 242)
        return maxOf(148, minOf(sizes[requested.coerceIn(0, 2)], screenHeightDp - 295))
    }

    @JvmStatic
    fun dockColumns(availableWidthDp: Int): Int = (availableWidthDp / 108).coerceIn(4, 8)

    @JvmStatic
    fun dockPlateDp(screenHeightDp: Int): Int = when {
        screenHeightDp < 450 -> 58
        screenHeightDp < 660 -> 68
        else -> 82
    }

    @JvmStatic
    fun dockTileHeightDp(screenHeightDp: Int): Int = dockPlateDp(screenHeightDp) + DOCK_CAPTION_HEIGHT_DP + 4

    @JvmStatic
    fun dockCellWidthDp(screenHeightDp: Int): Int = dockPlateDp(screenHeightDp) + 34

    @JvmStatic
    fun dockHeightDp(screenHeightDp: Int): Int = DOCK_HEADROOM_DP + dockTileHeightDp(screenHeightDp) + 18

    @JvmStatic
    fun dockSidePadding(viewportWidth: Int, cellWidth: Int, itemCount: Int): Int {
        val visible = minOf(itemCount, viewportWidth / cellWidth)
        return if (visible == 0) 0 else (viewportWidth - visible * cellWidth) / 2
    }

    @JvmStatic
    fun showNativeWeatherWidget(): Boolean = true

    @JvmStatic
    fun showSecondGreeting(): Boolean = false

    @JvmStatic
    fun drawerColumns(availableWidthDp: Int): Int = (availableWidthDp / 152).coerceIn(2, 5)

    @JvmStatic
    fun drawerHeightDp(screenHeightDp: Int): Int = (screenHeightDp - 84).coerceIn(230, 420)
}
