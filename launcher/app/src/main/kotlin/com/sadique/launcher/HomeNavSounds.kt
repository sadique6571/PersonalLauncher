package com.sadique.launcher

import android.app.Activity
import android.media.AudioManager
import android.view.KeyEvent

/** System navigation sounds: tick on arrows, click on OK. Pure move from PersonalHomeActivity. */
internal object HomeNavSounds {
    fun play(activity: Activity, keyCode: Int) {
        val audio = activity.getSystemService(AudioManager::class.java) ?: return
        var fx = -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> fx = AudioManager.FX_FOCUS_NAVIGATION_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> fx = AudioManager.FX_KEY_CLICK
        }
        if (fx != -1) audio.playSoundEffect(fx)
    }
}
