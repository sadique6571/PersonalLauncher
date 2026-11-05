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
        this.resource = normalize(resource);
        paint.setColor(0xfff1f5fb); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.1f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }
    private static final int[] SUPPORTED = {
        R.drawable.ic_search, R.drawable.ic_notifications, R.drawable.ic_settings, R.drawable.ic_person, R.drawable.ic_picture, R.drawable.ic_volume, R.drawable.ic_volume_muted, R.drawable.ic_volume_bluetooth, R.drawable.ic_volume_headphones, R.drawable.ic_volume_speaker, R.drawable.ic_wifi, R.drawable.ic_ethernet, R.drawable.ic_internet, R.drawable.ic_apps, R.drawable.ic_apps_pill, R.drawable.ic_categories, R.drawable.ic_home_settings, R.drawable.ic_home_settings_pill, R.drawable.ic_input, R.drawable.ic_av, R.drawable.ic_live_guide, R.drawable.ic_livetv, R.drawable.ic_lock, R.drawable.ic_lock_open, R.drawable.ic_media_next, R.drawable.ic_media_pause, R.drawable.ic_media_play, R.drawable.ic_media_previous, R.drawable.ic_microphone, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_clock, R.drawable.ic_swap_vert, R.drawable.ic_watchlist, android.R.drawable.stat_sys_data_bluetooth
    };
    static boolean supports(int resource) {
        for (int id : SUPPORTED) if (id == resource) return true;
        return false;
    }
    private static int normalize(int resource) {
        if (resource == android.R.drawable.stat_sys_data_bluetooth) return R.drawable.ic_volume_bluetooth;
        if (resource == R.drawable.ic_apps_pill || resource == R.drawable.ic_categories) return R.drawable.ic_apps;
        if (resource == R.drawable.ic_home_settings_pill) return R.drawable.ic_home_settings;
        if (resource == R.drawable.ic_av) return R.drawable.ic_input;
        if (resource == R.drawable.ic_live_guide) return R.drawable.ic_watchlist;
        return resource;
    }
    static Drawable icon(Context context, int resource) {
        if (TvGlassStyle.enabled(context.getSharedPreferences("personal_home", 0)) && supports(resource))
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
        } else if (resource == R.drawable.ic_picture) {
            canvas.drawRoundRect(2.5f, 4, 21.5f, 20, 3, 3, paint);
            path.moveTo(4, 18); path.lineTo(10, 11); path.lineTo(14, 15); path.lineTo(17, 12); path.lineTo(20, 16);
            canvas.drawPath(path, paint); canvas.drawCircle(17, 8, 1, paint);
        } else if (resource == R.drawable.ic_wifi) {
            canvas.drawArc(2, 5, 22, 23, 218, 104, false, paint);
            canvas.drawArc(6, 10, 18, 22, 220, 100, false, paint);
            canvas.drawCircle(12, 18, .7f, paint);
        } else if (resource == R.drawable.ic_internet) {
            canvas.drawCircle(12, 12, 9, paint); canvas.drawOval(8, 3, 16, 21, paint);
            canvas.drawLine(3, 12, 21, 12, paint);
        } else if (resource == R.drawable.ic_ethernet) {
            canvas.drawRoundRect(3, 4, 21, 17, 2, 2, paint);
            canvas.drawLine(12, 17, 12, 21, paint); canvas.drawLine(7, 21, 17, 21, paint);
            for (int x=7; x<=17; x+=3) canvas.drawLine(x, 6, x, 10, paint);
        } else if (resource == R.drawable.ic_volume_bluetooth) {
            path.moveTo(7, 7); path.lineTo(17, 17); path.lineTo(12, 22); path.lineTo(12, 2);
            path.lineTo(17, 7); path.lineTo(7, 17); canvas.drawPath(path, paint);
        } else if (resource == R.drawable.ic_volume_headphones) {
            canvas.drawArc(3, 3, 21, 21, 180, 180, false, paint);
            canvas.drawRoundRect(3, 11, 7, 20, 2, 2, paint); canvas.drawRoundRect(17, 11, 21, 20, 2, 2, paint);
        } else if (resource == R.drawable.ic_volume_speaker) {
            canvas.drawRoundRect(6, 2, 18, 22, 2, 2, paint);
            canvas.drawCircle(12, 8, 1, paint); canvas.drawCircle(12, 16, 3, paint);
        } else if (resource == R.drawable.ic_apps) {
            for (int y=3; y<=14; y+=11) for (int x=3; x<=14; x+=11)
                canvas.drawRoundRect(x, y, x+7, y+7, 2, 2, paint);
        } else if (resource == R.drawable.ic_home_settings) {
            path.moveTo(3, 11); path.lineTo(12, 3); path.lineTo(21, 11);
            path.moveTo(5, 10); path.lineTo(5, 21); path.lineTo(10, 21); path.lineTo(10, 15);
            path.lineTo(14, 15); path.lineTo(14, 21); path.lineTo(19, 21); path.lineTo(19, 10); canvas.drawPath(path, paint);
        } else if (resource == R.drawable.ic_input) {
            canvas.drawRoundRect(3, 4, 21, 20, 3, 3, paint);
            canvas.drawLine(8, 8, 8, 16, paint); canvas.drawLine(5, 12, 15, 12, paint);
            path.moveTo(12, 9); path.lineTo(15, 12); path.lineTo(12, 15); canvas.drawPath(path, paint);
        } else if (resource == R.drawable.ic_livetv) {
            canvas.drawRoundRect(3, 6, 21, 19, 2, 2, paint);
            canvas.drawLine(8, 2, 12, 6, paint); canvas.drawLine(16, 2, 12, 6, paint);
            canvas.drawLine(8, 22, 16, 22, paint);
        } else if (resource == R.drawable.ic_watchlist) {
            canvas.drawRoundRect(4, 3, 20, 21, 2, 2, paint);
            for (int y=7; y<=17; y+=5) canvas.drawLine(8, y, 16, y, paint);
        } else if (resource == R.drawable.ic_lock || resource == R.drawable.ic_lock_open) {
            canvas.drawRoundRect(5, 10, 19, 21, 2, 2, paint);
            canvas.drawArc(8, 2, 16, 16, resource == R.drawable.ic_lock ? 180 : 200,
                    resource == R.drawable.ic_lock ? 180 : 140, false, paint);
            canvas.drawLine(12, 14, 12, 17, paint);
        } else if (resource == R.drawable.ic_media_pause) {
            canvas.drawLine(8, 4, 8, 20, paint); canvas.drawLine(16, 4, 16, 20, paint);
        } else if (resource == R.drawable.ic_media_play || resource == R.drawable.ic_media_next || resource == R.drawable.ic_media_previous) {
            boolean previous=resource == R.drawable.ic_media_previous;
            path.moveTo(previous ? 17 : 7, 4); path.lineTo(previous ? 5 : 19, 12);
            path.lineTo(previous ? 17 : 7, 20); path.close(); canvas.drawPath(path, paint);
            if (resource != R.drawable.ic_media_play) canvas.drawLine(previous ? 3 : 21, 4, previous ? 3 : 21, 20, paint);
        } else if (resource == R.drawable.ic_microphone) {
            canvas.drawRoundRect(9, 2, 15, 15, 3, 3, paint);
            canvas.drawArc(5, 7, 19, 19, 0, 180, false, paint);
            canvas.drawLine(12, 19, 12, 22, paint); canvas.drawLine(8, 22, 16, 22, paint);
        } else if (resource == R.drawable.ic_palette) {
            path.moveTo(20, 13); path.cubicTo(25, 2, 7, 0, 3, 9); path.cubicTo(-1, 20, 12, 24, 14, 19);
            path.cubicTo(16, 15, 17, 17, 20, 13); path.close(); canvas.drawPath(path, paint);
            canvas.drawCircle(7, 10, .7f, paint); canvas.drawCircle(11, 6, .7f, paint); canvas.drawCircle(17, 7, .7f, paint);
        } else if (resource == R.drawable.ic_brightness) {
            canvas.drawCircle(12, 12, 4, paint);
            for (int i=0; i<8; i++) { canvas.save(); canvas.rotate(i*45, 12, 12); canvas.drawLine(12, 2, 12, 4, paint); canvas.restore(); }
        } else if (resource == R.drawable.ic_clock) {
            canvas.drawCircle(12, 12, 9, paint); canvas.drawLine(12, 6, 12, 12, paint); canvas.drawLine(12, 12, 17, 15, paint);
        } else if (resource == R.drawable.ic_swap_vert) {
            path.moveTo(4, 7); path.lineTo(8, 3); path.lineTo(12, 7); path.moveTo(8, 3); path.lineTo(8, 20);
            path.moveTo(12, 17); path.lineTo(16, 21); path.lineTo(20, 17); path.moveTo(16, 21); path.lineTo(16, 4); canvas.drawPath(path, paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setTint(int color) { paint.setColor(color); invalidateSelf(); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}

