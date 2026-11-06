package com.sadique.launcher;

import android.content.SharedPreferences;

/** Stable style identifiers; legacy boolean installs retain exactly their selected style. */
final class ThemeStyle {
    static final int GLASS=0, CLASSIC=1, DUNE=2, PEARL=3;
    private ThemeStyle() { }
    static int clamp(int style) { return Math.max(0,Math.min(3,style)); }
    static int id(SharedPreferences prefs) {
        return clamp(prefs.getInt("visual_style",prefs.getBoolean("tv_glass_style",true)?GLASS:CLASSIC));
    }
    static String prefix(int style) { return new String[]{"appearance.glass.","appearance.classic.","appearance.dune.","appearance.pearl."}[clamp(style)]; }
    static boolean custom(SharedPreferences prefs) { return id(prefs)>=DUNE; }
    static boolean pearl(SharedPreferences prefs) { return id(prefs)==PEARL; }
    static int foreground(SharedPreferences prefs) { return pearl(prefs)?0xff242d30:id(prefs)==DUNE?0xffffedd8:0xfff1f5fb; }
    static int muted(SharedPreferences prefs) { return pearl(prefs)?0xff4e6264:id(prefs)==DUNE?0xffd3af8e:0xffa4bcd6; }
    static int accent(SharedPreferences prefs) {
        int fallback=id(prefs)==DUNE?0xffedab75:id(prefs)==PEARL?0xff006d72:0xff40d9ff;
        switch(prefs.getInt("theme_name",0)) {
            case 1:return 0xffff9a5c;
            case 2:return pearl(prefs)?0xff69439c:0xffb388ff;
            case 3:return prefs.getInt("custom_accent",fallback);
            default:return fallback;
        }
    }
    static int focusAccent(SharedPreferences prefs) {
        int color=accent(prefs),background=pearl(prefs)?0xffe9e4dc:0xff111e24;
        double a=luminance(color),b=luminance(background);
        return (Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=3?color:foreground(prefs);
    }
    static double luminance(int color) {
        double[] rgb={((color>>16)&255)/255d,((color>>8)&255)/255d,(color&255)/255d};
        for(int i=0;i<3;i++) rgb[i]=rgb[i]<=.04045?rgb[i]/12.92:Math.pow((rgb[i]+.055)/1.055,2.4);
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
    }
    static int contentColor(SharedPreferences prefs,int color) {
        if(!pearl(prefs)) return color;
        return luminance(color)>.4 ? ((color>>>24)<240?muted(prefs):foreground(prefs)) : color;
    }
}
