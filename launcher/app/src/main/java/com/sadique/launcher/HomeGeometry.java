package com.sadique.launcher;

/** Geometry shared by the tile shell, clipped artwork and focus rim. Values are dp. */
final class HomeGeometry {
    private HomeGeometry() { }
    static int tileRadius(int choice) { return new int[]{4,16,28}[Math.max(0,Math.min(2,choice))]; }
    static float insetRadius(float radius,float inset) { return Math.max(0,radius-inset); }
    static int clockSize(int requested,boolean compact) { return compact?Math.round(requested*.72f):requested; }
    static float avatarRadius(int size) { return Math.max(0,size)/4f; }
}
