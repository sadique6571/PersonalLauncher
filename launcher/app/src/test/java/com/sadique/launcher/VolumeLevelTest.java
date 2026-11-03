package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeLevelTest {
    @Test public void muteEmptiesBarWithoutOverwritingRememberedLevel() {
        assertEquals(.6f, VolumeLevel.fraction(60,100,false), .001f);
        assertEquals(0f, VolumeLevel.fraction(60,100,true), .001f);
        assertEquals(0, VolumeLevel.percent(60,100,true));
        assertEquals(60, VolumeLevel.percent(60,100,false));
    }
    @Test public void stepDownShrinksBottomFillAndMaximumIsFull() {
        assertTrue(VolumeLevel.fraction(59,100,false) < VolumeLevel.fraction(60,100,false));
        assertEquals(1f, VolumeLevel.fraction(100,100,false), 0f);
        assertEquals(0f, VolumeLevel.fraction(0,100,false), 0f);
    }
    @Test public void invalidFirmwareValuesStayWithinTrack() {
        assertEquals(0f, VolumeLevel.fraction(30,0,false), 0f);
        assertEquals(0f, VolumeLevel.fraction(-1,100,false), 0f);
        assertEquals(1f, VolumeLevel.fraction(200,100,false), 0f);
    }
}
