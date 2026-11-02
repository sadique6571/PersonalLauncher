package com.sadique.launcher;
import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;
public class RemoteSetupTest {
    @Test public void protectsNavigationPowerAndDigits() {
        for (int code : new int[]{KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_POWER,
                KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT})
            assertFalse(RemoteSetup.allowed(code));
        for (int code = KeyEvent.KEYCODE_0; code <= KeyEvent.KEYCODE_9; code++) assertFalse(RemoteSetup.allowed(code));
        for (int code = KeyEvent.KEYCODE_NUMPAD_0; code <= KeyEvent.KEYCODE_NUMPAD_9; code++) assertFalse(RemoteSetup.allowed(code));
    }
    @Test public void acceptsSettingsAndDotAsSeparateKeycodes() {
        assertTrue(RemoteSetup.allowed(83)); assertTrue(RemoteSetup.allowed(76)); assertFalse(RemoteSetup.allowed(0));
    }
}
