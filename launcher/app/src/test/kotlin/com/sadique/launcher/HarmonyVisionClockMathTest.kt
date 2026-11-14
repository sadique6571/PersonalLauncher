package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonyVisionClockMathTest {
    @Test fun secondHandSweepsContinuouslyAndQuartzSteps() {
        assertEquals(3.0, HarmonyVisionClockMath.secondAngle(0, 500, true), 0.00001)
        assertEquals(0.0, HarmonyVisionClockMath.secondAngle(0, 500, false), 0.00001)
        assertEquals(359.994, HarmonyVisionClockMath.secondAngle(59, 999, true), 0.001)
    }

    @Test fun minuteAndHourTrackElapsedSecondsWithoutJumps() {
        assertEquals(180.0, HarmonyVisionClockMath.hourAngle(6, 0, 0, 0), 0.00001)
        assertEquals(270.0, HarmonyVisionClockMath.hourAngle(9, 0, 0, 0), 0.00001)
        assertEquals(3.0, HarmonyVisionClockMath.minuteAngle(0, 30, 0), 0.00001)
        assertEquals(15.0, HarmonyVisionClockMath.hourAngle(12, 30, 0, 0), 0.00001)
        assertTrue(HarmonyVisionClockMath.hourAngle(12, 31, 0, 0) > 15)
    }

    @Test fun faceIdsAreBounded() {
        assertEquals(0, HarmonyVisionClockMath.clampFace(-1))
        assertEquals(1, HarmonyVisionClockMath.clampFace(1))
        assertEquals(2, HarmonyVisionClockMath.clampFace(99))
    }
}
