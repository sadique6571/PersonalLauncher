package com.sadique.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Bounded bitmap decoding: decodes images to the exact size needed instead of
 * full resolution, saving significant memory on low-RAM TVs.
 */
final class BitmapUtils {

    private BitmapUtils() {}

    /**
     * Decodes a bitmap from resources, scaled down so the longest edge does not
     * exceed maxDim. Returns null on failure.
     */
    static Bitmap decodeBounded(Resources res, int resId, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim);
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // 2 bytes/pixel, no alpha needed
            return BitmapFactory.decodeResource(res, resId, opts);
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }

    /**
     * Decodes a bitmap from a file, scaled down so the longest edge does not
     * exceed maxDim. Returns null on failure. Handles corrupt files safely.
     */
    static Bitmap decodeFileBounded(String path, int maxDim) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists() || f.length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        } catch (Exception e) {
            return null;
        }
        try (InputStream in2 = new FileInputStream(f)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            // Re-read bounds for sample calculation
            opts.inSampleSize = 1;
            BitmapFactory.Options b2 = new BitmapFactory.Options();
            b2.inJustDecodeBounds = true;
            try (InputStream in3 = new FileInputStream(f)) {
                BitmapFactory.decodeStream(in3, null, b2);
            }
            opts.inSampleSize = sampleSize(b2.outWidth, b2.outHeight, maxDim);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in2, null, opts);
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }

    /**
     * Calculates inSampleSize (power of 2) so the longest edge fits within maxDim.
     */
    static int sampleSize(int width, int height, int maxDim) {
        if (maxDim <= 0 || width <= 0 || height <= 0) return 1;
        int longEdge = Math.max(width, height);
        int sample = 1;
        while (longEdge / (sample * 2) >= maxDim) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Returns true if the bitmap is non-null and not recycled.
     */
    static boolean isUsable(Bitmap b) {
        return b != null && !b.isRecycled();
    }

    /**
     * Safely recycles a bitmap if it's not null and not already recycled.
     * Only call when you're sure no view is still using it.
     */
    static void safeRecycle(Bitmap b) {
        if (b != null && !b.isRecycled()) {
            try {
                b.recycle();
            } catch (Exception ignored) {}
        }
    }
}
