package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateProgressTest {
    @Test public void reservesCompletionForVerifiedApk() {
        assertEquals(50, UpdateProgress.percent(500, 1000, false));
        assertEquals(99, UpdateProgress.percent(1000, 1000, false));
        assertEquals(99, UpdateProgress.percent(1500, 1000, false));
        assertEquals(100, UpdateProgress.percent(1000, 1000, true));
    }
    @Test public void handlesAbsentLengthAndEmptyTransfer() {
        assertEquals(0, UpdateProgress.percent(500, 0, false));
        assertEquals(0, UpdateProgress.percent(0, 1000, false));
        assertEquals(0, UpdateProgress.percent(-1, 1000, false));
    }
}
