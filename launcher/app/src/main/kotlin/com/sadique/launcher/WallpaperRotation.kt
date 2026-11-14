package com.sadique.launcher

import java.io.File
import java.util.Locale
import java.util.Random

object WallpaperRotation {
    const val ORDER_SEQUENTIAL = "sequential"
    const val ORDER_SHUFFLE = "shuffle"
    const val CHOICE_NONE = "none"

    @JvmStatic
    fun validOrder(order: String?): Boolean = order == ORDER_SEQUENTIAL || order == ORDER_SHUFFLE

    @JvmStatic
    fun images(directory: File?): List<File> {
        val entries = directory?.listFiles() ?: return ArrayList()
        entries.sortBy { it.name }
        val result = ArrayList<File>()
        for (entry in entries) {
            val name = entry.name.lowercase(Locale.ROOT)
            if (entry.isFile && entry.length() > 0 && entry.length() <= 10 * 1024 * 1024 &&
                (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                result.add(entry)
                if (result.size == 100) break
            }
        }
        return result
    }

    @JvmStatic
    fun find(images: List<File>, name: String?): File? = images.firstOrNull { it.name == name }

    /** A null result delegates the choice to the theme. An empty result disables the wallpaper. */
    @JvmStatic
    fun chosen(collection: List<File>, single: File?, choice: String?): List<File>? {
        if (choice == null) return null
        if (choice == CHOICE_NONE) return emptyList()
        val picked = find(collection, choice) ?: single?.takeIf { it.name == choice }
        return picked?.let(::listOf)
    }

    @JvmStatic
    fun pick(images: List<File>, previous: String?, random: Random): File? {
        val candidates = ArrayList(images)
        if (candidates.size > 1) candidates.removeAll { it.name == previous }
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }

    @JvmStatic
    fun next(images: List<File>, previous: String?): File? {
        if (images.isEmpty()) return null
        val index = images.indexOfFirst { it.name == previous }
        return images[(index + 1) % images.size]
    }

    @JvmStatic
    fun pick(images: List<File>, previous: String?, order: String?, random: Random): File? {
        return if (order == ORDER_SEQUENTIAL) next(images, previous) else pick(images, previous, random)
    }

    @JvmStatic
    fun due(previous: Long, now: Long, interval: Long): Boolean {
        // The minute tick can arrive a little early. Use bounded slack to avoid skipping it.
        val slack = minOf(2_000L, interval / 10)
        return interval > 0 && (previous == 0L || now < previous || now - previous >= interval - slack)
    }
}
