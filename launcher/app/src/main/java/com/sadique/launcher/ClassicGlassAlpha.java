package com.sadique.launcher;

/** Keep both Classic layers translucent; transparent cards over an opaque shell are not glass. */
final class ClassicGlassAlpha {
    private ClassicGlassAlpha() { }
    private static int clamp(int value) { return Math.max(0,Math.min(100,value)); }
    static int card(int intensity,boolean focused) { return Math.round(150*clamp(intensity)/100f)+(focused?48:0); }
    static int panel(int intensity) { return clamp(intensity); }
    static int drawer(int intensity) { return Math.round(70*clamp(intensity)/100f); }
}
