package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.Collections
import java.util.List
import java.util.Random

import org.junit.Test

class WallpaperRotationTest {

    private fun touch(dir: File, name: String, bytes: Int): File {
        val f = File(dir, name)
        FileOutputStream(f).use { out ->
            out.write(ByteArray(bytes))
        }
        return f
    }

    @Test
    fun imagesKeepsPicturesOnlySortedByName() {
        val dir = Files.createTempDirectory("walls").toFile()
        touch(dir, "b.JPG", 10)
        touch(dir, "a.png", 10)
        touch(dir, "notes.txt", 10)
        touch(dir, "empty.jpg", 0)
        File(dir, "folder.jpg").mkdir()
        val images: List<File> = WallpaperRotation.images(dir)
        assertEquals(Arrays.asList("a.png", "b.JPG"), names(images))
        assertTrue(WallpaperRotation.images(null).isEmpty())
        assertTrue(WallpaperRotation.images(File(dir, "missing")).isEmpty())
    }

    @Test
    fun pickAvoidsThePreviousPictureWhenThereIsAnother() {
        val images: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"))
        val random = Random(1)
        for (i in 0 until 20) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", random)!!.name)
        }
        assertEquals("a.jpg", WallpaperRotation.pick(Collections.singletonList(File("a.jpg")), "a.jpg", random)!!.name)
        assertNull(WallpaperRotation.pick(Collections.emptyList(), "", random))
    }

    @Test
    fun sequentialAdvancesInSortedOrderAndWraps() {
        val images: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"), File("c.jpg"))
        assertEquals("b.jpg", WallpaperRotation.next(images, "a.jpg")!!.name)
        assertEquals("c.jpg", WallpaperRotation.next(images, "b.jpg")!!.name)
        assertEquals("a.jpg", WallpaperRotation.next(images, "c.jpg")!!.name)
        // A first run, or an image removed from the collection, starts at the first picture.
        assertEquals("a.jpg", WallpaperRotation.next(images, "")!!.name)
        assertEquals("a.jpg", WallpaperRotation.next(images, "gone.jpg")!!.name)
        assertEquals("only.jpg", WallpaperRotation.next(
                Collections.singletonList(File("only.jpg")), "only.jpg")!!.name)
        assertNull(WallpaperRotation.next(Collections.emptyList(), ""))
    }

    @Test
    fun pickUsesTheRequestedOrder() {
        val images: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"), File("c.jpg"))
        val random = Random(1)
        assertEquals("b.jpg", WallpaperRotation.pick(images, "a.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random)!!.name)
        assertEquals("a.jpg", WallpaperRotation.pick(images, "c.jpg", WallpaperRotation.ORDER_SEQUENTIAL, random)!!.name)
        for (i in 0 until 20) {
            assertNotEquals("a.jpg", WallpaperRotation.pick(
                    images, "a.jpg", WallpaperRotation.ORDER_SHUFFLE, random)!!.name)
        }
        // Unknown order keeps the historical shuffle rather than failing or blanking.
        assertNotEquals("a.jpg", WallpaperRotation.pick(images, "a.jpg", "random", random)!!.name)
    }

    @Test
    fun findsTheFirstPictureWhenThePreviousOneIsGone() {
        val images: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"))
        assertEquals("a.jpg", WallpaperRotation.next(images, "deleted.jpg")!!.name)
    }

    @Test
    fun findMatchesByName() {
        val images: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"))
        assertEquals("b.jpg", WallpaperRotation.find(images, "b.jpg")!!.name)
        assertNull(WallpaperRotation.find(images, "c.jpg"))
    }

    @Test
    fun anOnTvPickReplacesTheRotation() {
        val collection: List<File> = Arrays.asList(File("a.jpg"), File("b.jpg"), File("c.jpg"))
        val single = File("wallpaper.jpg")
        // No choice, or a picture that has gone: the theme's rotation decides.
        assertNull(WallpaperRotation.chosen(collection, single, null))
        assertNull(WallpaperRotation.chosen(collection, single, "gone.jpg"))
        assertNull(WallpaperRotation.chosen(collection, null, "wallpaper.jpg"))
        // None: no wallpaper at all.
        assertTrue(WallpaperRotation.chosen(collection, single, WallpaperRotation.CHOICE_NONE)!!.isEmpty())
        assertTrue(WallpaperRotation.chosen(Collections.emptyList(), null, WallpaperRotation.CHOICE_NONE)!!.isEmpty())
        // A picked picture is the only one shown, so the rotation can't move past it.
        assertEquals(Collections.singletonList("b.jpg"), names(WallpaperRotation.chosen(collection, single, "b.jpg")!!))
        assertEquals(Collections.singletonList("wallpaper.jpg"),
                names(WallpaperRotation.chosen(collection, single, "wallpaper.jpg")!!))
        // Picking again simply replaces the earlier pick.
        assertEquals(Collections.singletonList("c.jpg"), names(WallpaperRotation.chosen(collection, single, "c.jpg")!!))
    }

    @Test
    fun homeRebuildsWhenTheSettingsRevisionMoved() {
        assertFalse(Config.settingsChanged(0, 0))
        assertFalse(Config.settingsChanged(3, 3))
        assertTrue(Config.settingsChanged(0, 1))
        assertTrue(Config.settingsChanged(4, 0))   // settings cleared
    }

    @Test
    fun dueOnlyWithRotationOnAndTheIntervalPassed() {
        val minute = 60_000L
        assertFalse(WallpaperRotation.due(0, 10 * minute, 0))             // rotation off
        assertTrue(WallpaperRotation.due(0, 10 * minute, 5 * minute))      // never changed yet
        assertFalse(WallpaperRotation.due(8 * minute, 10 * minute, 5 * minute))
        assertTrue(WallpaperRotation.due(5 * minute, 10 * minute, 5 * minute))
        assertTrue(WallpaperRotation.due(20 * minute, 10 * minute, 5 * minute))   // clock went back
    }

    private fun names(files: List<File>): List<String> {
        val out = Array(files.size) { "" }
        for (i in out.indices) out[i] = files[i].name
        return Arrays.asList(*out)
    }
}
