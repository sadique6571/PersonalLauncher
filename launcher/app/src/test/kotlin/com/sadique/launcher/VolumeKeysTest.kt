package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class VolumeKeysTest {

    private companion object {
        // Android's values, written out: android.view.KeyEvent and AudioManager aren't available in JVM tests.
        private const val KEYCODE_VOLUME_UP = 24
        private const val KEYCODE_VOLUME_DOWN = 25
        private const val KEYCODE_VOLUME_MUTE = 164
        private const val KEYCODE_DPAD_UP = 19
        private const val ADJUST_RAISE = 1
        private const val ADJUST_LOWER = -1
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1
    }

    @Test
    fun volumeUpAndDownAreHandled() {
        assertEquals(ADJUST_RAISE, VolumeKeys.direction(KEYCODE_VOLUME_UP))
        assertEquals(ADJUST_LOWER, VolumeKeys.direction(KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun otherKeysPassThrough() {
        // Mute is one press with nothing to repeat, and the firmware did not double it.
        assertEquals(0, VolumeKeys.direction(KEYCODE_VOLUME_MUTE))
        assertEquals(0, VolumeKeys.direction(KEYCODE_DPAD_UP))
    }

    @Test
    fun onlyTheFirstDownAndTheUpChangeThePress() {
        assertTrue(VolumeKeys.changesPress(ACTION_DOWN, 0))
        assertTrue(VolumeKeys.changesPress(ACTION_UP, 0))
        // A remote that sends its own repeats must not restart the 500 ms delay on every one.
        assertFalse(VolumeKeys.changesPress(ACTION_DOWN, 1))
        assertFalse(VolumeKeys.changesPress(ACTION_DOWN, 12))
    }
}
