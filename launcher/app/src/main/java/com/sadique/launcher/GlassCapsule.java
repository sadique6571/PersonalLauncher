package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Shared white-filled capsule for the volume OSD and interactive horizontal controls. */
final class GlassCapsule {
    private final GlassSurface shell;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float radius;
    GlassCapsule(Context context) {
        android.content.SharedPreferences prefs = ThemePreferences.get(context);
        shell = (GlassSurface) TvGlassStyle.surface(0,
                Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), false, prefs);
    }
    void size(int width, int height) {
        bounds.set(0, 0, width, height); radius = Math.min(width, height) / 2f;
        shell.setCornerRadius(radius); shell.setBounds(0, 0, width, height);
        fill.setShader(new LinearGradient(0, 0, Math.max(1, width), Math.max(1, height),
                0xffffffff, 0xffe9edf5, Shader.TileMode.CLAMP));
    }
    void draw(Canvas canvas, float fraction, boolean vertical, boolean rtl) {
        shell.draw(canvas);
        fraction = Math.max(0, Math.min(1, fraction));
        if (fraction > 0) {
            int save = canvas.save();
            if (vertical) canvas.clipRect(0, bounds.height() * (1 - fraction), bounds.width(), bounds.height());
            else if (rtl) canvas.clipRect(bounds.width() * (1 - fraction), 0, bounds.width(), bounds.height());
            else canvas.clipRect(0, 0, bounds.width() * fraction, bounds.height());
            canvas.drawRoundRect(bounds, radius, radius, fill);
            canvas.restoreToCount(save);
        }
        shell.drawRim(canvas);
    }
}
