package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class LauncherPinTest {
    @Test fun validatesPinFormat() {
        assertTrue(LauncherPin.valid("1234")); assertTrue(LauncherPin.valid("12345678"))
        assertFalse(LauncherPin.valid("123")); assertFalse(LauncherPin.valid("123456789"))
        assertFalse(LauncherPin.valid("abcd")); assertFalse(LauncherPin.valid(null))
    }
    @Test fun usesSaltAndRejectsIncorrectOrCorruptedCredentials() {
        val first = LauncherPin.encode("0123")
        val second = LauncherPin.encode("0123")
        assertNotEquals(first, second); assertNotEquals("0123", first)
        assertTrue(LauncherPin.matches("0123", first)); assertFalse(LauncherPin.matches("0124", first))
        assertFalse(LauncherPin.matches("0123", "broken")); assertFalse(LauncherPin.matches("0123", "a:b"))
    }
}
