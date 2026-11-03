package com.sadique.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** IDs and content hashes only: shown photos survive sessions/reboots without storing image files. */
final class LandscapeHistory extends SQLiteOpenHelper {
    LandscapeHistory(Context context) { super(context.getApplicationContext(),"landscape_history.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shown (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    boolean contains(String id) {
        try (Cursor cursor=getReadableDatabase().query("shown",new String[]{"id"},"id=?",new String[]{id},null,null,null,"1")) {
            return cursor.moveToFirst();
        }
    }
    boolean remember(String id,byte[] bytes) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex=new StringBuilder();
            for (byte b:digest) { hex.append(Character.forDigit((b>>>4)&15,16)); hex.append(Character.forDigit(b&15,16)); }
            ContentValues row=new ContentValues(); row.put("id",id); row.put("fingerprint",hex.toString());
            return getWritableDatabase().insertWithOnConflict("shown",null,row,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
