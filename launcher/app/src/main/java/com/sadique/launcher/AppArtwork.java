package com.sadique.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
