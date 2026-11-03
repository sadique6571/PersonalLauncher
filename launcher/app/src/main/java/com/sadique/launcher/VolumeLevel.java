package com.sadique.launcher;

/** Display the actual media state; muting must not destroy the remembered volume. */
final class VolumeLevel {
    static float fraction(int volume, int maximum, boolean muted) {
        if (muted || maximum <= 0) return 0f;
        return Math.max(0f, Math.min(1f, volume / (float) maximum));
    }
    static int percent(int volume, int maximum, boolean muted) {
        return Math.round(fraction(volume, maximum, muted) * 100);
    }
    private VolumeLevel() { }
}
