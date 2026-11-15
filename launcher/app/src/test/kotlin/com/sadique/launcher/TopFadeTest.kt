package com.sadique.launcher

import org.junit.Test

import org.junit.Assert.assertEquals

/** Plain JVM tests for fade_rows: how tall the fade at the grid's top edge is for a given scroll. */
class TopFadeTest {

    @Test
    fun theDockAtRestIsUntouched() {
        assertEquals(0f, TopFade.height(0, 72f), 0f)
        assertEquals(0f, TopFade.height(-5, 72f), 0f)   // an overscroll never fades anything
    }

    @Test
    fun theFadeGrowsInWithTheFirstPixelsOfScrolling() {
        assertEquals(10f, TopFade.height(10, 72f), 0f)
        assertEquals(71f, TopFade.height(71, 72f), 0f)
    }

    @Test
    fun thenItStaysAtItsFullLength() {
        assertEquals(72f, TopFade.height(72, 72f), 0f)
        assertEquals(72f, TopFade.height(5000, 72f), 0f)
    }
}
