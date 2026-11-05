package com.sadique.launcher;

/** Both glass layers must let the original wallpaper through; a cached blur is never an opaque replacement. */
final class GlassTransparency {
    private GlassTransparency() { }
    private static int strength(int intensity) { return Math.max(0,Math.min(100,intensity)); }
    static int backdropAlpha(int finish,int intensity) {
        int maximum=finish==1 ? 96 : finish==2 ? 210 : 150;
        return Math.round(maximum*strength(intensity)/100f);
    }
    static int tintAlpha(int finish,int intensity) {
        int maximum=finish==1 ? 36 : finish==2 ? 112 : 72;
        return Math.round(maximum*strength(intensity)/100f);
    }
    static int blurRadius(int finish,int intensity) {
        if (finish==1 || strength(intensity)==0) return 0;
        return finish==2 ? (strength(intensity)<50 ? 2 : 4) : (strength(intensity)<50 ? 1 : 2);
    }
}
