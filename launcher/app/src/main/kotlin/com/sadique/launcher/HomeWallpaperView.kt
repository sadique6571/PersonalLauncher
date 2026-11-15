package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.view.View
import java.io.File
import java.util.concurrent.Executor

/** Draws Home wallpaper and handles the daily Bing image crossfade. */
internal class HomeWallpaperView(
    context: Context,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val handler: Handler,
    private val worker: Executor,
    private val homeBingWallpaper: HomeBingWallpaper,
    previous: HomeWallpaperView?,
    private val favoriteFile: () -> File,
    private val customWallpaper: () -> Bitmap?,
    private val gradientAccent: () -> Int?,
    private val clockGlowAccent: () -> Int?,
    private val isCurrent: (HomeWallpaperView) -> Boolean,
    private val onWallpaperChanged: () -> Unit,
) : View(context) {
    private val glassBackdrop = GlassOsBackdrop()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Never share the bitmap paint with translucent sun/moon or focus effects.
    private val wallpaperPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** Decoded off the main thread; onDraw draws the gradient alone until it arrives. */
    var backdrop: Bitmap? = null
    private var smoothDay = -1f

    /** Releases transient bitmaps (online photos, crossfade buffer) under memory pressure. */
    fun releaseTransient() {
        stopCrossfade()
        crossfade = 1f
        // Don't recycle backdrop — it's the built-in wallpaper, still used in onDraw.
        // Only clear the reloadable online variants.
        onlineNext = null
        // Keep onlineWallpaper if it's currently displayed; it will be re-fetched on demand.
        // Null it only if we have a fallback.
        if (backdrop != null) {
            onlineWallpaper = null
        }
    }

    fun stopCrossfade() {
        handler.removeCallbacks(crossfadeStep)
    }

    /**
     * Carry the already-loaded wallpapers into a fresh HomeWallpaperView created by
     * buildHome(), so a settings change never flashes the default wallpaper.
     * The old instance is detached: its fields are nulled so a stale posted
     * runnable can never recycle a bitmap this instance is drawing.
     * (User 2026-11-01.)
     */
    fun adoptWallpapers(old: HomeWallpaperView) {
        if (old.backdrop != null) backdrop = old.backdrop
        if (old.onlineWallpaper != null || old.onlineNext != null) {
            onlineWallpaper = old.onlineWallpaper
            onlineNext = old.onlineNext
            onlineIndex = old.onlineIndex
            onlineImageUrl = old.onlineImageUrl
            onlineCheckedAt = old.onlineCheckedAt
            crossfade = old.crossfade
        }
        old.backdrop = null
        old.onlineWallpaper = null
        old.onlineNext = null
        old.onlineIndex = -1
        // A crossfade interrupted mid-flight continues on this instance.
        if (crossfade < 1f) {
            handler.removeCallbacks(crossfadeStep)
            handler.post(crossfadeStep)
        }
    }

    var minute = 0
    var focusAccent = 0
    var clockX = -1f
    var clockY = -1f
    var focusX = 0f
    var focusY = 0f
    var focusRadius = 0f
    /** Analog face radius in px, set from clock_size pref. */
    var clockRadius = -1f
    /** Analog clock mode: 0=digital, 1=analog. Set from prefs. */
    var clockStyle = 0
    /** Online wallpaper with crossfade. */
    private var onlineWallpaper: Bitmap? = null
    private var onlineNext: Bitmap? = null
    private var crossfade = 1f
    private var onlineIndex = -1
    private var onlineImageUrl = ""
    private var onlineCheckedAt = 0L
    private var onlineLoading = false
    /** Favorite wallpaper (saved by user). */
    private var favoriteWallpaper: Bitmap? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // A restored photo must exist before this View's first frame, including process restarts.
        if (previous == null || (previous.onlineWallpaper == null && previous.onlineNext == null)) {
            restoreBingSnapshot()
        }
        worker.execute {
            val bmp = if (prefs.getInt("wallpaper_source", 0) == 1) null
                    else BitmapFactory.decodeResource(resources, R.drawable.personal_wallpaper)
            var fav: Bitmap? = null
            try {
                val f = favoriteFile()
                if (f.exists()) fav = BitmapFactory.decodeFile(f.absolutePath)
            } catch (ignored: Exception) {
            }
            val bg = bmp
            val fg = fav
            handler.post { backdrop = bg; favoriteWallpaper = fg; invalidate(); onWallpaperChanged() }
        }
    }

    private fun restoreBingSnapshot() {
        if (prefs.getInt("wallpaper_source", 0) != 1 || onlineWallpaper != null || onlineNext != null) return
        val saved = homeBingWallpaper.restore() ?: return
        onlineWallpaper = saved.image
        onlineImageUrl = saved.url
        onlineCheckedAt = saved.checkedAt
        onlineIndex = 0
        crossfade = 1f
    }

    /** The wallpaper bitmap currently drawn (or null if none yet). */
    fun currentPicture(): Bitmap? {
        val mode = prefs.getString("wallpaper_mode", "mountains")
        val source = prefs.getInt("wallpaper_source", 0)
        var picture = if (mode == "custom") customWallpaper() ?: backdrop else backdrop
        if (source == 1) picture = onlineWallpaper ?: onlineNext
        if (source == 2 && favoriteWallpaper != null) picture = favoriteWallpaper
        if (mode == "solid") picture = null
        return picture
    }

    /** Keep Bing's daily image; periodically check metadata so a new day works while Home stays open. */
    fun loadOnlinePhoto(index: Int) {
        if (onlineLoading) return
        restoreBingSnapshot()
        if (index == onlineIndex && (onlineWallpaper != null || onlineNext != null)
                && !LandscapeModel.bingCheckDue(onlineCheckedAt, System.currentTimeMillis())) return
        onlineLoading = true
        val previousUrl = onlineImageUrl
        val hasCurrentPhoto = onlineWallpaper != null || onlineNext != null
        worker.execute {
            val (loaded, newUrl, metadataChecked) = homeBingWallpaper.refresh(index, previousUrl, hasCurrentPhoto)
            handler.post {
                onlineLoading = false
                if (!isCurrent(this)) { loaded?.recycle(); return@post }
                if (metadataChecked && (loaded != null || newUrl == onlineImageUrl)) {
                    onlineCheckedAt = System.currentTimeMillis()
                    onlineIndex = index
                }
                if (loaded == null) return@post
                onlineImageUrl = newUrl
                if (onlineWallpaper == null) {
                    onlineWallpaper = loaded
                    crossfade = 1f
                    invalidate()
                    onWallpaperChanged()
                    return@post
                }
                val next = onlineNext
                if (next != null && next !== onlineWallpaper) next.recycle()
                onlineNext = loaded
                crossfade = 0f
                handler.removeCallbacks(crossfadeStep)
                handler.post(crossfadeStep)
            }
        }
    }

    private val crossfadeStep = object : Runnable {
        override fun run() {
            if (onlineNext == null) { crossfade = 1f; return }
            crossfade += 0.06f
            if (crossfade >= 1f) {
                crossfade = 1f
                onlineWallpaper?.recycle()
                onlineWallpaper = onlineNext
                onlineNext = null
                onWallpaperChanged()
            } else {
                handler.postDelayed(this, 50)
            }
            invalidate()
        }
    }

    fun wallpaperFrame(image: Bitmap, clockY: Float, mountains: Boolean): FloatArray {
        return if (tv) HomeAppearance.fitFrame(width, height, image.width, image.height)
                else HomeAppearance.frame(width, height, image.width, image.height, clockY, mountains)
    }

    override fun onDraw(canvas: Canvas) {
        val backdropAccent = gradientAccent()
        if (backdropAccent != null) {
            glassBackdrop.draw(canvas, width, height, backdropAccent, Math.max(0, Math.min(100, prefs.getInt("ambient_strength", 100))))
            return
        }
        paint.color = Color.WHITE
        paint.shader = null
        val theme = prefs.getString("theme_mode", "dark")
        val targetDay = HomeAppearance.daylight(theme, minute)
        if (smoothDay < 0) smoothDay = targetDay // First draw: snap.
        // Smooth day/night transition: ease towards the target instead of jumping.
        if (prefs.getBoolean("daynight_smooth", true)) {
            if (Math.abs(targetDay - smoothDay) > 0.001f) {
                smoothDay += (targetDay - smoothDay) * 0.08f
                // Keep redrawing until settled.
                postInvalidateOnAnimation()
            } else {
                smoothDay = targetDay
            }
        } else {
            smoothDay = targetDay
        }
        val day = smoothDay
        val top = HomeAppearance.blend(0xff020817.toInt(), 0xff145d85.toInt(), day)
        val bottom = HomeAppearance.blend(0xff07172b.toInt(), 0xff092b4a.toInt(), day)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        val mode = prefs.getString("wallpaper_mode", "mountains")
        val source = prefs.getInt("wallpaper_source", 0)
        val onlineSource = source == 1
        var picture = if (mode == "custom") customWallpaper() ?: backdrop else backdrop
        // Online wallpaper overrides when enabled and a photo is loaded.
        if (onlineSource) picture = onlineWallpaper ?: onlineNext
        // Favorite wallpaper.
        if (source == 2 && favoriteWallpaper != null) picture = favoriteWallpaper
        val y = if (clockY >= 0) clockY else dp(150).toFloat()
        if (mode != "solid" && picture != null) {
            val mountains = picture === backdrop
            val frame = wallpaperFrame(picture, y, mountains)
            canvas.drawBitmap(picture, null, RectF(frame[0], frame[1], frame[2], frame[3]), wallpaperPaint)
            // Crossfade: incoming photo fades in over the current one.
            val next = onlineNext
            if (onlineSource && next != null && crossfade < 1f) {
                wallpaperPaint.alpha = (crossfade * 255).toInt()
                val nextFrame = wallpaperFrame(next, y, false)
                canvas.drawBitmap(next, null, RectF(nextFrame[0], nextFrame[1], nextFrame[2], nextFrame[3]), wallpaperPaint)
                wallpaperPaint.alpha = 255
            }
            val shade = HomeAppearance.shade(theme, minute, prefs.getInt("background_dim", 0))
            canvas.drawColor((shade shl 24) or 0x00051327)

            if (mountains && height > width) {
                // Blend the portrait hero into midnight below the lake, keeping the ridge visible.
                val base = HomeAppearance.blend(0xff07172b.toInt(), 0xff092b4a.toInt(), day)
                paint.shader = LinearGradient(0f, frame[3] * 0.78f, 0f, frame[3],
                        base and 0x00ffffff, base, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, frame[3] * 0.78f, width.toFloat(), frame[3], paint)
                paint.shader = null
            }
            // Bottom shade: dark gradient behind tiles for readability (demo look).
            if (prefs.getBoolean("bottom_shade", true)) {
                paint.shader = LinearGradient(0f, height * 0.55f, 0f, height.toFloat(),
                        Color.TRANSPARENT, 0x99000000.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
            }
        }
        if (focusAccent != 0 && focusRadius > 0) {
            val glow = (focusAccent and 0x00ffffff) or if (ThemeStyle.light(prefs)) 0x33000000 else 0x80000000.toInt()
            paint.shader = RadialGradient(focusX, focusY, focusRadius, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(focusX, focusY, focusRadius, paint)
            paint.shader = null
        }
        val cy = if (clockY >= 0) clockY else dp(150).toFloat()
        // Clock glow: soft halo behind the clock.
        val accent = clockGlowAccent()
        if (accent != null) {
            paint.shader = RadialGradient(if (clockX >= 0) clockX else width / 2f, cy, dp(160).toFloat(),
                    (accent and 0x00ffffff) or 0x60000000, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(if (clockX >= 0) clockX else width / 2f, cy, dp(160).toFloat(), paint)
            paint.shader = null
        }
        // Analog clock face.
        if (clockStyle == 1) {
            val radius = if (clockRadius > 0) clockRadius else Math.min(width, height) / 7f
            val cx = Math.max(radius, Math.min(width - radius, if (clockX >= 0) clockX else width / 2f))
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = if (ThemeStyle.light(prefs)) 0xccfff6e9.toInt() else 0x99000000.toInt()
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = ThemeStyle.foreground(prefs)
            canvas.drawCircle(cx, cy, radius, paint)
            val hour = (minute / 60) % 12
            val min = minute % 60
            // Tick marks.
            paint.strokeWidth = dp(2).toFloat()
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30).toDouble())
                val inner = radius * 0.85f
                val outer = radius * 0.95f
                canvas.drawLine(cx + (inner * Math.sin(angle)).toFloat(), cy - (inner * Math.cos(angle)).toFloat(),
                        cx + (outer * Math.sin(angle)).toFloat(), cy - (outer * Math.cos(angle)).toFloat(), paint)
            }
            // Hands.
            val hourAngle = Math.toRadians(((hour + min / 60f) * 30).toDouble())
            val minAngle = Math.toRadians((min * 6).toDouble())
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = dp(6).toFloat()
            canvas.drawLine(cx, cy, cx + (radius * 0.5 * Math.sin(hourAngle)).toFloat(),
                    cy - (radius * 0.5 * Math.cos(hourAngle)).toFloat(), paint)
            paint.strokeWidth = dp(4).toFloat()
            canvas.drawLine(cx, cy, cx + (radius * 0.75 * Math.sin(minAngle)).toFloat(),
                    cy - (radius * 0.75 * Math.cos(minAngle)).toFloat(), paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, dp(5).toFloat(), paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
}
