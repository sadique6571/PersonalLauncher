package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceCacheTest {
    @Test fun lightThemeSwitchInvalidatesClockEvenWithTheSameWallpaper() {
        val photo = Any()
        val key = ClockSampleKey(photo, 20, 30, 0, 0xff242d30.toInt())
        assertTrue(key.matches(photo, 20, 30, 0, 0xff242d30.toInt()))
        assertFalse(key.matches(photo, 20, 30, 0, 0xff0d2b1a.toInt()))
        assertFalse(key.matches(Any(), 20, 30, 0, 0xff242d30.toInt()))
        assertFalse(key.matches(photo, 21, 30, 0, 0xff242d30.toInt()))
        assertFalse(key.matches(photo, 20, 30, 10, 0xff242d30.toInt()))
    }

    @Test fun oldArtworkCompletionCannotClearNewThemeRequest() {
        val request = ArtworkRequestState()
        request.begin(1)
        assertTrue(request.pending(1))
        assertFalse(request.pending(2))
        request.begin(2)
        assertFalse(request.finish(1))
        assertTrue(request.pending(2))
        assertTrue(request.finish(2))
        assertFalse(request.pending(2))
        assertFalse(request.finish(2))
    }

    @Test fun appRescanCanRetryAnUnfinishedOlderRequest() {
        val request = ArtworkRequestState()
        request.begin(5)
        assertFalse(request.pending(6))
        request.begin(6)
        assertFalse(request.finish(5))
        assertTrue(request.finish(6))
    }
}
