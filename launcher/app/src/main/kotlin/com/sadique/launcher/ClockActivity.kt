package com.sadique.launcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
open class ClockActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LauncherLocale.wrap(base)) // locale
    }

    private var scene: ClockScene? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dream_clock)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE)
        scene = ClockScene(window.decorView).apply {
            useHomeWallpaper(this@ClockActivity)
        }
    }

    override fun onResume() {
        super.onResume()
        scene?.start()
    }

    override fun onPause() {
        scene?.stop()
        super.onPause()
        finish() // never left behind in the back stack
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        finish()
        return true
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
