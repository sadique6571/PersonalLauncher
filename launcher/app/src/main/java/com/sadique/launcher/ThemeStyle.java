package com.sadique.launcher;

import android.content.SharedPreferences;

/** Stable style identifiers; legacy boolean installs retain exactly their selected style. */
final class ThemeStyle {
    static final int GLASS=0, CLASSIC=1, DUNE=2, PEARL=3, COBALT=4, SAGE=5, PINE=6, AMBER=7, HARMONY=8;
    private static final String[] PREFIXES={"appearance.glass.","appearance.classic.","appearance.dune.","appearance.pearl.","appearance.cobalt.","appearance.sage.","appearance.pine.","appearance.amber.","appearance.harmony."};
    static final int COUNT=PREFIXES.length;
    private ThemeStyle() { }
    static int clamp(int style) { return Math.max(0,Math.min(COUNT-1,style)); }
    static int id(SharedPreferences prefs) {
        return clamp(prefs.getInt("visual_style",prefs.getBoolean("tv_glass_style",true)?GLASS:CLASSIC));
    }
    static String prefix(int style) { return PREFIXES[clamp(style)]; }
    static boolean harmony(SharedPreferences prefs) { return id(prefs)==HARMONY; }
    static boolean custom(SharedPreferences prefs) { return id(prefs)>=DUNE && !harmony(prefs); }
    static boolean pearl(SharedPreferences prefs) { return id(prefs)==PEARL; }
    static boolean light(SharedPreferences prefs) { return pearl(prefs)||id(prefs)==SAGE; }
    static int foreground(SharedPreferences prefs) { return id(prefs)==SAGE?0xff0d2b1a:pearl(prefs)?0xff242d30:id(prefs)==DUNE?0xffffedd8:id(prefs)==PINE?0xffeafff2:id(prefs)==AMBER?0xfffff3dd:id(prefs)==HARMONY?0xfff8fbff:0xfff1f5fb; }
    static int muted(SharedPreferences prefs) { return id(prefs)==SAGE?0xff31513c:pearl(prefs)?0xff4e6264:id(prefs)==DUNE?0xffd3af8e:id(prefs)==PINE?0xffa8cbb8:id(prefs)==AMBER?0xffd8b98a:id(prefs)==HARMONY?0xffc6daeb:0xffa4bcd6; }
    static int headerTitleColor(SharedPreferences prefs) {
        return id(prefs)==DUNE?focusAccent(prefs):id(prefs)==COBALT?0xffd5e2ff:foreground(prefs);
    }
    static int accent(SharedPreferences prefs) {
        int fallback=id(prefs)==COBALT?0xffffd92f:id(prefs)==SAGE?0xff195c36:id(prefs)==DUNE?0xffedab75:id(prefs)==PEARL?0xff006d72:id(prefs)==PINE?0xff8fd0b2:id(prefs)==AMBER?0xfff5b942:id(prefs)==HARMONY?0xff8bd9ff:0xff40d9ff;
        switch(prefs.getInt("theme_name",0)) {
            case 1:return 0xffff9a5c;
            case 2:return light(prefs)?0xff69439c:0xffb388ff;
            case 3:return prefs.getInt("custom_accent",fallback);
            default:return fallback;
        }
    }
    static int baseTop(SharedPreferences prefs) {
        switch(id(prefs)) { case SAGE:return 0xffdce6be; case COBALT:return 0xff12275c; case PEARL:return 0xfff9f1e8; case PINE:return 0xff17382c; case AMBER:return 0xff2a1a0c; case HARMONY:return 0xff294867; default:return 0xff26363a; }
    }
    static int materialColor(SharedPreferences prefs,int base,boolean focused) {
        return prefs.getInt("theme_name",0)==0?base:MaterialAppearance.tint(base,accent(prefs),focused);
    }
    static int baseBottom(SharedPreferences prefs) {
        switch(id(prefs)) { case SAGE:return 0xffc5d4ac; case COBALT:return 0xff07112d; case PEARL:return 0xffe9e4dc; case PINE:return 0xff08150f; case AMBER:return 0xff120a04; case HARMONY:return 0xff0a1b37; default:return 0xff111e24; }
    }
    static void demoDefaults(SharedPreferences prefs) { demoDefaults(prefs,false); }
    static void demoDefaults(SharedPreferences prefs,boolean tv) {
        if(!custom(prefs)||prefs.getBoolean("demo_defaults_v1",false)) return;
        SharedPreferences.Editor e=prefs.edit().putBoolean("demo_defaults_v1",true).putInt("header_bg_mode",1)
                .putInt("tv_columns",4).putInt("phone_columns",2).putInt("avatar_shape",1).putInt("clock_size",tv?84:60);
        if(!prefs.contains("tile_corners")) e.putInt("tile_corners",id(prefs)==DUNE||id(prefs)==AMBER?0:1);
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
    static boolean fixedTypography(SharedPreferences prefs) { return id(prefs)==GLASS || harmony(prefs); }
    static String fontFamily(SharedPreferences prefs) {
        return fixedTypography(prefs)?"sans-serif":new String[]{"sans-serif","serif","monospace","sans-serif-light"}[Math.max(0,Math.min(3,prefs.getInt("app_font",0)))];
    }
    static String clockFontFamily(SharedPreferences prefs) {
        return fixedTypography(prefs)?"sans-serif":new String[]{id(prefs)==DUNE?"sans-serif-light":id(prefs)==PINE?"serif":"sans-serif","serif","monospace"}[Math.max(0,Math.min(2,prefs.getInt("clock_font",0)))];
    }
    static boolean clockBold(SharedPreferences prefs) { return (id(prefs)==DUNE||id(prefs)==PINE)?prefs.getInt("clock_font",0)!=0:true; }
    static int clockAlignment(SharedPreferences prefs) { return fixedTypography(prefs)?1:Math.max(0,Math.min(2,prefs.getInt("clock_align",1))); }
    static double luminance(int color) {
        double[] rgb={((color>>16)&255)/255d,((color>>8)&255)/255d,(color&255)/255d};
        for(int i=0;i<3;i++) rgb[i]=rgb[i]<=.04045?rgb[i]/12.92:Math.pow((rgb[i]+.055)/1.055,2.4);
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
    }
    static int wallpaperClockInk(int background) { return wallpaperClockInk(background,0xff11191c); }
    static int wallpaperClockInk(int background,int darkInk) {
        // Keep the requested white clock on normal photos, including pale skies and beige.
        return ((background>>16)&255)>=245 && ((background>>8)&255)>=245 && (background&255)>=245
                ? darkInk : 0xffffffff;
    }
    static int contentColor(SharedPreferences prefs,int color) {
        if(!light(prefs)) return color;
        return luminance(color)>.4 ? ((color>>>24)<240?muted(prefs):foreground(prefs)) : color;
    }
}
