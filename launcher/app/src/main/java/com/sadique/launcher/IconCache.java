package com.sadique.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disk cache for processed app-card bitmaps. The first load still composes each icon, but every
 * later start (and the boot pre-warm) reads a small PNG instead — ~5ms per icon.
 */
final class IconCache {
    private final File dir;
    private final SharedPreferences prefs;

    IconCache(Context context) {
        Context app = context.getApplicationContext();
        this.dir = new File(app.getCacheDir(), "icon_cards");
        this.dir.mkdirs();
        this.prefs = app.getSharedPreferences("personal_home", Context.MODE_PRIVATE);
    }

    static long version(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (Exception gone) {
            return 0;
        }
    }

    private static String safe(String pkg) {
        return pkg.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private File file(String pkg, long version) {
        return new File(dir, safe(pkg) + "_" + version + ".png");
    }

    private String accentKey(String pkg, long version) {
        return "icon_accent_" + safe(pkg) + "_" + version;
    }

    /** Cached card, or null. */
    Bitmap load(String pkg, long version) {
        if (version == 0) return null;
        File f = file(pkg, version);
        if (!f.exists()) {
            prune(pkg, version);
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(f.getPath());
        if (bitmap == null) f.delete();
        return bitmap;
    }

    int accent(String pkg, long version, int fallback) {
        return prefs.getInt(accentKey(pkg, version), fallback);
    }

    void save(String pkg, long version, Bitmap card, int accent) {
        if (version == 0 || card == null) return;
        File tmp = new File(dir, safe(pkg) + "_" + version + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!card.compress(Bitmap.CompressFormat.PNG, 100, out)) return;
            out.flush();
            File dest = file(pkg, version);
            if (!tmp.renameTo(dest)) return;
            prefs.edit().putInt(accentKey(pkg, version), accent).apply();
            prune(pkg, version);
        } catch (Exception failed) {
            tmp.delete();
        }
    }

    /** Deletes the whole disk cache (used when logo size changes). */
    void clear() {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    /** Deletes stale versions of one package (and their accent entries). */
    private void prune(String pkg, long keepVersion) {
        String prefix = safe(pkg) + "_";
        String keep = prefix + keepVersion + ".png";
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(prefix) && !name.equals(keep)) f.delete();
        }
        String accentPrefix = "icon_accent_" + prefix;
        String keepAccent = accentKey(pkg, keepVersion);
        for (String key : new HashSet<>(prefs.getAll().keySet())) {
            if (key.startsWith(accentPrefix) && !key.equals(keepAccent)) prefs.edit().remove(key).apply();
        }
    }

    /** Boot pre-warm: compose and cache every launchable app's card before the home screen opens. */
    static void warm(Context context) {
        Context app = context.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        IconCache cache = new IconCache(app);
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Set<String> packages = new HashSet<>();
        android.content.res.Resources resources = app.getResources();
        float logoScale = new float[]{.8f, 1f, 1.2f}[Math.max(0, Math.min(2,
                app.getSharedPreferences("personal_home", Context.MODE_PRIVATE).getInt("logo_size", 1)))];
        for (ResolveInfo info : found) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(app.getPackageName()) || !packages.add(pkg)) continue;
            long version = version(pm, pkg);
            if (cache.load(pkg, version) != null) continue;
            try {
                Drawable logo = info.loadIcon(pm);
                if (pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube"))
                    logo = app.getDrawable(R.drawable.logo_youtube);
                if (pkg.equals("com.netflix.ninja") || pkg.equals("com.netflix.mediaclient"))
                    logo = app.getDrawable(R.drawable.logo_netflix);
                AppArtwork artwork = AppArtwork.create(resources, logo, logoScale);
                Bitmap composed = ((android.graphics.drawable.BitmapDrawable) artwork.image).getBitmap();
                cache.save(pkg, version, composed, artwork.accent);
            } catch (RuntimeException removedDuringQuery) {
                // A package can be replaced or removed while warming; the activity retries it.
            }
        }
    }
}
