package com.sadique.launcher

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/** Persists photo deduplication and a bounded reserve of unseen offline photos. */
class LandscapeHistory(context: Context) : SQLiteOpenHelper(context.applicationContext, "landscape_history.db", null, 3) {
    class SavedPhoto(@JvmField val id: String, @JvmField val subject: String, @JvmField val bytes: ByteArray)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE shown (id TEXT PRIMARY KEY, fingerprint TEXT NOT NULL UNIQUE, visual_x INTEGER, visual_y INTEGER)")
        createReserve(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_x INTEGER")
            db.execSQL("ALTER TABLE shown ADD COLUMN visual_y INTEGER")
        }
        if (oldVersion < 3) createReserve(db)
    }

    private fun createReserve(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photo_reserve (id TEXT PRIMARY KEY, subject TEXT NOT NULL, photo BLOB NOT NULL, visual_x INTEGER NOT NULL, visual_y INTEGER NOT NULL)")
    }

    fun contains(id: String): Boolean {
        return readableDatabase.query("shown", arrayOf("id"), "id=?", arrayOf(id), null, null, "1")
            .use { it.moveToFirst() }
    }

    fun reserved(id: String): Boolean {
        return readableDatabase.rawQuery("SELECT 1 FROM photo_reserve WHERE id=?", arrayOf(id))
            .use { it.moveToFirst() }
    }

    fun reserveFull(): Boolean = DatabaseUtils.queryNumEntries(readableDatabase, "photo_reserve") >= RESERVE_SIZE

    fun saveForOffline(id: String, subject: String, bytes: ByteArray, visual: LongArray) {
        if (bytes.size > RESERVE_PHOTO_LIMIT) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!reserveFull() && !contains(id) && !matchesVisual(db, "shown", visual) &&
                !matchesVisual(db, "photo_reserve", visual)) {
                val row = ContentValues().apply {
                    put("id", id)
                    put("subject", subject)
                    put("photo", bytes)
                    put("visual_x", visual[0])
                    put("visual_y", visual[1])
                }
                db.insertWithOnConflict("photo_reserve", null, row, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun takeOffline(): SavedPhoto? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            return db.rawQuery("SELECT id, subject, photo FROM photo_reserve ORDER BY rowid LIMIT 1", null).use { rows ->
                val photo = if (rows.moveToFirst()) SavedPhoto(rows.getString(0), rows.getString(1), rows.getBlob(2)) else null
                if (photo != null) db.delete("photo_reserve", "id=?", arrayOf(photo.id))
                db.setTransactionSuccessful()
                photo
            }
        } finally {
            db.endTransaction()
        }
    }

    fun remember(id: String, bytes: ByteArray, visual: LongArray): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (matchesVisual(db, "shown", visual)) return false
            val row = ContentValues().apply {
                put("id", id)
                put("fingerprint", fingerprint(bytes))
                put("visual_x", visual[0])
                put("visual_y", visual[1])
            }
            val added = db.insertWithOnConflict("shown", null, row, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            db.setTransactionSuccessful()
            return added
        } finally {
            db.endTransaction()
        }
    }

    private fun matchesVisual(db: SQLiteDatabase, table: String, visual: LongArray): Boolean {
        db.rawQuery("SELECT visual_x, visual_y FROM $table WHERE visual_x IS NOT NULL", null).use { photos ->
            while (photos.moveToNext()) {
                if (LandscapeFingerprint.matches(visual, photos.getLong(0), photos.getLong(1))) return true
            }
        }
        return false
    }

    private fun fingerprint(bytes: ByteArray): String {
        val digest = try {
            MessageDigest.getInstance("SHA-256").digest(bytes)
        } catch (impossible: NoSuchAlgorithmException) {
            throw AssertionError(impossible)
        }
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt()
                append(Character.forDigit((value ushr 4) and 15, 16))
                append(Character.forDigit(value and 15, 16))
            }
        }
    }

    companion object {
        const val RESERVE_SIZE = 6
        const val RESERVE_PHOTO_LIMIT = 900 * 1024
    }
}
