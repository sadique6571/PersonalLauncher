package com.sadique.launcher

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Stores a single Home wallpaper snapshot with an atomic file replacement. */
object HomeWallpaperCache {
    private const val MAGIC = 0x504c4231
    private const val LIMIT = 6 * 1024 * 1024

    class Entry(
        @JvmField val url: String,
        @JvmField val checkedAt: Long,
        @JvmField val image: ByteArray
    )

    @JvmStatic
    @Synchronized
    fun read(file: File): Entry? {
        if (!file.isFile || file.length() > LIMIT + 8192L) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readInt() != MAGIC) return null
                val url = input.readUTF()
                val checkedAt = input.readLong()
                val length = input.readInt()
                if (url.isEmpty() || url.length > 4096 || length !in 1..LIMIT) return null
                val image = ByteArray(length)
                input.readFully(image)
                if (input.read() != -1) return null
                Entry(url, checkedAt, image)
            }
        } catch (_: IOException) {
            null
        }
    }

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun write(file: File, url: String?, checkedAt: Long, image: ByteArray) {
        if (url.isNullOrEmpty() || url.length > 4096 || image.size !in 1..LIMIT) {
            throw IOException("Invalid Home wallpaper snapshot")
        }
        val next = File(file.path + ".new")
        try {
            FileOutputStream(next).use { stream ->
                DataOutputStream(BufferedOutputStream(stream)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeUTF(url)
                    output.writeLong(checkedAt)
                    output.writeInt(image.size)
                    output.write(image)
                    output.flush()
                    stream.fd.sync()
                }
            }
            val source = next.toPath()
            val destination = file.toPath()
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (next.exists()) next.delete()
        }
    }
}
