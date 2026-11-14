package com.sadique.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Default app artwork, with adaptive backgrounds filling the launcher's own rounded outline. */
class AppArtwork private constructor(@JvmField val image: Drawable, @JvmField val accent: Int) {

    companion object {
        @JvmStatic
        fun create(resources: Resources, source: Drawable): AppArtwork {
            return create(resources, source, 1f)
        }

        @JvmStatic
        fun create(resources: Resources, source: Drawable, logoScale: Float): AppArtwork {
            return create(resources, source, logoScale, false)
        }

        @JvmStatic
        fun create(resources: Resources, source: Drawable, logoScale: Float, mark: Boolean): AppArtwork {
            val size = 256
            var bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val icon = if (source.constantState == null) source.mutate() else source.constantState!!.newDrawable().mutate()
            val ratio = if (icon.intrinsicWidth > 0 && icon.intrinsicHeight > 0)
                icon.intrinsicWidth.toFloat() / icon.intrinsicHeight
            else 1f
            val width = if (ratio >= 1f) size else Math.round(size * ratio)
            val height = if (ratio >= 1f) Math.round(size / ratio) else size
            icon.setBounds((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2)
            icon.draw(canvas)
            // Bulk-read pixels once: per-pixel getPixel() is a JNI call each, ~66k per icon.
            var pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            // Sample before trimming: a cropped logo's corner may be red (Netflix N),
            // which must never become the background colour of the whole card.
            val nativeCorner = pixels[0]
            val nativeBackground = if ((nativeCorner ushr 24) > 240) nativeCorner else 0xff092038.toInt()
            // Remove baked-in transparent margins from legacy icons, not just ImageView padding.
            var left = size
            var top = size
            var right = -1
            var bottom = -1
            for (y in 0 until size) {
                val row = y * size
                for (x in 0 until size) {
                    if ((pixels[row + x] ushr 24) < 24) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right >= left && bottom >= top) {
                bitmap = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
                val w = right - left + 1
                val h = bottom - top + 1
                val trimmed = IntArray(w * h)
                for (y in 0 until h) {
                    System.arraycopy(pixels, (top + y) * size + left, trimmed, y * w, w)
                }
                pixels = trimmed
            }
            val weights = FloatArray(12)
            val sums = Array(12) { FloatArray(3) }
            val hsv = FloatArray(3)
            val pw = bitmap.width
            val ph = bitmap.height
            val stepX = Math.max(1, pw / 16)
            val stepY = Math.max(1, ph / 16)
            var y = 0
            while (y < ph) {
                var x = 0
                while (x < pw) {
                    val pixel = pixels[y * pw + x]
                    if ((pixel ushr 24) < 128) {
                        x += stepX
                        continue
                    }
                    Color.colorToHSV(pixel, hsv)
                    if (hsv[1] < 0.25f || hsv[2] < 0.2f) {
                        x += stepX
                        continue
                    }
                    val bin = (hsv[0] / 30).toInt() % 12
                    val weight = hsv[1] * hsv[2]
                    weights[bin] += weight
                    sums[bin][0] += Color.red(pixel) * weight
                    sums[bin][1] += Color.green(pixel) * weight
                    sums[bin][2] += Color.blue(pixel) * weight
                    x += stepX
                }
                y += stepY
            }
            var best = 0
            for (i in 1 until weights.size) {
                if (weights[i] > weights[best]) best = i
            }
            val accent = if (weights[best] < 1f) 0xff40d9ff.toInt() else Color.rgb(
                (sums[best][0] / weights[best]).toInt(),
                (sums[best][1] / weights[best]).toInt(),
                (sums[best][2] / weights[best]).toInt()
            )
            if (mark) {
                val logo = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                val logoCanvas = Canvas(logo)
                val scale = ArtworkFit.scale(bitmap.width, bitmap.height, logoScale)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                logoCanvas.drawBitmap(
                    bitmap,
                    null,
                    RectF((256 - w) / 2, (256 - h) / 2, (256 + w) / 2, (256 + h) / 2),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                return AppArtwork(BitmapDrawable(resources, logo), accent)
            }
            // A clean card background and proportional logo; never stretch icon edge pixels.
            val card = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
            val cardCanvas = Canvas(card)
            cardCanvas.drawColor(nativeBackground)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val scale = Math.min(270f / bitmap.width, 140f / bitmap.height) * logoScale
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val x = (320 - w) / 2f
            val yCoord = (180 - h) / 2f
            cardCanvas.drawBitmap(bitmap, null, RectF(x, yCoord, x + w, yCoord + h), paint)
            return AppArtwork(BitmapDrawable(resources, card), accent)
        }
    }
}
