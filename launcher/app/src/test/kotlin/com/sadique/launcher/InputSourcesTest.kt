package com.sadique.launcher

import java.util.Arrays
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputSourcesTest {
    @Test fun mapsTheLastInputComponent() {
        assertEquals(Integer.valueOf(25), InputSources.mapped(
            Collections.singletonList("HW7=25"), "service/.Hdmi/HW7"))
    }

    @Test fun trimsMappingWhitespace() {
        assertEquals(Integer.valueOf(25), InputSources.mapped(
            Collections.singletonList(" HW7 = 25 "), "service/.Hdmi/HW7"))
    }

    @Test fun aDashHidesAnInput() {
        assertEquals(Integer.valueOf(-1), InputSources.mapped(
            Collections.singletonList("HW8=-"), "service/.Hdmi/HW8"))
    }

    @Test fun anUnknownOrMalformedInputIsNotMapped() {
        assertNull(InputSources.mapped(Arrays.asList("HW5=23", "HW7=nope"), "service/.Hdmi/HW6"))
        assertNull(InputSources.mapped(Collections.singletonList("HW7=nope"), "service/.Hdmi/HW7"))
        assertNull(InputSources.mapped(null, "service/.Hdmi/HW7"))
    }
}
