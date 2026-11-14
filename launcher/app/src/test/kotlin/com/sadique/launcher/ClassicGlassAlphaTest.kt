package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicGlassAlphaTest {
    @Test fun classicPanelAndCardCannotHideTheEntireWallpaper() {
        for (value in intArrayOf(30, 70, 100)) {
            val remaining = (1 - ClassicGlassAlpha.card(value, false) / 255.0) * (1 - ClassicGlassAlpha.panel(value) / 255.0) * 0.88
            assertTrue(remaining > 0.20)
        }
        assertEquals(0, ClassicGlassAlpha.panel(0))
        assertEquals(0, ClassicGlassAlpha.card(0, false))
    }

    @Test fun everyIntensityChangesBothLayersAndFocusRemainsStronger() {
        var previousCard = -1
        var previousPanel = -1
        for (value in intArrayOf(30, 70, 100)) {
            assertTrue(ClassicGlassAlpha.card(value, false) > previousCard)
            assertTrue(ClassicGlassAlpha.panel(value) > previousPanel)
            assertTrue(ClassicGlassAlpha.card(value, true) > ClassicGlassAlpha.card(value, false))
            assertTrue(ClassicGlassAlpha.card(value, true) < 255)
            previousCard = ClassicGlassAlpha.card(value, false)
            previousPanel = ClassicGlassAlpha.panel(value)
        }
        assertTrue(ClassicGlassAlpha.drawer(30) < ClassicGlassAlpha.drawer(100))
    }
}
