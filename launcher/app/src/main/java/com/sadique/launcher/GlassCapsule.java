package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Shared full-height capsule: white glass fill for Apple, accent fill and border for Classic. */
final class GlassCapsule {
    private final android.graphics.drawable.GradientDrawable shell;
    private final boolean glass;
    private final int accent;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float radius;
    GlassCapsule(Context context) {
        android.content.SharedPreferences prefs = ThemePreferences.get(context);
        glass=TvGlassStyle.enabled(prefs); accent=GlassSettings.themeAccent(context);
        int stroke=Math.max(1,Math.round(context.getResources().getDisplayMetrics().density));
        if(glass) shell=TvGlassStyle.surface(0,stroke,false,prefs);
        else {
            shell=new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{TvGlassStyle.tinted(0xff132539,prefs),TvGlassStyle.tinted(0xff09182a,prefs)});
            shell.setStroke(stroke,0x55415a72);
            shell.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(TvGlassStyle.intensity(prefs),false),prefs,false));
        }
    }
    int filledTextColor() {
        return glass ? 0xff142239 : (android.graphics.Color.luminance(accent)>.179 ? 0xff000000 : 0xffffffff);
    }
    void size(int width, int height) {
        bounds.set(0, 0, width, height); radius = Math.min(width, height) / 2f;
        shell.setCornerRadius(radius); shell.setBounds(0, 0, width, height);
        fill.setShader(new LinearGradient(0, 0, Math.max(1, width), Math.max(1, height),
                glass ? 0xffffffff : accent, glass ? 0xffe9edf5 : accent, Shader.TileMode.CLAMP));
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
        if (shell instanceof GlassSurface) ((GlassSurface)shell).drawRim(canvas);
    }
}
