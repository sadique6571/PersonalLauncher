package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HarmonyVisionClockMathTest {
    @Test public void secondHandSweepsContinuouslyAndQuartzSteps(){
        assertEquals(3d,HarmonyVisionClockMath.secondAngle(0,500,true),.00001);
        assertEquals(0d,HarmonyVisionClockMath.secondAngle(0,500,false),.00001);
        assertEquals(359.994d,HarmonyVisionClockMath.secondAngle(59,999,true),.001);
    }
    @Test public void minuteAndHourTrackElapsedSecondsWithoutJumps(){
        assertEquals(180d,HarmonyVisionClockMath.hourAngle(6,0,0,0),.00001);
        assertEquals(270d,HarmonyVisionClockMath.hourAngle(9,0,0,0),.00001);
        assertEquals(3d,HarmonyVisionClockMath.minuteAngle(0,30,0),.00001);
        assertEquals(15d,HarmonyVisionClockMath.hourAngle(12,30,0,0),.00001);
        assertTrue(HarmonyVisionClockMath.hourAngle(12,31,0,0)>15);
    }
    @Test public void faceIdsAreBounded(){
        assertEquals(0,HarmonyVisionClockMath.clampFace(-1));
        assertEquals(1,HarmonyVisionClockMath.clampFace(1));
        assertEquals(3,HarmonyVisionClockMath.clampFace(99));
    }
}
