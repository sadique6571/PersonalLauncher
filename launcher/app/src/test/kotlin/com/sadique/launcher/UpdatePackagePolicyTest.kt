package com.sadique.launcher

import java.util.*
import org.junit.Test
import org.junit.Assert.*

class UpdatePackagePolicyTest {
    private val signed: MutableSet<String> = HashSet(Arrays.asList("publisher-certificate"))
    @Test fun onlyStrictlyNewerSamePackageSameSignerIsAccepted() {
        assertTrue(UpdatePackagePolicy.accepts("com.sadique.launcher", "com.sadique.launcher", 49, 50, signed, signed))
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher", "com.other.app", 49, 50, signed, signed))
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher", "com.sadique.launcher", 49, 49, signed, signed))
        assertFalse(UpdatePackagePolicy.accepts("com.sadique.launcher", "com.sadique.launcher", 49, 48, signed, signed))
    }
    @Test fun unsignedForeignAndAdditionalSignersAreRejected() {
        val other: MutableSet<String> = HashSet(Arrays.asList("another-certificate"))
        val extra: MutableSet<String> = HashSet(signed); extra.add("extra")
        assertFalse(UpdatePackagePolicy.accepts("pkg", "pkg", 1, 2, signed, other))
        assertFalse(UpdatePackagePolicy.accepts("pkg", "pkg", 1, 2, signed, extra))
        assertFalse(UpdatePackagePolicy.accepts("pkg", "pkg", 1, 2, Collections.emptySet(), Collections.emptySet()))
    }
    @Test fun shellTargetsRejectInjectedPackagesHashesAndUsers() {
        val hash = String(CharArray(64)).replace('\u0000', 'a')
        for (pkg in arrayOf("com.bad;id", "com.bad\$(id)", "com.bad/path")) {
            try { UpdateInstallCommand.build(pkg, 0, hash); fail() } catch (expected: IllegalArgumentException) { }
        }
        try { UpdateInstallCommand.build("com.sadique.launcher", -1, hash); fail() } catch (expected: IllegalArgumentException) { }
        try { UpdateInstallCommand.build("com.sadique.launcher", 0, "abc;id"); fail() } catch (expected: IllegalArgumentException) { }
    }
}
