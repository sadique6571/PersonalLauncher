package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class LauncherPinTest {
    @Test public void validatesPinFormat() {
        assertTrue(LauncherPin.valid("1234")); assertTrue(LauncherPin.valid("12345678"));
        assertFalse(LauncherPin.valid("123")); assertFalse(LauncherPin.valid("123456789"));
        assertFalse(LauncherPin.valid("abcd")); assertFalse(LauncherPin.valid(null));
    }
    @Test public void usesSaltAndRejectsIncorrectOrCorruptedCredentials() throws Exception {
        String first = LauncherPin.encode("0123"), second = LauncherPin.encode("0123");
        assertNotEquals(first, second); assertFalse(first.contains("0123"));
        assertTrue(LauncherPin.matches("0123", first)); assertFalse(LauncherPin.matches("0124", first));
        assertFalse(LauncherPin.matches("0123", "broken")); assertFalse(LauncherPin.matches("0123", "a:b"));
    }
}
