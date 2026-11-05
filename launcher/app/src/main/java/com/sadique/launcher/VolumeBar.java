package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** One bottom-filled capsule, with the output symbol inside the track in Apple style. */
final class VolumeBar extends View {
    private static final android.graphics.ColorFilter WHITE = new android.graphics.PorterDuffColorFilter(0xffffffff, android.graphics.PorterDuff.Mode.SRC_IN);
    private static final android.graphics.ColorFilter BLUE = new android.graphics.PorterDuffColorFilter(0xff159fe8, android.graphics.PorterDuff.Mode.SRC_IN);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF track = new RectF();
    private final GlassCapsule capsule;
    private float fraction;
    private int accent, symbolResource, symbolTint;
    private Drawable symbol;
    VolumeBar(Context context) {
        super(context); setFocusable(false);
        capsule = TvGlassStyle.enabled(ThemePreferences.get(context)) ? new GlassCapsule(context) : null;
    }
    void level(float fraction, int color) {
        if (this.fraction == fraction && accent == color) return;
        this.fraction = fraction; accent = color; invalidate();
    }
    void symbol(int resource) {
        if (symbolResource == resource) return;
        symbolResource = resource; symbolTint = 0;
        symbol = TvSymbols.icon(getContext(), resource); positionSymbol(); invalidate();
    }
    private void positionSymbol() {
        if (symbol == null) return;
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(27 * density), bottom = getHeight() - Math.round(18 * density);
        int left = (getWidth() - size) / 2;
        symbol.setBounds(left, bottom - size, left + size, bottom);
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (capsule != null) capsule.size(w, h);
        positionSymbol();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (capsule != null) {
            capsule.draw(canvas, fraction, true, false);
            if (symbol != null) {
                float boundary = getHeight() * (1 - fraction);
                // Draw both colours through disjoint clips: readable even where fill crosses the glyph.
                int save = canvas.save(); canvas.clipRect(0, 0, getWidth(), boundary);
                tint(0xffffffff); symbol.draw(canvas); canvas.restoreToCount(save);
                save = canvas.save(); canvas.clipRect(0, boundary, getWidth(), getHeight());
                tint(0xff159fe8); symbol.draw(canvas); canvas.restoreToCount(save);
            }
            return;
        }
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
    private void tint(int color) {
        if (symbolTint != color) { symbol.setColorFilter(color == 0xffffffff ? WHITE : BLUE); symbolTint = color; }
    }
}
