package com.sadique.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent

/**
 * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) opens the same
 * thing HomeKeyService would. While that service runs it consumes the key first; without it (not enabled,
 * or switched off by a force-stop) the stock launcher that used to handle the key is gone, and the button did
 * nothing even on Home.
 */
class MainSettingsKey(
    private val context: Context,
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private val keycodes: IntArray,
    private val keyPanel: Boolean,
    private val open: (Intent) -> Unit,
) {
    fun handle(event: KeyEvent): Boolean {
        for (code in keycodes) {
            if (event.keyCode != code) continue
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                open(if (keyPanel) Intent(context, QuickSettingsActivity::class.java)
                        else SystemSettings.intent(context, Settings.ACTION_SETTINGS))
            }
            return true
        }
        return false
    }
}
