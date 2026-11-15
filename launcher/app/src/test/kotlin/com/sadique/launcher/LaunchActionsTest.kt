package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LaunchActionsTest {
    @Test fun configuredActionOverridesDefaultOnlyForItsPackage() {
        val normal = Any()
        val featured = Any()
        val result = LaunchActions.select("store", mapOf("store" to "FEATURED_LAUNCH"), normal) { pkg, action ->
            assertEquals("store", pkg)
            assertEquals("FEATURED_LAUNCH", action)
            featured
        }
        assertSame(featured, result)
    }

    @Test fun unresolvedActionRetainsNormalEntry() {
        val normal: Any? = Any()
        assertSame(normal, LaunchActions.select("store", mapOf("store" to "MISSING"), normal) { _, _ -> null })
    }

    @Test fun unconfiguredPackageDoesNotTryOtherAppsAction() {
        val normal = Any()
        assertSame(normal, LaunchActions.select("other", mapOf("store" to "FEATURED_LAUNCH"), normal) { _, _ ->
            throw AssertionError("No action configured for this package")
        })
    }

    @Test fun viewPrefixGivesTheUri() {
        assertEquals("bbox://live", LaunchActions.viewUri("view:bbox://live"))
        assertEquals("bbox://live?a=b", LaunchActions.viewUri("view: bbox://live?a=b "))
    }

    @Test fun plainActionOrEmptyViewHasNoUri() {
        assertNull(LaunchActions.viewUri("com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE"))
        assertNull(LaunchActions.viewUri("view:"))
        assertNull(LaunchActions.viewUri(null))
    }

    @Test fun emptyConfigurationRetainsNormalEntryWithoutResolving() {
        val normal = Any()
        assertSame(normal, LaunchActions.select("store", emptyMap(), normal) { _, _ ->
            throw AssertionError("Unnecessary resolution")
        })
    }

    @Test fun inputIdStripsThePrefixAndTrimsTheId() {
        assertEquals("com.mediatek.tvinput/.hdmi.HDMIInputService/HW5",
            LaunchActions.inputId("input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"))
        assertEquals("HW5", LaunchActions.inputId("input: HW5 "))
    }

    @Test fun inputIdIsNullForEverythingElse() {
        assertNull(LaunchActions.inputId(null))
        assertNull(LaunchActions.inputId("FEATURED_LAUNCH"))
        assertNull(LaunchActions.inputId("INPUT:HW5"))
        assertNull(LaunchActions.inputId("not-input:HW5"))
    }

    @Test fun emptyInputIdFallsBackToANormalLaunchInsteadOfAnEmptySwitch() {
        assertNull(LaunchActions.inputId("input:"))
        assertNull(LaunchActions.inputId("input:   "))
    }
}
