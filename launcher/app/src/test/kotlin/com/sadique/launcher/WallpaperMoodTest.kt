package com.sadique.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.List
import java.util.Map
import java.util.Set

import org.junit.Test

/** Plain JVM tests for wallpaper_moods: the time of day's group and the pictures it picks from. */
class WallpaperMoodTest {

    private companion object {
        private const val RISE = 7 * 60 + 20
        private const val SET = 18 * 60 + 20
    }

    private fun at(hour: Int, minute: Int): String {
        return WallpaperMood.timeOfDay(hour * 60 + minute, RISE, SET)
    }

    @Test
    fun timeOfDayFollowsSunriseNoonAndDusk() {
        assertEquals(WallpaperMood.NIGHT, at(0, 30))
        assertEquals(WallpaperMood.NIGHT, at(7, 19))
        assertEquals(WallpaperMood.MORNING, at(7, 20))
        assertEquals(WallpaperMood.MORNING, at(11, 59))
        assertEquals(WallpaperMood.EVENING, at(12, 0))
        assertEquals(WallpaperMood.EVENING, at(18, 49))   // half an hour after sunset is still evening
        assertEquals(WallpaperMood.NIGHT, at(18, 50))
        assertEquals(WallpaperMood.NIGHT, at(23, 59))
    }

    @Test
    fun sunComesFromTheConfiguredPlaceOrTheDefaults() {
        val day = LocalDate.of(2026, 10, 22)
        val zone = ZoneId.of("America/Los_Angeles")
        assertArrayEquals(Sun.riseAndSet(day, 37.35, -121.98, zone), WallpaperMood.sun(day, "37.35", " -121.98 ", zone))
        val defaults = intArrayOf(WallpaperMood.DEFAULT_SUNRISE, WallpaperMood.DEFAULT_SUNSET)
        assertArrayEquals(defaults, WallpaperMood.sun(day, "", "", zone))
        assertArrayEquals(defaults, WallpaperMood.sun(day, "37.35", "", zone))
        assertArrayEquals(defaults, WallpaperMood.sun(day, "north", "-121.98", zone))
        // the sun does not set at the pole in June: the defaults
        assertArrayEquals(defaults, WallpaperMood.sun(LocalDate.of(2026, 6, 21), "89.9", "0", ZoneId.of("UTC")))
    }

    private fun files(vararg names: String): List<File> {
        val out: MutableList<File> = ArrayList()
        for (name in names) out.add(File("/walls", name))
        return out
    }

    private fun names(files: List<File>): List<String> {
        val out: MutableList<String> = ArrayList()
        for (f in files) out.add(f.name)
        return out
    }

    @Test
    fun selectKeepsTheCollectionsOrderAndFallsBackToAll() {
        val all: List<File> = files("a.jpg", "b.jpg", "c.jpg", "d.jpg")
        val groups: MutableMap<String, Set<String>> = HashMap()
        groups[WallpaperMood.NIGHT] = HashSet(Arrays.asList("d.jpg", "a.jpg"))
        groups[WallpaperMood.MORNING] = HashSet(Collections.singletonList("gone.jpg"))
        assertEquals(Arrays.asList("a.jpg", "d.jpg"), names(WallpaperMood.select(all, groups, WallpaperMood.NIGHT)))
        assertEquals(all, WallpaperMood.select(all, groups, WallpaperMood.MORNING))   // none of its pictures here
        assertEquals(all, WallpaperMood.select(all, groups, WallpaperMood.EVENING))   // no group at all
        assertEquals(all, WallpaperMood.select(all, Collections.emptyMap(), WallpaperMood.NIGHT))
    }
}
