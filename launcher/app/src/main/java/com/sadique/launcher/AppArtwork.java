package com.sadique.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Default app artwork, with adaptive backgrounds filling the launcher's own rounded outline. */
final class AppArtwork {
    final Drawable image;
    final int accent;
    private AppArtwork(Drawable image, int accent) { this.image = image; this.accent = accent; }

    static AppArtwork create(Resources resources, Drawable source) {
        int size = 256;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable icon = source.getConstantState() == null ? source.mutate() : source.getConstantState().newDrawable().mutate();
        float ratio = icon.getIntrinsicWidth() > 0 && icon.getIntrinsicHeight() > 0
                ? icon.getIntrinsicWidth() / (float) icon.getIntrinsicHeight() : 1f;
        int width = ratio >= 1 ? size : Math.round(size * ratio);
        int height = ratio >= 1 ? Math.round(size / ratio) : size;
        icon.setBounds((size - width) / 2, (size - height) / 2, (size + width) / 2, (size + height) / 2);
        Drawable adaptiveBackground = null;
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            adaptiveBackground = adaptive.getBackground();
            if (adaptiveBackground != null) { adaptiveBackground.setBounds(0, 0, size, size); adaptiveBackground.draw(canvas); }
            Drawable foreground = adaptive.getForeground();
            if (foreground != null) {
                int inset = Math.round(size * AdaptiveIconDrawable.getExtraInsetFraction());
                foreground.setBounds(-inset, -inset, size + inset, size + inset); foreground.draw(canvas);
            }
        } else icon.draw(canvas);
        // Remove baked-in transparent margins from legacy icons, not just ImageView padding.
        int left = size, top = size, right = -1, bottom = -1;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (Color.alpha(bitmap.getPixel(x, y)) < 24) continue;
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right >= left && bottom >= top) bitmap = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
        float[] weights = new float[12];
        float[][] sums = new float[12][3];
        float[] hsv = new float[3];
        for (int y = 0; y < bitmap.getHeight(); y += Math.max(1, bitmap.getHeight() / 16))
            for (int x = 0; x < bitmap.getWidth(); x += Math.max(1, bitmap.getWidth() / 16)) {
            int pixel = bitmap.getPixel(x, y);
            Color.colorToHSV(pixel, hsv);
            if (Color.alpha(pixel) < 128 || hsv[1] < .25f || hsv[2] < .2f) continue;
            int bin = (int) (hsv[0] / 30) % 12;
            float weight = hsv[1] * hsv[2];
            weights[bin] += weight;
            sums[bin][0] += Color.red(pixel) * weight;
            sums[bin][1] += Color.green(pixel) * weight;
            sums[bin][2] += Color.blue(pixel) * weight;
        }
        int best = 0;
        for (int i = 1; i < weights.length; i++) if (weights[i] > weights[best]) best = i;
        int accent = weights[best] < 1 ? 0xff40d9ff : Color.rgb((int) (sums[best][0] / weights[best]),
                (int) (sums[best][1] / weights[best]), (int) (sums[best][2] / weights[best]));
        // Compose the wide card once off the UI thread. Extend the native background to
        // every edge while preserving the foreground logo's proportions and complete shape.
        Bitmap card = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas cardCanvas = new Canvas(card);
        cardCanvas.drawColor(0xff092038);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (adaptiveBackground != null) {
            adaptiveBackground.setBounds(0, 0, 320, 180); adaptiveBackground.draw(cardCanvas);
        }
        float scale = Math.min(320f / bitmap.getWidth(), 180f / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        float x = (320 - w) / 2f, y = (180 - h) / 2f;
        if (adaptiveBackground == null) {
            // Matching edge colours avoid a small square pasted onto an unrelated tile.
            cardCanvas.drawBitmap(bitmap, new Rect(0, 0, 1, bitmap.getHeight()), new RectF(0, y, x, y + h), paint);
            cardCanvas.drawBitmap(bitmap, new Rect(bitmap.getWidth() - 1, 0, bitmap.getWidth(), bitmap.getHeight()),
                    new RectF(x + w, y, 320, y + h), paint);
        }
        cardCanvas.drawBitmap(bitmap, null, new RectF(x, y, x + w, y + h), paint);
        return new AppArtwork(new BitmapDrawable(resources, card), accent);
    }
}
