package com.sadique.launcher;

import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;

/** Configurable TV glass surfaces. Optional window blur is managed by GlassSettings. */
final class TvGlassStyle {
    private TvGlassStyle() { }
    static boolean enabled(SharedPreferences prefs) { return ThemeStyle.id(prefs)!=ThemeStyle.CLASSIC; }
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
        return surface(radius, strokeWidth, focused, prefs, false, false);
    }
    static GradientDrawable liquid(int radius, int strokeWidth, SharedPreferences prefs) {
        return surface(radius, strokeWidth, false, prefs, true, false);
    }
    static GradientDrawable drawer(int radius, int strokeWidth, SharedPreferences prefs) {
        return surface(radius, strokeWidth, false, prefs, true, true);
    }
    static GradientDrawable drawerCard(int radius,int strokeWidth,boolean focused,SharedPreferences prefs) {
        return surface(radius,strokeWidth,focused,prefs,true,true);
    }
    static GradientDrawable header(int radius,int strokeWidth,SharedPreferences prefs,boolean frosted) {
        if(ThemeStyle.custom(prefs)) return new MaterialSurface(radius,strokeWidth,false,prefs,false,frosted?2:finish(prefs));
        int finish=frosted ? 2 : finish(prefs), intensity=intensity(prefs);
        if (enabled(prefs)) return surface(radius,strokeWidth,false,prefs,true,false,finish);
        GlassSurface shape=new GlassSurface(strokeWidth,edge(prefs));
        int alpha=GlassTransparency.tintAlpha(finish,intensity);
        shape.setColors(new int[]{
                (PanelTransparency.alpha(alpha,prefs,false)<<24) | (tinted(0xff132539,prefs)&0xffffff),
                (PanelTransparency.alpha(Math.max(0,alpha-12),prefs,false)<<24) | (tinted(0xff09182a,prefs)&0xffffff)});
        shape.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        shape.backdropOpacity(GlassTransparency.backdropAlpha(finish,intensity));
        shape.setCornerRadius(radius); return shape;
    }
    private static GradientDrawable surface(int radius, int strokeWidth, boolean focused, SharedPreferences prefs, boolean liquid, boolean drawer) {
        return surface(radius,strokeWidth,focused,prefs,liquid,drawer,finish(prefs));
    }
    private static GradientDrawable surface(int radius,int strokeWidth,boolean focused,SharedPreferences prefs,boolean liquid,boolean drawer,int finish) {
        if(ThemeStyle.custom(prefs)) return new MaterialSurface(radius,strokeWidth,focused,prefs,drawer,finish);
        float strength = intensity(prefs) / 100f;
        int base = finish == 1 ? 24 : finish == 2 ? 96 : 48;
        int alpha = base + Math.round((245 - base) * strength * strength);
        if (liquid) alpha = GlassTransparency.tintAlpha(finish,intensity(prefs));
        if (focused) alpha = Math.min(245, alpha + 30);
        int top = (PanelTransparency.alpha(alpha,prefs,drawer) << 24) | (tinted(focused ? 0xffaaa7b5 : 0xff797580, prefs) & 0xffffff);
        int middle = (PanelTransparency.alpha(Math.max(0, alpha - 28),prefs,drawer) << 24) | (tinted(0xff494451, prefs) & 0xffffff);
        int bottom = (PanelTransparency.alpha(Math.max(0, alpha - 16),prefs,drawer) << 24) | (tinted(0xff393541, prefs) & 0xffffff);
        int edge = edge(prefs);
        GlassSurface shape = new GlassSurface(strokeWidth, focused ? 0xccffffff : edge);
        if (liquid) shape.backdropOpacity(GlassTransparency.backdropAlpha(finish,intensity(prefs)));
        shape.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        shape.setColors(new int[]{top, middle, bottom});
        shape.setCornerRadius(radius);
        return shape;
    }
}



