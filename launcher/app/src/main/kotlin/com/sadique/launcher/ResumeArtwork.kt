package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Loads bounded Watch Next artwork off the UI thread. */
object ResumeArtwork {
    private const val LIMIT = 2 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @JvmStatic
    fun load(context: Context, vararg candidates: String?): Bitmap? {
        for (address in candidates) {
            if (Thread.currentThread().isInterrupted) return null
            if (address == null || address.length > 8192) continue
            cache.get(address)?.let { return it }
            val directory = File(context.applicationContext.cacheDir, "resume_posters")
            directory.mkdirs()
            val file = File(directory, Integer.toHexString(address.hashCode()) + ".jpg")
            if (file.exists()) {
                val image = BitmapFactory.decodeFile(file.path)
                if (image != null) {
                    cache.put(address, image)
                    return image
                }
                file.delete()
            }
            val image = try {
                val data = read(context, address) ?: continue
                decode(data) ?: continue
            } catch (_: Exception) {
                // The publishing app's icon is the fallback when no poster decodes.
                continue
            }
            cache.put(address, image)
            try {
                file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (_: Exception) {
                // The memory cache still serves the poster.
            }
            return image
        }
        return null
    }

    private fun read(context: Context, address: String): ByteArray? {
        val uri = Uri.parse(address)
        if (uri.scheme == "content") {
            return context.contentResolver.openInputStream(uri)?.use { OnlineContent.readBounded(it, LIMIT) }
        }
        if (uri.scheme != "https" || uri.userInfo != null) return null
        val connection = URL(address).openConnection() as HttpsURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = true
        return try {
            if (connection.responseCode != 200 || connection.contentLengthLong > LIMIT) null
            else connection.inputStream.use { OnlineContent.readBounded(it, LIMIT) }
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(data: ByteArray): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }
}
