package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

import java.util.Arrays
import java.util.Map

import org.junit.Test

class WallpaperCaptionsTest {

    @Test
    fun readsFilenameTabName() {
        val names: Map<String, String> = WallpaperCaptions.parse(Arrays.asList(
                "001.jpg\tRed Spider Nebula · Hubble",
                "002.jpg\t  Falcon 9 at sunset  "))
        assertEquals("Red Spider Nebula · Hubble", names["001.jpg"])
        assertEquals("Falcon 9 at sunset", names["002.jpg"])
    }

    @Test
    fun skipsCommentsBlankAndMalformedLines() {
        val names: Map<String, String> = WallpaperCaptions.parse(Arrays.asList(
                "# captions for the collection", "", "   ", "no-tab-here.jpg Dusk", "\tno file", "003.jpg\t", null))
        assertTrue(names.isEmpty())
    }

    @Test
    fun cutsVeryLongNames() {
        val longName = StringBuilder()
        for (i in 0 until 30) longName.append("word ")
        val names: Map<String, String> = WallpaperCaptions.parse(Arrays.asList("004.jpg\t" + longName))
        assertTrue(names["004.jpg"]!!.length <= WallpaperCaptions.MAX_LENGTH)
    }

    @Test
    fun laterLineWinsForTheSameFile() {
        val names: Map<String, String> = WallpaperCaptions.parse(Arrays.asList("005.jpg\tOld", "005.jpg\tNew"))
        assertEquals("New", names["005.jpg"])
        assertFalse(names.containsKey("006.jpg"))
    }

    @Test
    fun missingFolderHasNoNames() {
        assertTrue(WallpaperCaptions.read(null).isEmpty())
    }
}
