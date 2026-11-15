package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.IOException

/** Reads and refreshes the last successfully decoded Bing Home wallpaper. */
internal class HomeBingWallpaper(private val context: Context) {
    data class Snapshot(val image: Bitmap, val url: String, val checkedAt: Long)
    data class Refresh(val image: Bitmap?, val url: String, val metadataChecked: Boolean)

    private fun snapshotFile(): File = File(context.filesDir, "home_bing_snapshot.bin")

    fun restore(): Snapshot? {
        val saved = HomeWallpaperCache.read(snapshotFile()) ?: return null
        val image = decodeBingPhoto(saved.image) ?: return null
        return Snapshot(image, saved.url, saved.checkedAt)
    }

    fun refresh(index: Int, previousUrl: String, hasCurrentPhoto: Boolean): Refresh {
        var bmp: Bitmap? = null
        var fetchedUrl = ""
        var checked = false
        try {
            // Retire the old 6-photo disk cache: it is no longer populated.
            val stale = File(context.cacheDir, "screensaver_photos").listFiles()
            if (stale != null) for (f in stale) f.delete()
            // Bing daily wallpaper: API returns JSON, parse to get image URL.
            val apiUrl = LandscapeModel.photoUrl(index)
            val jsonBytes = OnlineContent.get(apiUrl, 64 * 1024)
            val json = String(jsonBytes, Charsets.UTF_8)
            val imageUrl = LandscapeModel.bingImageUrl(json)
            fetchedUrl = imageUrl
            checked = true
            if (imageUrl != previousUrl || !hasCurrentPhoto) {
                val bytes = OnlineContent.get(imageUrl, 6 * 1024 * 1024)
                bmp = decodeBingPhoto(bytes)
                if (bmp != null) {
                    try { HomeWallpaperCache.write(snapshotFile(), imageUrl, System.currentTimeMillis(), bytes) }
                    catch (unavailable: IOException) { Log.w("PersonalWallpaper", "Could not persist the last Home photo", unavailable) }
                }
            }
        } catch (ignored: Exception) {
        }
        return Refresh(bmp, fetchedUrl, checked)
    }

    private fun decodeBingPhoto(bytes: ByteArray): Bitmap? {
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            val metrics = context.resources.displayMetrics
            options.inSampleSize = WallpaperSize.sampleSize(options.outWidth, options.outHeight, metrics.widthPixels, metrics.heightPixels)
            if (!WallpaperSize.accepted(options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize)) return null
            val scale = WallpaperSize.coverScale(options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize, metrics.widthPixels, metrics.heightPixels)
            if (scale != null) { options.inScaled = true; options.inDensity = scale[0]; options.inTargetDensity = scale[1] }
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (unavailable: RuntimeException) { return null }
        catch (unavailable: OutOfMemoryError) { return null }
    }

}
