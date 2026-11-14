package com.sadique.launcher

import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.SpringFrames

import org.junit.Test

import org.junit.Assert.assertTrue

/** Reduced motion's spring: no bounce, and settled within 5 frames at 60 Hz for any move on a 1080p screen. */
class MotionTest {

    @Test
    fun reducedSpringSettlesWithinFiveFrames() {
        for (px in floatArrayOf(50f, 150f, 300f, 600f, 1000f, 1920f)) {
            val frames = SpringFrames.toSettle(Motion.REDUCED_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY,
                    Motion.REDUCED_MIN_CHANGE, px)
            assertTrue("$px px took $frames frames", frames <= 5)
        }
    }

    @Test
    fun reducedAnimationsLastAtMost100ms() {
        assertTrue(Motion.REDUCED_MS <= 100)
    }
}
