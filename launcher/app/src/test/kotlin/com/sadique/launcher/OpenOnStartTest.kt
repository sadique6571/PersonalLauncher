package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class OpenOnStartTest {

    private companion object {
        private const val SELF = "com.sadique.launcher"
        private const val YOUTUBE = "com.google.android.youtube.tv"
    }

    @Test fun firstHomeOfANewBootOpensTheApp() {
        assertTrue(OpenOnStart.due(12, 11, YOUTUBE, SELF))
        assertTrue(OpenOnStart.due(0, -1, YOUTUBE, SELF))
    }

    @Test fun laterHomeOfTheSameBootStaysHome() {
        assertFalse(OpenOnStart.due(12, 12, YOUTUBE, SELF))
    }

    @Test fun unknownBootCountStaysHome() {
        assertFalse(OpenOnStart.due(-1, -1, YOUTUBE, SELF))
    }

    @Test fun offStaysHome() {
        assertFalse(OpenOnStart.due(12, 11, "", SELF))
        assertFalse(OpenOnStart.due(12, 11, null, SELF))
    }

    @Test fun theLauncherItselfIsNeverOpened() {
        assertFalse(OpenOnStart.due(12, 11, SELF, SELF))
    }

    @Test fun onlineTheAppOpensAtOnce() {
        assertTrue(OpenOnStart.opensNow(true, 1_000L, 60_000L))
    }

    @Test fun offlineTheAppWaitsUntilTheEndOfTheWait() {
        assertFalse(OpenOnStart.opensNow(false, 59_999L, 60_000L))
        assertTrue(OpenOnStart.opensNow(false, 60_000L, 60_000L))
    }
}
