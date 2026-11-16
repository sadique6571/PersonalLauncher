package com.sadique.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.ByteArrayOutputStream

/** Applies the same framing before either a queued or a live photo is decoded. */
internal object LandscapePhotoFraming {
    fun prepare(bytes: ByteArray, scene: LandscapeFeed.Scene): ByteArray? {
        val framed = if (scene.cropOffsetPercent >= 0) {
            frameReviewedPortrait(bytes, scene.cropOffsetPercent) ?: return null
        } else bytes
        return if (scene.livePeople) fitLivePeoplePhoto(framed) else framed
    }

    private fun frameReviewedPortrait(bytes: ByteArray, offsetPercent: Int): ByteArray? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        val frameHeight = width / 16 * 9
        // Only frame the reviewed horizontal previews. If a CDN rendition changes, skip it.
        if (width != 1024 || height < frameHeight || height > width) return null
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        var framed = original
        try {
            val top = Math.round((height - frameHeight) * offsetPercent / 100f)
            framed = Bitmap.createBitmap(original, 0, top, width, frameHeight)
            val output = ByteArrayOutputStream()
            return if (framed.compress(Bitmap.CompressFormat.JPEG, 90, output)) output.toByteArray() else null
        } finally {
            if (framed != original) framed.recycle()
            original.recycle()
        }
    }

    private fun fitLivePeoplePhoto(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (!LandscapeFeed.fitsPeopleFrame(bounds.outWidth, bounds.outHeight)) return null
        if (LandscapeFeed.fitsWidescreen(bounds.outWidth, bounds.outHeight)) return bytes
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        var frame: Bitmap? = null
        try {
            val units = (original.height + 8) / 9
            val created = Bitmap.createBitmap(units * 16, units * 9, Bitmap.Config.ARGB_8888)
            frame = created
            val canvas = Canvas(created)
            canvas.drawColor(0xff041225.toInt())
            canvas.drawBitmap(
                original, (created.width - original.width) / 2f,
                (created.height - original.height) / 2f, null
            )
            val output = ByteArrayOutputStream()
            return if (created.compress(Bitmap.CompressFormat.JPEG, 90, output)) output.toByteArray() else null
        } finally {
            frame?.recycle()
            original.recycle()
        }
    }
}
