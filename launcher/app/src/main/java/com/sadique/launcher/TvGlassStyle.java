package com.sadique.launcher;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/** Configurable TV glass surfaces. Optional window blur is managed by GlassSettings. */
final class TvGlassStyle {
    private TvGlassStyle() { }
    static boolean enabled(SharedPreferences prefs) { return prefs.getBoolean("tv_glass_style", true); }
    static int finish(SharedPreferences prefs) { return Math.max(0, Math.min(2, prefs.getInt("glass_finish", 0))); }
    static int intensity(SharedPreferences prefs) { return Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70))); }
    static int edge(SharedPreferences prefs) {
        return new int[]{0x00ffffff, 0x55ffffff, 0x99ffffff}[Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)))];
    }
    static GradientDrawable surface(int radius, int strokeWidth, boolean focused, SharedPreferences prefs) {
        int finish = finish(prefs);
        int top = focused ? 0xff596575 : (finish == 2 ? 0xff596575 : 0xff2d3541);
        int bottom = focused ? 0xff374455 : (finish == 2 ? 0xff344152 : 0xff18202d);
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        shape.setCornerRadius(radius);
        shape.setStroke(strokeWidth, focused ? 0xffedf5ff : edge(prefs));
        int base = finish == 1 ? 130 : (finish == 2 ? 185 : 180);
        int range = 255 - base;
        shape.setAlpha(base + intensity(prefs) * range / 100);
        return shape;
    }
}
