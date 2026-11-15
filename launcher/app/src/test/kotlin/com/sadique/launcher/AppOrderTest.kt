package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    @Test fun mergeRetainsSavedOrderRemovesMissingAndAppendsNewApps() {
        assertEquals(listOf("c", "a", "b", "d"), AppOrder.merge(listOf("a", "b", "c", "d"), listOf("gone", "c", "a", "c")))
    }

    @Test fun movingInEitherDirectionPreservesOtherApps() {
        assertEquals(listOf("c", "a", "b"), AppOrder.move(listOf("a", "b", "c"), "c", 0))
        assertEquals(listOf("b", "c", "a"), AppOrder.move(listOf("a", "b", "c"), "a", 2))
        assertEquals(listOf("a", "b"), AppOrder.move(listOf("a", "b"), "missing", 0))
    }
}
