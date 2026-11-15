package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadPassthroughTest {
    // Sources as dumpsys input reports them on a TCL G07.
    @Test fun xboxControllerIsAGamepad() {
        assertTrue(GamepadPassthrough.isGamepad(0x01000511))
    }

    @Test fun voiceRemoteWithJoystickIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x01000311))
    }

    @Test fun virtualSearchKeyboardIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x00000701))
    }

    @Test fun keypadIsNot() {
        assertFalse(GamepadPassthrough.isGamepad(0x00000101))
    }
}
