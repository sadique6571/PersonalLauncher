package com.sadique.launcher

import android.service.dreams.DreamService

/** System-managed landscape screensaver. Any remote key returns to the previous app. */
class LandscapeDream : DreamService() {
    private var landscape: LandscapeView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val view = LandscapeView(this)
        landscape = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        checkNotNull(landscape).start()
    }

    override fun onDreamingStopped() {
        landscape?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        landscape?.stop()
        super.onDetachedFromWindow()
    }
}
