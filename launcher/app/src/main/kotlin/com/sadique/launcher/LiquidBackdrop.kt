package com.sadique.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.Executor

/** Shares a small blurred wallpaper sample between glass surfaces. */
class LiquidBackdrop(
    private val source: View,
    private val worker: Executor,
    private val main: Handler,
    private val finishOverride: Int,
    vararg initialTargets: View
) {
    constructor(source: View, worker: Executor, main: Handler, vararg targets: View) :
        this(source, worker, main, -1, *targets)

    private val targets = ArrayList<WeakReference<View>>()
    private val targetPoint = IntArray(2)
    private val rootPoint = IntArray(2)
    private var blurred: Bitmap? = null
    private var closed = false
    private var epoch = 0
    private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> align() }
    private val sourceLayout = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
        if (r - l != oldR - oldL || b - t != oldB - oldT) refresh()
    }
    private val capture: Runnable = Runnable(::takeSample)

    init {
        for (target in initialTargets) bind(target)
        source.addOnLayoutChangeListener(sourceLayout)
        refresh()
    }

    fun bind(target: View) {
        if (closed) return
        targets.removeAll { it.get() == null }
        if (targets.any { it.get() === target }) return
        targets.add(WeakReference(target))
        target.addOnLayoutChangeListener(layout)
        target.post(::align)
    }

    fun refresh() {
        if (closed) return
        epoch++
        main.removeCallbacks(capture)
        main.postDelayed(capture, 100)
    }

    private fun takeSample() {
        if (closed || !source.isAttachedToWindow || source.width <= 0 || source.height <= 0) return
        val generation = epoch
        val prefs = ThemePreferences.get(source.context)
        val finish = if (finishOverride < 0) TvGlassStyle.finish(prefs) else finishOverride
        val radius = GlassTransparency.blurRadius(finish, TvGlassStyle.intensity(prefs))
        val scale = 256f / maxOf(source.width, source.height)
        val width = maxOf(1, Math.round(source.width * scale))
        val height = maxOf(1, Math.round(source.height * scale))
        val sample = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: RuntimeException) {
            return
        } catch (_: OutOfMemoryError) {
            return
        }
        if (!drawSample(sample, scale)) {
            sample.recycle()
            return
        }
        worker.execute {
            if (Thread.currentThread().isInterrupted || !blur(sample, width, height, radius)) {
                sample.recycle()
                return@execute
            }
            main.post {
                if (closed || generation != epoch) {
                    sample.recycle()
                    return@post
                }
                blurred = sample
                align()
            }
        }
    }

    private fun drawSample(sample: Bitmap, scale: Float): Boolean {
        return try {
            val canvas = Canvas(sample)
            canvas.drawColor(0xff031126.toInt())
            canvas.scale(scale, scale)
            // Include wallpaper overscan without sampling foreground views.
            canvas.concat(source.matrix)
            source.draw(canvas)
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    private fun blur(sample: Bitmap, width: Int, height: Int, radius: Int): Boolean {
        return try {
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            GlassBlur.blur(pixels, width, height, radius)
            sample.setPixels(pixels, 0, width, 0, 0, width, height)
            true
        } catch (_: RuntimeException) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    private fun align() {
        if (closed) return
        val bitmap = blurred ?: return
        val root = source.parent as? View ?: return
        root.getLocationOnScreen(rootPoint)
        targets.removeAll { it.get() == null }
        for (reference in targets) {
            val target = reference.get() ?: continue
            if (!target.isAttachedToWindow) continue
            val surface = target.background as? GlassSurface ?: continue
            target.getLocationOnScreen(targetPoint)
            surface.backdrop(bitmap, source.width, source.height,
                targetPoint[0] - rootPoint[0], targetPoint[1] - rootPoint[1])
        }
    }

    fun close() {
        closed = true
        epoch++
        main.removeCallbacks(capture)
        source.removeOnLayoutChangeListener(sourceLayout)
        for (reference in targets) reference.get()?.removeOnLayoutChangeListener(layout)
        targets.clear()
        // Existing drawables own the sample until Android releases their view tree.
        blurred = null
    }
}
