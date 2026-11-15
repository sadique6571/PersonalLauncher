package com.sadique.launcher

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class HomeWallpaperCacheTest {
    @Test fun snapshotSurvivesASecondReadAndAtomicReplacement() {
        val directory = Files.createTempDirectory("home-photo").toFile()
        val file = File(directory, "photo.bin")
        try {
            HomeWallpaperCache.write(file, "https://www.bing.com/first", 123, byteArrayOf(1, 2, 3))
            val first = HomeWallpaperCache.read(file)!!
            assertEquals("https://www.bing.com/first", first.url)
            assertEquals(123, first.checkedAt)
            assertArrayEquals(byteArrayOf(1, 2, 3), first.image)
            HomeWallpaperCache.write(file, "https://www.bing.com/second", 456, byteArrayOf(4, 5))
            val second = HomeWallpaperCache.read(file)!!
            assertEquals("https://www.bing.com/second", second.url)
            assertArrayEquals(byteArrayOf(4, 5), second.image)
            assertFalse(File("$file.new").exists())
        } finally {
            file.delete()
            directory.delete()
        }
    }

    @Test fun interruptedNewWriteDoesNotDamageLastGoodSnapshot() {
        val directory = Files.createTempDirectory("home-photo").toFile()
        val file = File(directory, "photo.bin")
        val next = File("$file.new")
        try {
            HomeWallpaperCache.write(file, "https://www.bing.com/last", 100, byteArrayOf(7))
            Files.write(next.toPath(), byteArrayOf(1, 2))
            assertArrayEquals(byteArrayOf(7), HomeWallpaperCache.read(file)!!.image)
        } finally {
            next.delete()
            file.delete()
            directory.delete()
        }
    }

    @Test fun missingTruncatedAndMalformedSnapshotsAreIgnored() {
        val file = File.createTempFile("home-photo", ".bin")
        try {
            assertNull(HomeWallpaperCache.read(file))
            Files.write(file.toPath(), byteArrayOf(1, 2, 3))
            assertNull(HomeWallpaperCache.read(file))
            HomeWallpaperCache.write(file, "https://www.bing.com/last", 100, byteArrayOf(1, 2, 3))
            RandomAccessFile(file, "rw").use { out -> out.setLength(file.length() - 1) }
            assertNull(HomeWallpaperCache.read(file))
        } finally {
            file.delete()
        }
    }

    @Test fun invalidReplacementKeepsLastGoodSnapshot() {
        val file = File.createTempFile("home-photo", ".bin")
        try {
            HomeWallpaperCache.write(file, "https://www.bing.com/last", 100, byteArrayOf(7))
            try {
                HomeWallpaperCache.write(file, "", 200, byteArrayOf(9))
                fail()
            } catch (expected: IOException) {
            }
            assertArrayEquals(byteArrayOf(7), HomeWallpaperCache.read(file)!!.image)
        } finally {
            file.delete()
        }
    }
}
