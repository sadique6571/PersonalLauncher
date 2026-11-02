package com.sadique.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;

/** Screen-bounded local profile image decoding, on the existing image worker. */
final class ProfileAssets {
    private ProfileAssets() { }
    static Bitmap load(Context ctx, File file, boolean avatar) {
        if (!file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        int bound = avatar ? 256 : Math.min(MemoryProfile.wallpaperMaxDim(MemoryProfile.effectiveTier(ctx,
                ctx.getSharedPreferences("personal_home", 0))), Math.max(ctx.getResources().getDisplayMetrics().widthPixels,
                ctx.getResources().getDisplayMetrics().heightPixels));
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > bound * 2) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (bitmap == null) return null;
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest > bound) return Bitmap.createScaledBitmap(bitmap, Math.max(1, bitmap.getWidth() * bound / largest),
                Math.max(1, bitmap.getHeight() * bound / largest), true);
        return bitmap;
    }
}
