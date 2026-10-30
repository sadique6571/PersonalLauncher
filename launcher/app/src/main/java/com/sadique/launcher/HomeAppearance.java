package com.sadique.launcher;

/** Wallpaper framing and contrast rules, shared by Home and its settings cards. */
final class HomeAppearance {
    static float daylight(String mode, int minute) {
        return "light".equals(mode) ? 1f : "auto".equals(mode) ? PersonalHomeModel.daylight(minute) : 0f;
    }

    static int shade(String mode, int minute, int extra) {
        return Math.max(0, Math.min(150, Math.round(24 * (1 - daylight(mode, minute))) + Math.max(0, extra)));
    }

    // Portrait uses a landscape hero instead of magnifying a 16:9 photo to phone height.
    static float[] frame(int width, int height, int imageWidth, int imageHeight, float clockY, boolean mountains) {
        boolean portrait = height > width;
        float areaHeight = mountains && portrait ? Math.min(height, Math.max(width * .75f, clockY * 1.8f)) : height;
        float scale = Math.max(width / (float) imageWidth, areaHeight / imageHeight);
        float photoWidth = imageWidth * scale, photoHeight = imageHeight * scale;
        float left = (width - photoWidth) / 2f;
        float upper = mountains ? 0 : (height - photoHeight) / 2f;
        return new float[]{left, upper, left + photoWidth, upper + photoHeight};
    }
}
