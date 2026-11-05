package com.sadique.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Original rounded line symbols, sized for the TV toolbar; no external icon/font dependency. */
final class TvSymbols extends Drawable {
    private final int resource;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private TvSymbols(int resource) {
        this.resource = resource;
        paint.setColor(0xfff1f5fb); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.1f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    static Drawable icon(Context context, int resource) {
        if (TvGlassStyle.enabled(context.getSharedPreferences("personal_home", 0))
                && (resource == R.drawable.ic_search || resource == R.drawable.ic_notifications
                || resource == R.drawable.ic_settings || resource == R.drawable.ic_person || resource == R.drawable.ic_picture
                || resource == R.drawable.ic_volume || resource == R.drawable.ic_volume_muted))
            return new TvSymbols(resource);
        Drawable original = context.getDrawable(resource);
        return original == null ? null : original.mutate();
    }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        path.reset();
        if (resource == R.drawable.ic_search) {
            canvas.drawCircle(10.3f, 10.3f, 6.6f, paint); canvas.drawLine(15.3f, 15.3f, 21, 21, paint);
        } else if (resource == R.drawable.ic_settings) {
            canvas.drawCircle(12, 12, 6.2f, paint); canvas.drawCircle(12, 12, 2.4f, paint);
            for (int i = 0; i < 8; i++) { canvas.save(); canvas.rotate(i * 45, 12, 12); canvas.drawLine(12, 3, 12, 5.6f, paint); canvas.restore(); }
        } else if (resource == R.drawable.ic_notifications) {
            path.moveTo(5, 17); path.cubicTo(7, 15, 7, 13, 7, 10);
            path.cubicTo(7, 3, 17, 3, 17, 10); path.cubicTo(17, 13, 17, 15, 19, 17);
            path.close(); canvas.drawPath(path, paint);
            canvas.drawArc(10, 17, 14, 21, 0, 180, false, paint); canvas.drawLine(12, 3, 12, 4.5f, paint);
        } else if (resource == R.drawable.ic_volume || resource == R.drawable.ic_volume_muted) {
            path.moveTo(3, 9); path.lineTo(7, 9); path.lineTo(12, 5); path.lineTo(12, 19); path.lineTo(7, 15); path.lineTo(3, 15); path.close();
            canvas.drawPath(path, paint);
            if (resource == R.drawable.ic_volume_muted) { canvas.drawLine(16, 9, 21, 15, paint); canvas.drawLine(21, 9, 16, 15, paint); }
            else { canvas.drawArc(11, 7, 20, 17, -55, 110, false, paint); canvas.drawArc(11, 3, 25, 21, -50, 100, false, paint); }
        } else if (resource == R.drawable.ic_person) {
            canvas.drawCircle(12, 8, 3.7f, paint);
            path.moveTo(4, 21); path.cubicTo(4, 12, 20, 12, 20, 21); canvas.drawPath(path, paint);
        } else {
            canvas.drawRoundRect(2.5f, 4, 21.5f, 20, 3, 3, paint);
            path.moveTo(4, 18); path.lineTo(10, 11); path.lineTo(14, 15); path.lineTo(17, 12); path.lineTo(20, 16);
            canvas.drawPath(path, paint); canvas.drawCircle(17, 8, 1, paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
