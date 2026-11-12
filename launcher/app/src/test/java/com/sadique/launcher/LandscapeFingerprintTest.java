package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeFingerprintTest {
    @Test public void brightnessChangesPreserveTheImageFingerprint() {
        int[] image = new int[81];
        int[] brighter = new int[81];
        for (int i = 0; i < image.length; i++) {
            int value = 20 + (i * 31) % 175;
            image[i] = value * 0x010101;
            brighter[i] = (value + 30) * 0x010101;
        }
        long[] reference = LandscapeFingerprint.of(image);
        assertArrayEquals(reference, LandscapeFingerprint.of(brighter));
        assertTrue(LandscapeFingerprint.matches(reference, reference[0] ^ 0xF, reference[1] ^ 0xF));
        assertFalse(LandscapeFingerprint.matches(reference, reference[0] ^ 0xFF, reference[1] ^ 0xF));
    }
}
