package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random
import java.util.concurrent.Executors

/** Displays and rotates a theme's local wallpaper while Home is visible. */
internal class WallpaperPlayer(context: Context, private val root: FrameLayout, config: Config) {
    private val backgroundColor = config.backgroundColor
    private val files = ArrayList(config.wallpapers)
    private val interval = config.wallpaperRotationMinutes * 60_000L
    private val order = config.wallpaperOrder
    private val preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
    private val decoder = Executors.newSingleThreadExecutor()
    private val random = Random()
    private val metrics = context.resources.displayMetrics
    private val width = metrics.widthPixels
    private val height = metrics.heightPixels
    private val bitmapConfig = if (config.wallpaperTrueColor) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
    private val dimColor = if (config.wallpaperDim > 0) (config.wallpaperDim * 255 / 100) shl 24 else 0
    private var current: Bitmap? = null
    private var active = false
    private var closed = false
    private var loading = false
    private var displayed = ""
    private val moods = config.wallpaperMoods
    private val latitude = if (moods.isEmpty()) "" else context.getString(R.string.latitude)
    private val longitude = if (moods.isEmpty()) "" else context.getString(R.string.longitude)
    private var sunDay: LocalDate? = null
    private var sun = intArrayOf(WallpaperMood.DEFAULT_SUNRISE, WallpaperMood.DEFAULT_SUNSET)
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val names = if (config.wallpaperCaptions && config.wallpaperEnabled && files.isNotEmpty()) {
        WallpaperCaptions.read(files[0].parentFile)
    } else emptyMap()
    private val caption = if (names.isEmpty()) null else makeCaption(context)

    init {
        val collection = files.joinToString("", transform = { "${it.name}:${it.length()};" })
        if (collection != preferences.getString("collection", "")) {
            preferences.edit().putString("collection", collection).remove("image").remove("changed").apply()
        }
        if (config.wallpaperEnabled && files.isNotEmpty()) {
            root.addView(image, 0, FrameLayout.LayoutParams(-1, -1))
            showFirst()
        }
    }

    private fun showFirst() {
        val previous = preferences.getString("image", "")
        var playable = playable()
        var first = WallpaperRotation.find(playable, previous) ?: WallpaperRotation.pick(playable, previous, order, random)
        while (first != null) {
            val bitmap = decode(first)
            if (bitmap != null) {
                show(bitmap)
                displayed = first.name
                showCaption(false)
                if (displayed != previous) saveSelection()
                return
            }
            files.remove(first)
            playable = playable()
            first = WallpaperRotation.pick(playable, previous, order, random)
        }
    }

    fun start() {
        active = true
        update()
    }

    fun stop() { active = false }

    fun releaseForMemoryPressure() {
        if (current == null) return
        image.setImageDrawable(null)
        // The renderer may still have a queued frame referencing the previous bitmap.
        current = null
        displayed = ""
        root.setBackgroundColor(backgroundColor)
    }

    private fun playable(): List<File> {
        if (moods.isEmpty()) return files
        val now = LocalDateTime.now()
        val day = now.toLocalDate()
        if (day != sunDay) {
            sunDay = day
            sun = WallpaperMood.sun(day, latitude, longitude, ZoneId.systemDefault())
        }
        return WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.hour * 60 + now.minute, sun[0], sun[1]))
    }

    fun close() {
        active = false
        closed = true
        decoder.shutdownNow()
    }

    fun update() {
        if (!active || closed || loading || files.isEmpty()) return
        val previous = preferences.getString("image", "")
        val playable = playable()
        val changed = preferences.getLong("changed", 0)
        val selected = WallpaperRotation.find(playable, previous)
        val next = if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            WallpaperRotation.pick(playable, previous, order, random)
        } else selected
        if (next == null || next.name == displayed) return
        loading = true
        decoder.execute {
            val bitmap = decode(next)
            root.post {
                loading = false
                if (closed || !active) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap == null) {
                    files.remove(next)
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: ${next.name}")
                    update()
                } else {
                    show(bitmap)
                    displayed = next.name
                    showCaption(true)
                    if (displayed != previous || changed == 0L) saveSelection()
                }
            }
        }
    }

    private fun saveSelection() {
        preferences.edit().putString("image", displayed).putLong("changed", System.currentTimeMillis()).apply()
    }

    private fun makeCaption(context: Context): TextView {
        val dp = metrics.density
        val caption = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xd9ffffff.toInt())
            setShadowLayer(6 * dp, 0f, dp, 0xcc000000.toInt())
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = Math.round(520 * dp)
            isFocusable = false
            alpha = 0f
            try {
                typeface = UiFont.get(context, R.string.font_regular)
            } catch (_: Exception) {
                // Use the system font if the theme font is unavailable.
            }
        }
        root.addView(caption, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = Math.round(110 * dp)
            marginEnd = Math.round(62 * dp)
        })
        return caption
    }

    private fun showCaption(fade: Boolean) {
        val caption = caption ?: return
        val name = names[displayed]
        caption.animate().cancel()
        if (name == null) {
            caption.alpha = 0f
            return
        }
        caption.contentDescription = name
        if (!fade || caption.alpha == 0f) {
            caption.text = name
            caption.alpha = 1f
            return
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction {
            caption.text = name
            caption.animate().alpha(1f).setDuration(350).start()
        }.start()
    }

    private fun show(bitmap: Bitmap) {
        val old = current
        current = bitmap
        root.background = null
        image.setImageBitmap(bitmap)
        if (old != null && old !== bitmap) image.postDelayed(old::recycle, 500)
    }

    private fun decode(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null
            val sample = WallpaperSize.sampleSize(w, h, width, height)
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            WallpaperSize.coverScale(w / sample, h / sample, width, height)?.let {
                options.inScaled = true
                options.inDensity = it[0]
                options.inTargetDensity = it[1]
            }
            options.inPreferredConfig = bitmapConfig
            options.inMutable = dimColor != 0
            val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
            if (dimColor != 0) Canvas(decoded).drawColor(dimColor)
            val gpu = decoded.copy(Bitmap.Config.HARDWARE, false) ?: return decoded
            decoded.recycle()
            gpu
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
