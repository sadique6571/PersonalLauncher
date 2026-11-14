package com.sadique.launcher

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

/** Manual preview. Android's screensaver picker uses [LandscapeDream]. */
class LandscapeActivity : Activity() {
    private lateinit var landscape: LandscapeView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        landscape = LandscapeView(this).apply { setOnClickListener { finish() } }
        setContentView(landscape)
    }

    override fun onResume() {
        super.onResume()
        landscape.start()
    }

    override fun onPause() {
        landscape.stop()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
