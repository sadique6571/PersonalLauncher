package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** A wide bottom-filled track, without a thumb or surrounding card. */
final class VolumeBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF track = new RectF();
    private float fraction;
    private int accent;
    VolumeBar(Context context) { super(context); setFocusable(false); }
    void level(float fraction, int color) {
        if (this.fraction == fraction && accent == color) return;
        this.fraction = fraction; accent = color; invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        track.set(1, 1, getWidth() - 1, getHeight() - 1);
        float radius = getWidth() / 2f;
        paint.setColor(0xb51b2a3c); canvas.drawRoundRect(track, radius, radius, paint);
        if (fraction <= 0) return;
        clip.reset(); clip.addRoundRect(track, radius, radius, Path.Direction.CW);
        int save = canvas.save(); canvas.clipPath(clip);
        paint.setColor(accent);
        canvas.drawRect(track.left, track.bottom - track.height() * fraction, track.right, track.bottom, paint);
        canvas.restoreToCount(save);
    }
}
