package com.sadique.launcher;

/** Difference hashes from horizontal and vertical luminance changes in a 9×9 image sample. */
final class LandscapeFingerprint {
    static final int SIZE = 9;

    private LandscapeFingerprint() { }

    static long[] of(int[] pixels) {
        long horizontal = 0;
        long vertical = 0;
        for (int y = 0; y < SIZE - 1; y++) {
            for (int x = 0; x < SIZE - 1; x++) {
                int i = y * SIZE + x;
                int value = luminance(pixels[i]);
                horizontal = (horizontal << 1) | (value < luminance(pixels[i + 1]) ? 1 : 0);
                vertical = (vertical << 1) | (value < luminance(pixels[i + SIZE]) ? 1 : 0);
            }
        }
        return new long[]{horizontal, vertical};
    }

    static boolean matches(long[] candidate, long horizontal, long vertical) {
        return Long.bitCount(candidate[0] ^ horizontal) + Long.bitCount(candidate[1] ^ vertical) <= 8;
    }

    private static int luminance(int pixel) {
        return 299 * ((pixel >> 16) & 255) + 587 * ((pixel >> 8) & 255) + 114 * (pixel & 255);
    }
}
