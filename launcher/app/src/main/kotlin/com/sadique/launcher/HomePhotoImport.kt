package com.sadique.launcher

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.IOException
import android.util.AtomicFile
import android.graphics.BitmapFactory

internal object HomePhotoImport {
    fun readAndSave(contentResolver: ContentResolver, uri: Uri, targetAsset: File, wallpaper: Boolean): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Invalid image")
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > (if (wallpaper) 1920 else 512)) options.inSampleSize *= 2
        var decoded: Bitmap? = null
        contentResolver.openInputStream(uri)?.use { input ->
            decoded = BitmapFactory.decodeStream(input, null, options)
        }
        val image = decoded ?: throw IOException("Invalid image")
        val destination = AtomicFile(targetAsset)
        val out = destination.startWrite()
        try {
            if (!image.compress(if (wallpaper) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (wallpaper) 85 else 100, out)) throw IOException("Cannot save image")
            destination.finishWrite(out)
        } catch (error: Exception) { destination.failWrite(out); throw error }
        return image
    }
}
