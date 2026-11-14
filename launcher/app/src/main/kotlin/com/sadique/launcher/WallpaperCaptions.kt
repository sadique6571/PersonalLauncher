package com.sadique.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** Reads optional display names for a wallpaper collection. */
object WallpaperCaptions {
    const val FILE_NAME = "captions.txt"
    const val MAX_LENGTH = 80

    @JvmStatic
    fun parse(lines: List<String?>): Map<String, String> {
        val names = HashMap<String, String>()
        for (line in lines) {
            val content = line?.trim { it <= ' ' } ?: continue
            if (content.isEmpty() || content.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val file = line.substring(0, tab).trim { it <= ' ' }
            val name = line.substring(tab + 1).trim { it <= ' ' }
            if (file.isEmpty() || name.isEmpty()) continue
            names[file] = name.take(MAX_LENGTH).trim { it <= ' ' }
        }
        return names
    }

    @JvmStatic
    fun read(folder: File?): Map<String, String> {
        val file = folder?.resolve(FILE_NAME) ?: return emptyMap()
        if (!file.isFile || file.length() > 256 * 1024) return emptyMap()
        return try {
            parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
