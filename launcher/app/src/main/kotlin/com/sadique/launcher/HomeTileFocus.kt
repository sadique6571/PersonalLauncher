package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Point
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** Tile focus effects: wallpaper glow, glass accent and the focused-app spotlight.
 *  Pure move from PersonalHomeActivity. */
internal class HomeTileFocus(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val dp: (Int) -> Int,
    private val glassOs: () -> Boolean,
    private val perfAllows: (String) -> Boolean,
    private val surfaces: HomeSurfaces,
    private val tileViews: HashMap<String, View>,
    private val sky: () -> HomeWallpaperView?,
    private val appHeading: () -> TextView?,
    private val avatar: () -> ImageView?,
    private val spotlight: () -> TextView?,
) {
    /** Muted label color; matches PersonalHomeActivity's companion MUTED. */
    private val muted = 0xffa4bcd6.toInt()
    /** Fallback tile accent; matches PersonalHomeActivity's companion CYAN. */
    private val cyan = 0xff40d9ff.toInt()

    private var spotlightName = ""
    private var spotlightCategory = ""
    var glassAccent = 0xffe50914.toInt()
        private set

    fun updateTileFocusWallpaper(tile: View, app: PersonalApp, focus: Boolean) {
        if (focus) {
            if (glassOs()) { spotlightName = app.name; spotlightCategory = appCategory(app); updateGlassAccent(app.accent) }
            if (!TvGlassStyle.enabled(prefs)) appHeading()?.text = app.name
            appHeading()?.setTextColor(ThemeStyle.foreground(prefs))
            val position = IntArray(2)
            val origin = IntArray(2)
            tile.getLocationInWindow(position)
            val skyView = sky()
            if (skyView != null) {
                skyView.getLocationInWindow(origin)
                skyView.focusX = position[0] - origin[0] + tile.width / 2f
                skyView.focusY = position[1] - origin[1] + tile.height / 2f
                skyView.focusRadius = tile.width.toFloat()
                skyView.focusAccent = if (prefs.getBoolean("focus_glow", true)) app.accent else 0
                if (!tv) {
                    // Parallax: wallpaper drifts slightly opposite to the focused tile,
                    // vertical only — never sideways. (User 2026-11-01.)
                    val cy = position[1] + tile.height / 2f
                    val size = Point()
                    activity.windowManager.defaultDisplay.getSize(size)
                    skyView.animate().translationX(0f)
                            .translationY(if (perfAllows("parallax")) (size.y / 2f - cy) / size.y * dp(36) else 0f)
                            .setDuration(350).start()
                }
            }
        } else sky()?.focusAccent = 0
        sky()?.invalidate()
    }

    fun updateGlassAccent(color: Int) {
        glassAccent = color
        val photo = avatar()
        if (photo is AppAccentAvatar) photo.accent(color)
        sky()?.invalidate()
        updateSpotlight()
    }

    fun appCategory(app: PersonalApp): String {
        val value = ApplicationInfo.getCategoryTitle(activity, app.info.activityInfo.applicationInfo.category)
        return value?.toString() ?: "App"
    }

    fun updateSpotlight() {
        val spotlight = spotlight() ?: return
        val name = if (spotlightName.isEmpty()) activity.getString(R.string.h_your_apps) else spotlightName
        val caption = "●  " + name + if (spotlightCategory.isEmpty()) "" else "  • " + spotlightCategory
        val text = SpannableString(caption)
        text.setSpan(ForegroundColorSpan(glassAccent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val secondary = caption.indexOf("  • ")
        if (secondary >= 0) text.setSpan(ForegroundColorSpan(muted), secondary, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spotlight.text = text
    }

    /** Re-applies backgrounds so tiles match the current day/night theme without a rebuild. */
    fun refreshTileSurfaces() {
        for (entry in tileViews.entries) {
            val tile = entry.value
            val focused = tile.isFocused
            val tag = tile.tag
            val accent = if (tag is Int) tag else cyan
            if (tile is HarmonyAppTile) tile.appearance(focused)
            else if (tile is GlassIconTile) tile.appearance(accent, focused)
            else { tile.background = surfaces.appSurface(accent, focused); tile.foreground = surfaces.tileBorder(accent, focused) }
        }
        sky()?.invalidate()
    }
}
