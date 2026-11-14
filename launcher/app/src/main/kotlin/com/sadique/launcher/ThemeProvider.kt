package com.sadique.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Exposes the existing allowlisted theme files through the manifest-protected provider. */
open class ThemeProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "home_auto_status_v1" -> {
                VolumePanelBinder.enforceShell()
                val host = requireNotNull(context)
                Bundle().apply {
                    putString("status", HomeAutomation.status(host))
                    putBoolean("enabled", HomeAutomation.enabled(host))
                    putString("current_home", HomeAutomation.currentHome(host))
                }
            }
            "volume_auto_setup_v1" -> {
                VolumePanelBinder.enforceShell()
                VolumeAutoStart.setup(requireNotNull(context), arg)
            }
            "volume_auto_status_v1" -> {
                VolumePanelBinder.enforceShell()
                Bundle().apply { putString("status", VolumeAutoStart.status(requireNotNull(context))) }
            }
            VolumePanelBinder.METHOD -> {
                VolumePanelBinder.enforceShell()
                Bundle().apply {
                    putInt("protocol", VolumePanelBinder.PROTOCOL)
                    putBinder("panel", VolumePanelBinder.endpoint())
                }
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun file(uri: Uri): File {
        val path = ThemeFiles.path(uri.pathSegments) ?: throw FileNotFoundException(uri.toString())
        val dir = Config.dir(requireNotNull(context)) ?: throw FileNotFoundException(uri.toString())
        return File(dir, path).also {
            val parent = it.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs()) throw FileNotFoundException(uri.toString())
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = file(uri)
        val flags = if ('w' in mode) {
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        } else ParcelFileDescriptor.MODE_READ_ONLY
        return ParcelFileDescriptor.open(file, flags)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val dir = Config.dir(requireNotNull(context))
        if (dir != null && ThemeFiles.isCollection(uri.pathSegments)) {
            return File(dir, ThemeFiles.WALLPAPERS).listFiles { _, name -> ThemeFiles.collectionImage(name) }
                ?.count { it.delete() } ?: 0
        }
        return try {
            if (file(uri).delete()) 1 else 0
        } catch (_: FileNotFoundException) {
            0
        }
    }

    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
