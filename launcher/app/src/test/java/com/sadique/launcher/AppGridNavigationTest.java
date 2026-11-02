package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppGridNavigationTest {
    @Test public void horizontalTravelCrossesRowsInBothDirections() {
        assertEquals(4, AppGridNavigation.horizontal(3, 10, true));
        assertEquals(3, AppGridNavigation.horizontal(4, 10, false));
        int position = 9;
        for (int i = 8; i >= 0; i--) { position = AppGridNavigation.horizontal(position, 10, false); assertEquals(i, position); }
    }
    @Test public void boundariesDoNotEscapeAndEmptyOrInvalidPositionsAreRejected() {
        assertEquals(0, AppGridNavigation.horizontal(0, 10, false));
        assertEquals(9, AppGridNavigation.horizontal(9, 10, true));
        assertEquals(0, AppGridNavigation.horizontal(0, 1, true));
        assertEquals(-1, AppGridNavigation.horizontal(0, 0, true));
        assertEquals(-1, AppGridNavigation.horizontal(-1, 10, true));
        assertEquals(-1, AppGridNavigation.horizontal(10, 10, false));
    }
}
