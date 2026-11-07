package com.sadique.launcher;

/** All inputs that can change the adaptive wallpaper clock colour. */
final class ClockSampleKey {
    private final Object photo;
    private final int x, y, shade, darkInk;
    ClockSampleKey(Object photo, int x, int y, int shade, int darkInk) {
        this.photo=photo; this.x=x; this.y=y; this.shade=shade; this.darkInk=darkInk;
    }
    boolean matches(Object photo, int x, int y, int shade, int darkInk) {
        return this.photo==photo && this.x==x && this.y==y && this.shade==shade && this.darkInk==darkInk;
    }
}
