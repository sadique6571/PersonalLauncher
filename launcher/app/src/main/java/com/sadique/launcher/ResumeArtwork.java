package com.sadique.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Public artwork supplied by Watch Next, bounded and decoded off the UI thread. */
final class ResumeArtwork {
    private static final int LIMIT = 2 * 1024 * 1024;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private static java.io.File diskDir;

    private static java.io.File diskFile(Context context, String address) {
        if (diskDir == null) {
            diskDir = new java.io.File(context.getApplicationContext().getCacheDir(), "resume_posters");
            diskDir.mkdirs();
        }
        return new java.io.File(diskDir, Integer.toHexString(address.hashCode()) + ".jpg");
    }

    static Bitmap load(Context context, String... candidates) {
        for (String address : candidates) {
            if (Thread.currentThread().isInterrupted()) return null;
            if (address == null || address.length() > 8192) continue;
            Bitmap cached = CACHE.get(address);
            if (cached != null) return cached;
            // Disk cache: posters survive restarts and reloads don't re-download.
            java.io.File disk = diskFile(context, address);
            if (disk.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(disk.getPath());
                if (bmp != null) { CACHE.put(address, bmp); return bmp; }
                disk.delete();
            }
            try {
                byte[] data;
                Uri uri = Uri.parse(address);
                if ("content".equals(uri.getScheme())) {
                    try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                        if (stream == null) continue;
                        data = OnlineContent.readBounded(stream, LIMIT);
                    }
                } else if ("https".equals(uri.getScheme()) && uri.getUserInfo() == null) {
                    HttpsURLConnection connection = (HttpsURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                    // Poster CDNs commonly redirect (http->https, signed URLs); follow them.
                    connection.setInstanceFollowRedirects(true);
                    try {
                        if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > LIMIT) continue;
                        try (InputStream stream = connection.getInputStream()) { data = OnlineContent.readBounded(stream, LIMIT); }
                    } finally { connection.disconnect(); }
                } else continue;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) continue;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 640) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (image != null) {
                    CACHE.put(address, image);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(disk)) {
                        image.compress(Bitmap.CompressFormat.JPEG, 85, out);
                    } catch (Exception ignored) { /* memory cache still works */ }
                    return image;
                }
            } catch (Exception unavailable) { /* Keep the publishing app's real icon when it supplies no usable poster. */ }
        }
        return null;
    }
}
