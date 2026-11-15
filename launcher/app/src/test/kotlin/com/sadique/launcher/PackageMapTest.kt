package com.sadique.launcher

import org.junit.Test

import java.util.Arrays
import java.util.Collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PackageMapTest {
    @Test fun anEntryMapsItsPackageToItsAction() {
        val m: Map<String, String> = Config.packageMap(Collections.singletonList(
                "com.telekom.onetv.tv.pl=com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE"))
        assertEquals("com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE", m["com.telekom.onetv.tv.pl"])
    }

    @Test fun spacesAroundTheHalvesAreTrimmed() {
        assertEquals("a.ACTION", Config.packageMap(Collections.singletonList(" a.pkg = a.ACTION ")).get("a.pkg"))
    }

    @Test fun entriesWithoutBothHalvesAreSkipped() {
        assertTrue(Config.packageMap(Arrays.asList("a.pkg", "=a.ACTION", "a.pkg=", "")).isEmpty())
    }

    @Test fun aValueMayContainAnEqualsSign() {
        assertEquals("a=b", Config.packageMap(Collections.singletonList("a.pkg=a=b")).get("a.pkg"))
    }

    @Test fun aLaterEntryForAPackageWins() {
        assertEquals("second", Config.packageMap(Arrays.asList("a.pkg=first", "a.pkg=second")).get("a.pkg"))
    }
}
