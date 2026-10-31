package com.sadique.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
        icon.draw(canvas);
        // Bulk-read pixels once: per-pixel getPixel() is a JNI call each, ~66k per icon.
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        // Sample before trimming: a cropped logo's corner may be red (Netflix N),
        // which must never become the background colour of the whole card.
        int nativeCorner = pixels[0];
        int nativeBackground = (nativeCorner >>> 24) > 240 ? nativeCorner : 0xff092038;
        // Remove baked-in transparent margins from legacy icons, not just ImageView padding.
        int left = size, top = size, right = -1, bottom = -1;
        for (int y = 0; y < size; y++) {
            int row = y * size;
            for (int x = 0; x < size; x++) {
                if ((pixels[row + x] >>> 24) < 24) continue;
                if (x < left) left = x; if (x > right) right = x;
                if (y < top) top = y; if (y > bottom) bottom = y;
            }
        }
        if (right >= left && bottom >= top) {
            bitmap = Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
            int w = right - left + 1, h = bottom - top + 1;
            int[] trimmed = new int[w * h];
            for (int y = 0; y < h; y++)
                System.arraycopy(pixels, (top + y) * size + left, trimmed, y * w, w);
            pixels = trimmed;
        }
        float[] weights = new float[12];
        float[][] sums = new float[12][3];
        float[] hsv = new float[3];
        int pw = bitmap.getWidth(), ph = bitmap.getHeight();
        int stepX = Math.max(1, pw / 16), stepY = Math.max(1, ph / 16);
        for (int y = 0; y < ph; y += stepY)
            for (int x = 0; x < pw; x += stepX) {
                int pixel = pixels[y * pw + x];
                if ((pixel >>> 24) < 128) continue;
                Color.colorToHSV(pixel, hsv);
                if (hsv[1] < .25f || hsv[2] < .2f) continue;
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
        // A clean card background and proportional logo; never stretch icon edge pixels.
        Bitmap card = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas cardCanvas = new Canvas(card);
        cardCanvas.drawColor(nativeBackground);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float scale = Math.min(270f / bitmap.getWidth(), 140f / bitmap.getHeight());
        float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
        float x = (320 - w) / 2f, y = (180 - h) / 2f;
        cardCanvas.drawBitmap(bitmap, null, new RectF(x, y, x + w, y + h), paint);
        return new AppArtwork(new BitmapDrawable(resources, card), accent);
    }
}
