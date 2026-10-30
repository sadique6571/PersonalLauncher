package com.sadique.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) icon;
            Drawable background = adaptive.getBackground(), foreground = adaptive.getForeground();
            if (background != null) { background.setBounds(0, 0, size, size); background.draw(canvas); }
            if (foreground != null) {
                int inset = Math.round(size * AdaptiveIconDrawable.getExtraInsetFraction());
                foreground.setBounds(-inset, -inset, size + inset, size + inset); foreground.draw(canvas);
            }
        } else {
            icon.setBounds(0, 0, size, size); icon.draw(canvas);
            int left = size, top = size, right = -1, bottom = -1;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) <= 24) continue;
                left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
            if (right >= left && bottom >= top && (right - left < size - 4 || bottom - top < size - 4))
                bitmap = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
        }
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
        return new AppArtwork(new BitmapDrawable(resources, bitmap), accent);
    }
}
