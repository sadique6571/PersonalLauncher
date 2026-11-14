package com.sadique.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeFingerprintTest {
    @Test fun brightnessChangesPreserveTheImageFingerprint() {
        val image = IntArray(81)
        val brighter = IntArray(81)
        for (i in image.indices) {
            val value = 20 + (i * 31) % 175
            image[i] = value * 0x010101
            brighter[i] = (value + 30) * 0x010101
        }
        val reference = LandscapeFingerprint.of(image)
        assertArrayEquals(reference, LandscapeFingerprint.of(brighter))
        assertTrue(LandscapeFingerprint.matches(reference, reference[0] xor 0xF, reference[1] xor 0xF))
        assertFalse(LandscapeFingerprint.matches(reference, reference[0] xor 0xFF, reference[1] xor 0xF))
    }
}
