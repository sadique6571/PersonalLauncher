package com.sadique.launcher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/** Displays progress in Home's window and releases it before opening the installer. */
class UpdateDownloadUi {
    private val main = Handler(Looper.getMainLooper())
    private var capsule = WeakReference<DownloadCapsule>(null)
    private var message = "Checking GitHub"
    private var percent = -1
    private var posted = false
    private var closed = false
    private var completing = false
    private val render = Runnable {
        synchronized(this) {
            posted = false
            if (!closed) capsule.get()?.progress(message, percent)
        }
    }

    fun attach(host: Activity) {
        if (host.isFinishing || host.isDestroyed) return
        val content = host.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        synchronized(this) {
            if (closed || completing) return
            if (capsule.get()?.parent === content) {
                render.run()
                return
            }
            remove()
            val shell = DownloadCapsule(host).apply { keepScreenOn = true }
            val width = minOf(GlassSettings.dp(host, 440f),
                host.resources.displayMetrics.widthPixels - GlassSettings.dp(host, 32f))
            val position = FrameLayout.LayoutParams(width, GlassSettings.dp(host, 48f),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            position.topMargin = GlassSettings.dp(host, 12f)
            content.addView(shell, position)
            capsule = WeakReference(shell)
            render.run()
        }
    }

    @Synchronized
    fun report(message: String, percent: Int) {
        if (closed || completing || (this.message == message && this.percent == percent)) return
        this.message = message
        this.percent = percent
        if (!posted && capsule.get() != null) {
            posted = true
            main.postDelayed(render, 80)
        }
    }

    @Synchronized
    fun complete(openInstaller: Runnable) {
        if (closed || completing) return
        completing = true
        message = "Verified update"
        percent = 100
        main.post {
            render.run()
            main.postDelayed({
                synchronized(this) {
                    closed = true
                    remove()
                }
                openInstaller.run()
            }, 180)
        }
    }

    @Synchronized
    fun finish() {
        if (completing || closed) return
        closed = true
        main.removeCallbacks(render)
        main.post(::remove)
    }

    private fun remove() {
        val view = capsule.get()
        (view?.parent as? ViewGroup)?.removeView(view)
        capsule.clear()
    }
}
