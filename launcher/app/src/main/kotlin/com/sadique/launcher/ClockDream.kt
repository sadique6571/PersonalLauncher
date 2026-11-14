package com.sadique.launcher

import android.service.dreams.DreamService

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * No network, no images: cheap enough for a 1 GB TV.
 */
open class ClockDream : DreamService() {
    private var scene: ClockScene? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false // any key wakes the TV back to where it was
        isFullscreen = true
        isScreenBright = false
        setContentView(R.layout.dream_clock)
        scene = ClockScene(window.decorView).apply {
            useHomeWallpaper(this@ClockDream)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scene?.start()
    }

    override fun onDreamingStopped() {
        scene?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scene?.stop()
        super.onDetachedFromWindow()
    }
}
