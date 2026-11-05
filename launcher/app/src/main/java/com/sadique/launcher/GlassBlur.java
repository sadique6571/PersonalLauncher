package com.sadique.launcher;

/** Three separable box passes on a bounded thumbnail; never run for each rendered frame. */
final class GlassBlur {
    private GlassBlur() { }
    static void blur(int[] pixels, int width, int height, int radius) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length || radius < 0 || radius > 32)
            throw new IllegalArgumentException("Invalid blur bounds");
        if (radius == 0) return;
        int[] scratch = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            line(pixels, scratch, width, height, radius, false);
            line(scratch, pixels, height, width, radius, true);
        }
    }
    private static void line(int[] source, int[] out, int length, int lines, int radius, boolean vertical) {
        int stride = vertical ? lines : 1, lineStride = vertical ? 1 : length;
        int count = radius * 2 + 1;
        for (int row = 0; row < lines; row++) {
            int start = row * lineStride, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int color = source[start + Math.max(0, Math.min(length - 1, i)) * stride];
                r += (color >> 16) & 255; g += (color >> 8) & 255; b += color & 255;
            }
            for (int x = 0; x < length; x++) {
                out[start + x * stride] = 0xff000000 | (r / count << 16) | (g / count << 8) | b / count;
                int old = source[start + Math.max(0, x - radius) * stride];
                int next = source[start + Math.min(length - 1, x + radius + 1) * stride];
                r += ((next >> 16) & 255) - ((old >> 16) & 255);
                g += ((next >> 8) & 255) - ((old >> 8) & 255); b += (next & 255) - (old & 255);
            }
        }
    }
}
