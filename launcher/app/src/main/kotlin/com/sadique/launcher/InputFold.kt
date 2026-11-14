package com.sadique.launcher

import android.media.tv.TvInputManager

/** Which Inputs menu rows inputs_connected_only folds behind "More inputs". */
object InputFold {
    @JvmStatic
    fun folds(hdmi: Boolean, state: Int): Boolean {
        if (!hdmi) return true
        return state != -1 && state != TvInputManager.INPUT_STATE_CONNECTED
    }
}
