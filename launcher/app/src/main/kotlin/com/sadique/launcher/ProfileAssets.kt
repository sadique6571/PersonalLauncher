package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Screen-bounded local profile image decoding, on the existing image worker. */
object ProfileAssets {
    @JvmStatic
    fun load(ctx: Context, file: File, avatar: Boolean): Bitmap? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val bound = if (avatar) 256 else Math.min(
            MemoryProfile.wallpaperMaxDim(MemoryProfile.effectiveTier(ctx, ThemePreferences.get(ctx))),
            Math.max(ctx.resources.displayMetrics.widthPixels, ctx.resources.displayMetrics.heightPixels)
        )
        options.inSampleSize = 1
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > bound * 2) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        val largest = Math.max(bitmap.width, bitmap.height)
        if (largest > bound) {
            return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, bitmap.width * bound / largest),
                Math.max(1, bitmap.height * bound / largest),
                true
            )
        }
        return bitmap
    }
}
