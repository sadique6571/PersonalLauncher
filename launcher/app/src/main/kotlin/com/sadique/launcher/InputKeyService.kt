package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.util.Log

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
open class InputKeyService : AccessibilityService() {

    private var inputKeycodes = IntArray(0)
    private var appKeys: Map<Int, String> = emptyMap()
    private var appScancodes: Map<Int, String> = emptyMap()
    private var holdAppKeys: Map<Int, String> = emptyMap()
    private var holdAppScancodes: Map<Int, String> = emptyMap()
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private var repairRecord = false
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private var consumeVolumeKeys = false
    private var volumeKeyFilter = false
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter. */
    private var gamepads: GamepadPassthrough? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        val config = Config.load(this)
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices
        if (repairRecord) ServiceRepair.connected(this, javaClass)
        inputKeycodes = config.inputKeycodes
        consumeVolumeKeys = config.consumeVolumeKeys
        volumeKeyFilter = config.keyFilter
        VolumeSlider.observe(this)
        VolumeAutoStart.request(this)
        Log.d("PersonalVolume", "InputKeyService keyFilter=$volumeKeyFilter compatibility=$consumeVolumeKeys")
        appKeys = RemoteKeys.parseAppKeys(config.appKeys)
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys)
        RemoteKeys.applyKeyFilter(this, config.keyFilter)
        RemoteKeys.setDebug(config.remoteKeysDebug)
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys)
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys)
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = GamepadPassthrough(this)
            gamepads?.start()
        }
    }

    private fun isInputKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true
        for (code in inputKeycodes) {
            if (keyCode == code) return true
        }
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true
        if (volumeKeyFilter && VolumeKeys.control(this, event)) return true
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes[event.scanCode], holdAppScancodes[event.scanCode])) return true
        if (RemoteKeys.handle(this, event, appKeys[event.keyCode], holdAppKeys[event.keyCode])) return true
        if (!isInputKey(event.keyCode)) {
            RemoteKeys.debugUnmatched(event)
            return false
        }
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            try {
                startActivity(Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
        return true
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    override fun onUnbind(intent: Intent): Boolean {
        RemoteKeys.release(this)
        if (repairRecord) ServiceRepair.disconnected(this, javaClass)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        RemoteKeys.release(this)
        gamepads?.stop()
        if (consumeVolumeKeys || volumeKeyFilter) VolumeKeys.stop()
        VolumeSlider.release(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
    }

    override fun onInterrupt() {
    }

    companion object {
        private const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"
    }
}
