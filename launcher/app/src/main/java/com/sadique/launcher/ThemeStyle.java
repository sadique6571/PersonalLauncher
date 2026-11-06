package com.sadique.launcher;

import android.content.SharedPreferences;

/** Stable style identifiers; legacy boolean installs retain exactly their selected style. */
final class ThemeStyle {
    static final int GLASS=0, CLASSIC=1, DUNE=2, PEARL=3, COBALT=4, SAGE=5;
    private static final String[] PREFIXES={"appearance.glass.","appearance.classic.","appearance.dune.","appearance.pearl.","appearance.cobalt.","appearance.sage."};
    private ThemeStyle() { }
    static int clamp(int style) { return Math.max(0,Math.min(5,style)); }
    static int id(SharedPreferences prefs) {
        return clamp(prefs.getInt("visual_style",prefs.getBoolean("tv_glass_style",true)?GLASS:CLASSIC));
    }
    static String prefix(int style) { return PREFIXES[clamp(style)]; }
    static boolean custom(SharedPreferences prefs) { return id(prefs)>=DUNE; }
    static boolean pearl(SharedPreferences prefs) { return id(prefs)==PEARL; }
    static boolean light(SharedPreferences prefs) { return pearl(prefs)||id(prefs)==SAGE; }
    static int foreground(SharedPreferences prefs) { return id(prefs)==SAGE?0xff0d2b1a:pearl(prefs)?0xff242d30:id(prefs)==DUNE?0xffffedd8:0xfff1f5fb; }
    static int muted(SharedPreferences prefs) { return id(prefs)==SAGE?0xff31513c:pearl(prefs)?0xff4e6264:id(prefs)==DUNE?0xffd3af8e:0xffa4bcd6; }
    static int accent(SharedPreferences prefs) {
        int fallback=id(prefs)==COBALT?0xffffd92f:id(prefs)==SAGE?0xff195c36:id(prefs)==DUNE?0xffedab75:id(prefs)==PEARL?0xff006d72:0xff40d9ff;
        switch(prefs.getInt("theme_name",0)) {
            case 1:return 0xffff9a5c;
            case 2:return light(prefs)?0xff69439c:0xffb388ff;
            case 3:return prefs.getInt("custom_accent",fallback);
            default:return fallback;
        }
    }
    static int baseTop(SharedPreferences prefs) {
        switch(id(prefs)) { case SAGE:return 0xffdce6be; case COBALT:return 0xff12275c; case PEARL:return 0xfff9f1e8; default:return 0xff26363a; }
    }
    static int baseBottom(SharedPreferences prefs) {
        switch(id(prefs)) { case SAGE:return 0xffc5d4ac; case COBALT:return 0xff07112d; case PEARL:return 0xffe9e4dc; default:return 0xff111e24; }
    }
    static void demoDefaults(SharedPreferences prefs) { demoDefaults(prefs,false); }
    static void demoDefaults(SharedPreferences prefs,boolean tv) {
        if(!custom(prefs)||prefs.getBoolean("demo_defaults_v1",false)) return;
        SharedPreferences.Editor e=prefs.edit().putBoolean("demo_defaults_v1",true).putInt("header_bg_mode",1)
                .putInt("tv_columns",4).putInt("phone_columns",2).putInt("avatar_shape",1).putInt("clock_size",tv?84:60);
        if(!prefs.contains("tile_corners")) e.putInt("tile_corners",id(prefs)==DUNE?0:1);
        if(!prefs.contains("continue_watching")) e.putBoolean("continue_watching",false);
        if(!prefs.contains("focus_glow")) e.putBoolean("focus_glow",false);
        e.apply();
    }
    static int focusAccent(SharedPreferences prefs) {
        int color=accent(prefs),background=light(prefs)?0xffe9e4dc:0xff111e24;
        double base=luminance(background);
        for(int step=0;step<=20;step++) {
            int adjusted=MaterialAppearance.mix(color,foreground(prefs),step/20f);
            double value=luminance(adjusted);
            if((Math.max(value,base)+.05)/(Math.min(value,base)+.05)>=3) return adjusted;
        }
        return foreground(prefs);
    }
    static boolean fixedTypography(SharedPreferences prefs) { return id(prefs)==GLASS; }
    static String fontFamily(SharedPreferences prefs) {
        return fixedTypography(prefs)?"sans-serif":new String[]{"sans-serif","serif","monospace","sans-serif-light"}[Math.max(0,Math.min(3,prefs.getInt("app_font",0)))];
    }
    static String clockFontFamily(SharedPreferences prefs) {
        return fixedTypography(prefs)?"sans-serif":new String[]{id(prefs)==DUNE?"sans-serif-light":"sans-serif","serif","monospace"}[Math.max(0,Math.min(2,prefs.getInt("clock_font",0)))];
    }
    static boolean clockBold(SharedPreferences prefs) { return id(prefs)!=DUNE||prefs.getInt("clock_font",0)!=0; }
    static int clockAlignment(SharedPreferences prefs) { return fixedTypography(prefs)?1:Math.max(0,Math.min(2,prefs.getInt("clock_align",1))); }
    static double luminance(int color) {
        double[] rgb={((color>>16)&255)/255d,((color>>8)&255)/255d,(color&255)/255d};
        for(int i=0;i<3;i++) rgb[i]=rgb[i]<=.04045?rgb[i]/12.92:Math.pow((rgb[i]+.055)/1.055,2.4);
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
    }
    static int wallpaperClockInk(int background) {
        double value=luminance(background),dark=luminance(0xff11191c);
        return (value+.05)/(dark+.05)>=1.05/(value+.05)?0xff11191c:0xffffffff;
    }
    static int contentColor(SharedPreferences prefs,int color) {
        if(!light(prefs)) return color;
        return luminance(color)>.4 ? ((color>>>24)<240?muted(prefs):foreground(prefs)) : color;
    }
}
