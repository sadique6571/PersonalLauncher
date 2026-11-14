package com.sadique.launcher

import org.junit.Test

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.List
import java.util.Map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Plain JVM tests for the saved tile order: file format, dock_apps reset and the sort step. */
class OrderTest {

    private companion object {
        private val DOCK: List<String> = Arrays.asList("com.plexapp.android", "com.netflix.ninja")
    }

    @Test
    fun roundTrips() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A", "c/.C"))
        val back = Order.decode(Order.encode(o))!!
        assertEquals(o.dock, back.dock)
        assertEquals(o.keys, back.keys)
    }

    @Test
    fun dockedRoundTrips() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A", "c/.C"), 2)
        val text = Order.encode(o)
        assertEquals("tvhome-order 1\ndock com.plexapp.android,com.netflix.ninja\ndocked 2\nb/.B\na/.A\nc/.C\n", text)
        val back = Order.decode(text)!!
        assertEquals(2, back.docked)
        assertEquals(o.keys, back.keys)
    }

    @Test
    fun anOrderWithoutDockedLeavesTheDockToDockApps() {
        val back = Order.decode("tvhome-order 1\ndock x\na/.A\nb/.B\n")!!
        assertEquals(-1, back.docked)
        assertEquals(Arrays.asList("a/.A", "b/.B"), back.keys)
        assertEquals(-1, Order.docked(back, Collections.singletonList("x")))
        assertTrue(Order.encode(Order("x", Arrays.asList("a/.A"))).indexOf("docked") < 0)
    }

    @Test
    fun dockedFollowsTheDockAppsCheck() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A"), 1)
        assertEquals(1, Order.docked(o, DOCK))
        assertEquals(-1, Order.docked(o, Collections.singletonList("com.plexapp.android")))
        assertEquals(-1, Order.docked(null, DOCK))
    }

    @Test
    fun rejectsABadDockedLine() {
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked two\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked -1\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\ndock x\ndocked 3\na/.A\n"))   // more docked than keys
    }

    @Test
    fun emptyOrderRoundTrips() {
        val back = Order.decode(Order.encode(Order("", Collections.emptyList())))!!
        assertEquals("", back.dock)
        assertTrue(back.keys.isEmpty())
    }

    @Test
    fun rejectsWhatIsNotAnOrder() {
        assertNull(Order.decode(null))
        assertNull(Order.decode(""))
        assertNull(Order.decode("{\"version\":2}"))
        assertNull(Order.decode("tvhome-order 2\ndock x\na/.A\n"))
        assertNull(Order.decode("tvhome-order 1\na/.A\n"))                 // no dock line
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\n\nb/.B\n"))  // blank key in the middle
        assertNull(Order.decode("tvhome-order 1\ndock x\na/.A\na/.A\n"))    // duplicate key
    }

    @Test
    fun ranksFollowTheSavedOrder() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A"))
        val r: Map<String, Int> = Order.ranks(o, DOCK)
        assertEquals(Integer.valueOf(0), r["b/.B"])
        assertEquals(Integer.valueOf(1), r["a/.A"])
    }

    @Test
    fun changedDockAppsDropTheSavedOrder() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("b/.B", "a/.A"))
        assertTrue(Order.ranks(o, Arrays.asList("com.netflix.ninja", "com.plexapp.android")).isEmpty())
        assertTrue(Order.ranks(o, Collections.singletonList("com.plexapp.android")).isEmpty())
        assertTrue(Order.ranks(null, DOCK).isEmpty())
    }

    @Test
    fun savedTilesComeFirstAndNewOnesKeepTheirOrderAfter() {
        val o = Order(Order.dockKey(DOCK), Arrays.asList("c/.C", "a/.A", "gone/.Gone"))
        val r: Map<String, Int> = Order.ranks(o, DOCK)
        // "n" and "m" are new installs: unranked, so the usual rule (here: list order) decides between them.
        val tiles: MutableList<String> = ArrayList(Arrays.asList("m/.M", "a/.A", "n/.N", "c/.C"))
        val usual: MutableList<String> = ArrayList(tiles)
        tiles.sortWith { x, y ->
            val c = Order.compare(r, x, y)
            if (c != 0) c else Integer.compare(usual.indexOf(x), usual.indexOf(y))
        }
        assertEquals(Arrays.asList("c/.C", "a/.A", "m/.M", "n/.N"), tiles)
    }
}
