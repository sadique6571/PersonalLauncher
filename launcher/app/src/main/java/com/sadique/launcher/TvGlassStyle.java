package com.sadique.launcher;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/** Neutral, lightweight TV surfaces: gradients and outlines, without backdrop blur. */
final class TvGlassStyle {
    private TvGlassStyle() { }
    static boolean enabled(SharedPreferences prefs) { return prefs.getBoolean("tv_glass_style", true); }
    static GradientDrawable surface(int radius, int strokeWidth, boolean focused, int intensity) {
        int top = focused ? 0xff596575 : 0xff2d3541;
        int bottom = focused ? 0xff374455 : 0xff18202d;
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        shape.setCornerRadius(radius);
        shape.setStroke(strokeWidth, focused ? 0xffedf5ff : 0x55ffffff);
        shape.setAlpha(Math.max(180, Math.min(255, 180 + intensity * 75 / 100)));
        return shape;
    }
}
