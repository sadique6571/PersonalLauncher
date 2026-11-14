package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice

/**
 * Stops HomeKeyService and InputKeyService filtering keys while a game controller is connected. With key filtering on,
 * Android routes every key through the accessibility layer and re-injects the ones the service passes on, and games
 * (Steam Link with an Xbox controller, on a TCL G07) stop seeing the controller's buttons as the controller's. The
 * remote buttons the services handle do nothing until the controller disconnects; filtering then comes back.
 * Off by default (gamepad_passthrough). Started only with key_filter on too: with it off the services never filter,
 * and this must not turn filtering back on.
 */
internal class GamepadPassthrough(private val service: AccessibilityService) : InputManager.InputDeviceListener {
    private val input: InputManager = service.getSystemService(Context.INPUT_SERVICE) as InputManager

    fun start() {
        input.registerInputDeviceListener(this, null)
        update()
    }

    fun stop() {
        input.unregisterInputDeviceListener(this)
    }

    private fun gamepadConnected(): Boolean {
        for (id in input.inputDeviceIds) {
            val device = input.getInputDevice(id)
            if (device != null && !device.isVirtual && isGamepad(device.sources)) return true
        }
        return false
    }

    private fun update() {
        val info = service.serviceInfo ?: return
        val filter = !gamepadConnected()
        val filtering = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
        if (filter == filtering) return
        // Without the flag no more keys come, the UP of a button held right now included.
        if (!filter) RemoteKeys.release(service)
        info.flags = info.flags xor AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        service.setServiceInfo(info)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        update()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        update()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        update()
    }

    companion object {
        /**
         * A gamepad has all the SOURCE_GAMEPAD bits and the SOURCE_JOYSTICK ones, as dumpsys input reports them on a TCL
         * G07. Either alone is not enough: the Bluetooth voice remote reports a joystick (BT_RC833_U1: 0x01000311), and
         * the firmware's virtual-search keyboard, which isVirtual() misses, reports a gamepad (0x00000701). An Xbox
         * controller reports both (0x01000511).
         */
        @JvmStatic
        fun isGamepad(sources: Int): Boolean {
            return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    && (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
    }
}
