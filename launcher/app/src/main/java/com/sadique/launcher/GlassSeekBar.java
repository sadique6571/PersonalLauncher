package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.SeekBar;

/** Native touch, remote and accessibility semantics with a broad thumb-free glass fill. */
final class GlassSeekBar extends SeekBar {
    private final GlassCapsule capsule;
    GlassSeekBar(Context context) {
        super(context);
        capsule = TvGlassStyle.enabled(ThemePreferences.get(context)) ? new GlassCapsule(context) : null;
        if (capsule != null) {
            setBackground(null); setThumb(null); setThumbOffset(0); setPadding(0, 0, 0, 0);
            setSplitTrack(false);
        }
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (capsule != null) capsule.size(w, h);
    }
    @Override protected synchronized void onDraw(Canvas canvas) {
        if (capsule == null) { super.onDraw(canvas); return; }
        capsule.draw(canvas, (getProgress() - getMin()) / (float) Math.max(1, getMax() - getMin()),
                false, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }
}
