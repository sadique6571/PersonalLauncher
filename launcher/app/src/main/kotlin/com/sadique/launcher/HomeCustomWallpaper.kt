package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import java.io.File
import java.util.concurrent.ExecutorService

/** Custom + online wallpaper loading for Home. Pure move from PersonalHomeActivity. */
internal class HomeCustomWallpaper(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val worker: ExecutorService,
    private val handler: Handler,
    private val profileEpoch: () -> Int,
    private val destroyed: () -> Boolean,
    private val profileAsset: (String) -> File,
    private val setCustomWallpaper: (Bitmap?) -> Unit,
    private val sky: () -> HomeWallpaperView?,
    private val adaptTextColors: () -> Unit,
) {
    fun loadWallpaper() {
        if ("custom" != prefs.getString("wallpaper_mode", "mountains")) return
        val epoch = profileEpoch()
        val file = profileAsset("personal-wallpaper.jpg")
        worker.execute {
            val image = ProfileAssets.load(activity, file, false)
            handler.post { if (!destroyed() && epoch == profileEpoch()) { setCustomWallpaper(image); sky()?.invalidate(); adaptTextColors() } }
        }
    }

    /** Loads today's Bing wallpaper when the online source is selected. */
    fun updateOnlineWallpaper() {
        val skyView = sky()
        if (skyView == null || prefs.getInt("wallpaper_source", 0) != 1) return
        skyView.loadOnlinePhoto(0)
    }

    fun hasFavoriteWallpaper(): Boolean {
        return profileAsset("favorite_wallpaper.jpg").isFile
    }
}
