package com.sadique.launcher

import android.animation.ArgbEvaluator
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextClock
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayList
import java.util.Random

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
internal class ClockScene(root: View) {
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private val content: View = root.findViewById(R.id.dreamContent)
    private val ambient: ImageView = root.findViewById(R.id.dreamAmbient)
    private val dreamTime: TextClock? = root.findViewById(R.id.dreamTime)
    private val dreamAmpm: TextClock? = root.findViewById(R.id.dreamAmpm)
    private val dreamDate: TextClock? = root.findViewById(R.id.dreamDate)
    private val glow: GradientDrawable
    private var colorIndex = 0
    private var glowColor = 0
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private var wallpaperShown = false
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private var wallpaperFile: File? = null
    private var wallpaperShade = 0
    private var wallpaperRes: Resources? = null
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private var wallpaperGeneration = 0
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private var rotation: MutableList<File>? = null
    private var rotationMs = 0L
    private var shownMs = 0L
    private var order: String? = null
    private var moods: Map<String, Set<String>>? = null
    private var latitude: String? = null
    private var longitude: String? = null

    private val tick: Runnable = object : Runnable {
        override fun run() {
            moveClock()
            shiftGlow()
            advanceWallpaper()
            handler.postDelayed(this, MOVE_MS)
        }
    }

    init {
        styleClockText()
        glow = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
        }
        ambient.background = glow
        ambient.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            glow.gradientRadius = (r - l) * 0.55f
        }
        colorIndex = random.nextInt(PALETTE.size)
        setGlow(PALETTE[colorIndex])
    }

    fun start() {
        content.alpha = 0f
        content.post {
            placeClock()
            content.animate().alpha(1f).duration = 1200
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, MOVE_MS)
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null) // the tick and any glow steps still queued
        content.animate().cancel()
        wallpaperGeneration++ // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the clock, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    fun useHomeWallpaper(ctx: Context) {
        val cfg = try {
            Config.load(ctx)
        } catch (e: RuntimeException) {
            return
        }
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true // also stops the glow's colour cycle
                ambient.background = null
            }
            return
        }
        if (cfg.wallpapers.isEmpty()) {
            wallpaperFile = null
        } else {
            rotation = ArrayList(cfg.wallpapers)
            val shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                .getString("image", "")
            val current = WallpaperRotation.find(rotation, shown)
            wallpaperFile = current ?: rotation!![0]
            rotationMs = cfg.wallpaperRotationMinutes * 60_000L
            order = cfg.wallpaperOrder
            moods = cfg.wallpaperMoods
            latitude = if (moods.isNullOrEmpty()) "" else ctx.getString(R.string.latitude)
            longitude = if (moods.isNullOrEmpty()) "" else ctx.getString(R.string.longitude)
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) shl 24
        wallpaperRes = ctx.resources
    }

    private fun loadWallpaper() {
        val file = wallpaperFile ?: return
        val shade = wallpaperShade
        val res = wallpaperRes ?: return
        val dm = res.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val generation = ++wallpaperGeneration
        Thread({
            val bmp = decode(file, w, h) ?: return@Thread
            handler.post {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle() // stopped meanwhile (or already shown): this picture was never attached
                    return@post
                }
                wallpaperShown = true
                ambient.background = null
                ambient.setImageDrawable(
                    LayerDrawable(
                        arrayOf<Drawable>(
                            BitmapDrawable(res, bmp),
                            ColorDrawable(shade)
                        )
                    )
                )
                adaptClockColors(bmp, shade)
            }
        }, "clock-wallpaper").start()
    }

    private fun styleClockText() {
        if (dreamTime == null) return
        val dp = content.resources.displayMetrics.density
        dreamTime.setTextColor(0xFFFFFFFF.toInt())
        dreamTime.setShadowLayer(6 * dp, 0f, 3 * dp, 0xE6000000.toInt())
        dreamAmpm?.setTextColor(0xE6FFFFFF.toInt())
        dreamAmpm?.setShadowLayer(4 * dp, 0f, 2 * dp, 0xE6000000.toInt())
        dreamDate?.setTextColor(0xFFFFFFFF.toInt())
        dreamDate?.setShadowLayer(5 * dp, 0f, 3 * dp, 0xE6000000.toInt())
    }

    private fun adaptClockColors(bmp: Bitmap, shade: Int) {
        styleClockText()
    }

    private fun advanceWallpaper() {
        if (rotation == null || rotationMs <= 0 || rotation!!.size < 2 || wallpaperFile == null) return
        shownMs += MOVE_MS
        if (shownMs < rotationMs) return
        shownMs = 0
        val next = WallpaperRotation.pick(playable(), wallpaperFile!!.name, order, random)
        if (next == wallpaperFile) return
        wallpaperFile = next
        wallpaperShown = false
        loadWallpaper()
    }

    private fun playable(): List<File> {
        val rot = rotation ?: return emptyList()
        val m = moods ?: return rot
        if (m.isEmpty()) return rot
        val now = LocalDateTime.now()
        val sun = WallpaperMood.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault())
        return WallpaperMood.select(
            rot, m,
            WallpaperMood.timeOfDay(now.hour * 60 + now.minute, sun[0], sun[1])
        )
    }

    private fun moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction {
            placeClock()
            content.animate().alpha(1f).setDuration(1200).withLayer().start()
        }.start()
    }

    private fun placeClock() {
        val parent = content.parent as? View ?: return
        val maxX = Math.max(0, parent.width - content.width)
        val maxY = Math.max(0, parent.height - content.height)
        val x = maxX * (0.1f + 0.8f * random.nextFloat())
        val y = maxY * (0.1f + 0.8f * random.nextFloat())
        content.x = x
        content.y = y
        if (parent.width > 0 && parent.height > 0) {
            glow.setGradientCenter(
                (x + content.width / 2f) / parent.width,
                (y + content.height / 2f) / parent.height
            )
        }
    }

    private fun shiftGlow() {
        if (wallpaperShown) return
        val from = glowColor
        colorIndex = (colorIndex + 1) % PALETTE.size
        val to = PALETTE[colorIndex]
        val mix = ArgbEvaluator()
        for (i in 1..GLOW_STEPS) {
            val f = i.toFloat() / GLOW_STEPS
            handler.postDelayed({ setGlow(mix.evaluate(f, from, to) as Int) }, (i * GLOW_STEP_MS))
        }
    }

    private fun setGlow(color: Int) {
        glowColor = color
        val c = color and 0x00FFFFFF
        glow.colors = intArrayOf(0x40000000 or c, 0x14000000 or c, 0x00000000 or c)
    }

    companion object {
        private const val MOVE_MS = 60_000L
        private const val GLOW_STEPS = 16
        private const val GLOW_STEP_MS = 500L
        private val PALETTE = intArrayOf(
            0xFF2B5CFF.toInt(),
            0xFF00A39A.toInt(),
            0xFF8A3FFC.toInt(),
            0xFFE0661B.toInt(),
            0xFFD6336C.toInt()
        )

        private fun decode(file: File, screenW: Int, screenH: Int): Bitmap? {
            return try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.path, options)
                val width = options.outWidth
                val height = options.outHeight
                if (width <= 0 || height <= 0) return null
                val sample = WallpaperSize.sampleSize(width, height, screenW, screenH)
                if (!WallpaperSize.accepted(width / sample, height / sample)) return null
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
                val scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH)
                if (scale != null) {
                    options.inScaled = true
                    options.inDensity = scale[0]
                    options.inTargetDensity = scale[1]
                }
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.path, options)
            } catch (failure: Throwable) {
                null
            }
        }
    }
}
