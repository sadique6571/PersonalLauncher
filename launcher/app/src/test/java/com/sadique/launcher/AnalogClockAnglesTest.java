package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalogClockAnglesTest {
    @Test public void secondsMoveBetweenTicks(){
        assertEquals(61.5f,AnalogClockAngles.seconds(10,250),.0001f);
        assertTrue(AnalogClockAngles.seconds(10,33)>AnalogClockAngles.seconds(10,0));
        assertEquals(.006f,360-AnalogClockAngles.seconds(59,999),.0001f);
        assertEquals(0,AnalogClockAngles.seconds(0,0),0);
    }
    @Test public void minuteAndHourHandsAlsoSweep(){
        assertEquals(3.05f,AnalogClockAngles.minutes(0,30,500),.0001f);
        assertEquals(97.75417f,AnalogClockAngles.hours(3,15,30,500),.0001f);
        assertEquals(AnalogClockAngles.hours(0,0,0,0),AnalogClockAngles.hours(12,0,0,0),0);
    }
}
