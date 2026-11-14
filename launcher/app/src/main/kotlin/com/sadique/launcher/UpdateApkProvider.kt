package com.sadique.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Provides the private verified update APK under the manifest's existing grants. */
class UpdateApkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun file(uri: Uri): File {
        val file = GitHubUpdates.readyFile(context)
        if (uri.path != "/apk" || !file.isFile) throw FileNotFoundException("No verified update")
        return file
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val file = try {
            file(uri)
        } catch (_: FileNotFoundException) {
            return null
        }
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        val values = columns.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> "PersonalLauncher.apk"
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        cursor.addRow(values)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
