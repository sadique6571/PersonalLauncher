package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class LauncherSleepTest {
    @Test public void timerCannotRunEarlyOrAfterReboot() {
        assertFalse(LauncherSleep.due(1000, 999, 4, 4));
        assertTrue(LauncherSleep.due(1000, 1000, 4, 4));
        assertTrue(LauncherSleep.due(1000, 1400, 4, 4));
        assertFalse(LauncherSleep.due(1000, 1400, 4, 5));
        assertFalse(LauncherSleep.due(1000, 1400, -1, -1));
        assertFalse(LauncherSleep.due(0, 1400, 4, 4));
    }
}
