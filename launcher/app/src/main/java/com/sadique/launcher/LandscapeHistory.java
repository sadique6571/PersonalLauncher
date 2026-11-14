package com.sadique.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Persists image identity and fingerprints across screensaver sessions and a bounded reserve of unseen photos. */
final class LandscapeHistory extends SQLiteOpenHelper {
    static final int RESERVE_SIZE = 6;
    static final int RESERVE_PHOTO_LIMIT = 900 * 1024;
    static final class SavedPhoto {
        final String id, subject;
        final byte[] bytes;
        SavedPhoto(String id, String subject, byte[] bytes) {
            this.id = id;
            this.subject = subject;
            this.bytes = bytes;
        }
    }

    LandscapeHistory(Context context) { super(context.getApplicationContext(), "landscape_history.db", null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shown (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE, visual_x INTEGER, visual_y INTEGER)");
        createReserve(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_x INTEGER");
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_y INTEGER");
        }
        if (oldVersion < 3) createReserve(db);
    }

    private static void createReserve(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE photo_reserve (id TEXT PRIMARY KEY, subject TEXT NOT NULL, photo BLOB NOT NULL, visual_x INTEGER NOT NULL, visual_y INTEGER NOT NULL)");
    }

    boolean contains(String id) {
        try (Cursor cursor = getReadableDatabase().query("shown", new String[]{"id"}, "id=?",
                new String[]{id}, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    boolean reserved(String id) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM photo_reserve WHERE id=?", new String[]{id})) {
            return cursor.moveToFirst();
        }
    }

    boolean reserveFull() {
        return android.database.DatabaseUtils.queryNumEntries(getReadableDatabase(), "photo_reserve") >= RESERVE_SIZE;
    }

    void saveForOffline(String id, String subject, byte[] bytes, long[] visual) {
        if (bytes.length > RESERVE_PHOTO_LIMIT) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!reserveFull() && !contains(id) && !matchesVisual(db, "shown", visual)
                    && !matchesVisual(db, "photo_reserve", visual)) {
                ContentValues row = new ContentValues();
                row.put("id", id);
                row.put("subject", subject);
                row.put("photo", bytes);
                row.put("visual_x", visual[0]);
                row.put("visual_y", visual[1]);
                db.insertWithOnConflict("photo_reserve", null, row, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    SavedPhoto takeOffline() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (Cursor rows = db.rawQuery("SELECT id, subject, photo FROM photo_reserve ORDER BY rowid LIMIT 1", null)) {
            SavedPhoto photo = rows.moveToFirst() ? new SavedPhoto(rows.getString(0), rows.getString(1), rows.getBlob(2)) : null;
            if (photo != null) db.delete("photo_reserve", "id=?", new String[]{photo.id});
            db.setTransactionSuccessful();
            return photo;
        } finally {
            db.endTransaction();
        }
    }

    boolean remember(String id, byte[] bytes, long[] visual) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (matchesVisual(db, "shown", visual)) return false;
            ContentValues row = new ContentValues();
            row.put("id", id);
            row.put("fingerprint", fingerprint(bytes));
            row.put("visual_x", visual[0]);
            row.put("visual_y", visual[1]);
            boolean added = db.insertWithOnConflict("shown", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1;
            db.setTransactionSuccessful();
            return added;
        } finally {
            db.endTransaction();
        }
    }

    private static boolean matchesVisual(SQLiteDatabase db, String table, long[] visual) {
        try (Cursor photos = db.rawQuery("SELECT visual_x, visual_y FROM " + table + " WHERE visual_x IS NOT NULL", null)) {
            while (photos.moveToNext()) {
                if (LandscapeFingerprint.matches(visual, photos.getLong(0), photos.getLong(1))) return true;
            }
        }
        return false;
    }

    private static String fingerprint(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 15, 16));
                hex.append(Character.forDigit(b & 15, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
