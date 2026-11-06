package com.sadique.launcher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;

/** Lightweight translucent lens: cached gradients, a bright upper lip and a soft lower reflection. */
class GlassSurface extends GradientDrawable {
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Matrix backdropMatrix = new android.graphics.Matrix();
    private android.graphics.Bitmap backdropImage;
    private int backdropOpacity=255;
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF outer = new RectF(), inset = new RectF();
    private final float width;
    private final int edge;

    GlassSurface(float width, int edge) {
        this.width = Math.max(1, width); this.edge = edge;
        rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(this.width);
        inner.setStyle(Paint.Style.STROKE); inner.setStrokeWidth(this.width);
    }
    void backdropOpacity(int alpha) { backdropOpacity=Math.max(0,Math.min(255,alpha)); invalidateSelf(); }
    void backdrop(android.graphics.Bitmap image, int sceneWidth, int sceneHeight, int x, int y) {
        if (backdropImage != image) {
            backdropImage=image;
            backdrop.setShader(new android.graphics.BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        backdropMatrix.setScale(sceneWidth / (float) image.getWidth(), sceneHeight / (float) image.getHeight());
        backdropMatrix.postTranslate(-x,-y); backdrop.getShader().setLocalMatrix(backdropMatrix);
        invalidateSelf();
    }
    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        outer.set(bounds); outer.inset(width / 2f, width / 2f);
        inset.set(outer); inset.inset(width * 1.5f, width * 1.5f);
        float bottom = Math.max(bounds.top + 1f, bounds.bottom);
        int a = android.graphics.Color.alpha(edge);
        rim.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bottom,
                new int[]{(Math.min(255, a * 2) << 24) | 0xffffff, (a / 3 << 24) | 0xffffff,
                        (a << 24) | 0xffffff}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        inner.setShader(new LinearGradient(0, bounds.top, 0, bottom,
                new int[]{(a / 2 << 24) | 0xffffff, 0x00ffffff, (a / 5 << 24) | 0xffffff},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas canvas) {
        if (backdropImage != null && backdropOpacity>0) {
            backdrop.setAlpha(backdropOpacity*getAlpha()/255);
            float radius=getCornerRadius();
            if (getShape()==OVAL) canvas.drawOval(outer,backdrop);
            else canvas.drawRoundRect(outer,radius,radius,backdrop);
        }
        super.draw(canvas);
        drawRim(canvas);
    }
    void drawRim(Canvas canvas) {
        rim.setAlpha(getAlpha()); inner.setAlpha(getAlpha());
        if (getShape() == OVAL) {
            canvas.drawOval(outer, rim); canvas.drawOval(inset, inner);
        } else {
            float r = getCornerRadius();
            canvas.drawRoundRect(outer, HomeGeometry.insetRadius(r,width/2f), HomeGeometry.insetRadius(r,width/2f), rim);
            canvas.drawRoundRect(inset, HomeGeometry.insetRadius(r,width*2f), HomeGeometry.insetRadius(r,width*2f), inner);
        }
    }
}


