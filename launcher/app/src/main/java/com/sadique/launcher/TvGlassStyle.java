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
    static int tinted(int base, SharedPreferences prefs) {
        int theme = prefs.getInt("theme_name", 0);
        int accent = theme == 1 ? 0xffff9a5c : theme == 2 ? 0xffb388ff : theme == 3 ? prefs.getInt("custom_accent", 0xff40d9ff) : 0xff40d9ff;
        float mix = theme == 3 && (accent & 0x00ffffff) == 0 ? .55f : .12f;
        return android.graphics.Color.rgb(
                Math.round(android.graphics.Color.red(base) * (1 - mix) + android.graphics.Color.red(accent) * mix),
                Math.round(android.graphics.Color.green(base) * (1 - mix) + android.graphics.Color.green(accent) * mix),
                Math.round(android.graphics.Color.blue(base) * (1 - mix) + android.graphics.Color.blue(accent) * mix));
    }
    static int readable(int color) {
        return android.graphics.Color.luminance(color) < .15 ? 0xffdce5f2 : color;
    }
    static GradientDrawable surface(int radius, int strokeWidth, boolean focused, SharedPreferences prefs) {
        int finish = finish(prefs);
        int top = focused ? 0xff596575 : (finish == 2 ? 0xff596575 : 0xff2d3541);
        int bottom = focused ? 0xff374455 : (finish == 2 ? 0xff344152 : 0xff18202d);
        top = tinted(top, prefs); bottom = tinted(bottom, prefs);
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        shape.setCornerRadius(radius);
        shape.setStroke(strokeWidth, focused ? 0xffedf5ff : edge(prefs));
        int base = finish == 1 ? 130 : (finish == 2 ? 185 : 180);
        int range = 255 - base;
        shape.setAlpha(base + intensity(prefs) * range / 100);
        return shape;
    }
}
