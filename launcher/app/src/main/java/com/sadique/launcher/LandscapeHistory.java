package com.sadique.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Persists image identity and fingerprints across screensaver sessions without caching photo files. */
final class LandscapeHistory extends SQLiteOpenHelper {
    LandscapeHistory(Context context) { super(context.getApplicationContext(), "landscape_history.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shown (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE, visual_x INTEGER, visual_y INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_x INTEGER");
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_y INTEGER");
        }
    }

    boolean contains(String id) {
        try (Cursor cursor = getReadableDatabase().query("shown", new String[]{"id"}, "id=?",
                new String[]{id}, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    boolean remember(String id, byte[] bytes, long[] visual) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor shown = db.rawQuery("SELECT visual_x, visual_y FROM shown WHERE visual_x IS NOT NULL", null)) {
                while (shown.moveToNext()) {
                    if (LandscapeFingerprint.matches(visual, shown.getLong(0), shown.getLong(1))) return false;
                }
            }
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
