package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.widget.LinearLayout

/** App-grid and dock sizing for Home. Pure move from PersonalHomeActivity. */
internal class HomeTileGeometry(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val dp: (Int) -> Int,
    private val pageWidth: () -> Int,
    private val appBody: () -> LinearLayout?,
    private val columnCount: () -> Int,
) {
    /**
     * Width the app grid can actually use: the page content width minus the
     * appBody panel's side padding. Sizing tiles from pageWidth alone overflowed
     * the panel and clipped the rightmost column (portrait and landscape).
     */
    fun gridWidth(): Int {
        val body = appBody()
        val pad = if (body == null) dp(16) else body.paddingLeft + body.paddingRight
        return Math.max(dp(120), pageWidth() - pad)
    }

    fun referenceCardHeight(cellWidth: Int): Int = referenceCardHeight(cellWidth, columnCount())
    fun referenceCardHeight(cellWidth: Int, columns: Int): Int {
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val margin = (if (TvGlassStyle.enabled(prefs)) intArrayOf(dp(6), dp(10), dp(16)) else intArrayOf(dp(4), dp(8), dp(14)))[spacing]
        val widthDp = Math.round((GlassDockLayout.compactCellWidth(cellWidth * columns, columns) - margin * 2) / activity.resources.displayMetrics.density)
        var height = GlassDockLayout.iconTileHeight(widthDp, activity.resources.configuration.screenHeightDp, referenceCaptionHeight())
        if (ThemeStyle.harmony(prefs)) height = HarmonyVisionLayout.dockTileHeightDp(activity.resources.configuration.screenHeightDp)
        return dp(height)
    }
    fun referenceCaptionHeight(): Int = GlassDockLayout.captionHeight(prefs.getInt("app_label_size", 16), activity.resources.configuration.fontScale)
    fun referenceDockHeight(): Int {
        if (ThemeStyle.harmony(prefs)) return dp(HarmonyVisionLayout.dockHeightDp(activity.resources.configuration.screenHeightDp))
        val available = if (pageWidth() > 0) gridWidth() else dp(Math.max(160, activity.resources.configuration.screenWidthDp - if (tv) 64 else 32))
        val cols = if (ThemeStyle.harmony(prefs)) HarmonyVisionLayout.dockColumns(Math.round(available / activity.resources.displayMetrics.density)) else PersonalHomeModel.columns(Math.round(available / activity.resources.displayMetrics.density), prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0), prefs.getBoolean("large_tiles", false))
        val card = referenceCardHeight(available / cols, cols)
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val margin = intArrayOf(6, 8, 12)[spacing]
        return card + dp(GlassDockLayout.reflectionHeight(Math.round(card / activity.resources.displayMetrics.density)) + margin * 2 + 44)
    }

    fun placeDemoCards(grid: HomeAppGrid<PersonalApp>?, destroyed: Boolean) {
        if (destroyed || grid == null || !ThemeStyle.custom(prefs) || !tv || grid.childCount == 0) return
        val row = grid.getChildAt(0).height
        val top = Math.max(dp(10), Math.min(grid.height / 4, grid.height - row * 2 - dp(14)))
        if (grid.paddingTop != top) grid.setPadding(0, top, 0, dp(14))
    }
}
