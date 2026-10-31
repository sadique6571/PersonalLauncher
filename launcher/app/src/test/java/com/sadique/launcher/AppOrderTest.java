package com.sadique.launcher;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AppOrderTest {
    @Test public void mergeRetainsSavedOrderRemovesMissingAndAppendsNewApps() {
        assertEquals(List.of("c", "a", "b", "d"), AppOrder.merge(List.of("a", "b", "c", "d"), List.of("gone", "c", "a", "c")));
    }
    @Test public void movingInEitherDirectionPreservesOtherApps() {
        assertEquals(List.of("c", "a", "b"), AppOrder.move(List.of("a", "b", "c"), "c", 0));
        assertEquals(List.of("b", "c", "a"), AppOrder.move(List.of("a", "b", "c"), "a", 2));
        assertEquals(List.of("a", "b"), AppOrder.move(List.of("a", "b"), "missing", 0));
    }
}
