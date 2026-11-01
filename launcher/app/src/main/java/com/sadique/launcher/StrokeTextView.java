package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * TextView with a thin black outline (stroke) under the normal fill, so white
 * text stays readable on bright wallpapers. The stroke is deliberately narrow.
 * (User 2026-11-01: "Outline lgao narrowed ho".)
 */
final class StrokeTextView extends TextView {

    /** Narrow outline width in px. */
    private float strokeWidthPx;

    StrokeTextView(Context context) {
        super(context);
        init(context);
    }

    StrokeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float dp = context.getResources().getDisplayMetrics().density;
        strokeWidthPx = 1.5f * dp;
    }

    /** Override the outline width (px). Zero disables the stroke. */
    void setStrokeWidthPx(float px) {
        strokeWidthPx = px;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (strokeWidthPx > 0) {
            Paint paint = getPaint();
            int color = paint.getColor();
            Paint.Style style = paint.getStyle();
            float width = paint.getStrokeWidth();
            Paint.Join join = paint.getStrokeJoin();
            // Pass 1: thin black stroke.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(strokeWidthPx);
            paint.setColor(0xFF000000);
            super.onDraw(canvas);
            // Pass 2: normal fill (with its shadow layer) on top.
            paint.setStyle(style);
            paint.setStrokeWidth(width);
            paint.setStrokeJoin(join);
            paint.setColor(color);
        }
        super.onDraw(canvas);
    }
}
