package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import java.util.ArrayList
import java.util.Arrays
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class RemoteKeysTest {

    private companion object {
        // Key names resolved by a stand-in: android.view.KeyEvent isn't available in JVM tests.
        private fun keyCode(name: String): Int {
            if (name == "KEYCODE_BUTTON_3") return 190
            if (name == "KEYCODE_BUTTON_8") return 195
            return 0
        }

        private fun parse(vararg entries: String): Map<Int, String> {
            return RemoteKeys.parseAppKeys(Arrays.asList(*entries), RemoteKeysTest::keyCode)
        }

        private fun parseHold(vararg entries: String): Map<Int, String> {
            return RemoteKeys.parseHoldAppKeys(Arrays.asList(*entries), RemoteKeysTest::keyCode)
        }
    }

    @Test fun inFrontMatchesTheTargetsPackageOnly() {
        assertTrue(RemoteKeys.inFront("com.vt.tv.tvcenter", "com.vt.tv.tvcenter"))
        assertTrue(RemoteKeys.inFront("io.github.maxhayim.brightness/.QuickActivity", "io.github.maxhayim.brightness"))
        assertFalse(RemoteKeys.inFront("com.vt.tv.tvcenter", "com.sadique.launcher"))
        assertFalse(RemoteKeys.inFront("com.vt.tv.tvcenter", ""))
        assertFalse(RemoteKeys.inFront(null, "com.vt.tv.tvcenter"))
        assertFalse(RemoteKeys.inFront("input:com.vt.source.external/.HdmiInputService/HW2", "com.vt.source.external"))
    }

    @Test fun frontEntriesAreReadApartFromTheRest() {
        val entries: List<String> = Arrays.asList("172:com.vt.tv.tvcenter", "front:172:guide:com.vt.tv.tvcenter",
                "front:scan:490:com.wbd.hbomax", "hold:172:a.b")
        assertEquals("com.vt.tv.tvcenter", RemoteKeys.parseAppKeys(entries, RemoteKeysTest::keyCode)[172])
        val front: Map<Int, String> = RemoteKeys.parseFrontAppKeys(entries, RemoteKeysTest::keyCode)
        assertEquals(1, front.size)
        assertEquals("guide:com.vt.tv.tvcenter", front[172])
        assertEquals("com.wbd.hbomax", RemoteKeys.parseFrontAppScancodes(entries)[490])
        assertTrue(RemoteKeys.parseAppScancodes(entries).isEmpty())
    }

    @Test fun guideTargetNamesItsPackage() {
        assertEquals("com.vt.tv.tvcenter", RemoteKeys.guidePackage("guide:com.vt.tv.tvcenter"))
        assertNull(RemoteKeys.guidePackage("guide:"))
        assertNull(RemoteKeys.guidePackage("com.vt.tv.tvcenter"))
        assertNull(RemoteKeys.guidePackage(null))
    }

    @Test fun keyNamesWorkLikeNumbers() {
        val keys: Map<Int, String> = parse("KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_NOT_A_KEY:a.b")
        assertEquals(1, keys.size)
        assertEquals("com.google.android.youtube.tv", keys[190])
    }

    @Test fun keycodeAndPackage() {
        val keys: Map<Int, String> = parse(
                "190:com.google.android.youtube.tv", " 193 : com.netflix.ninja ")
        assertEquals(2, keys.size)
        assertEquals("com.google.android.youtube.tv", keys[190])
        assertEquals("com.netflix.ninja", keys[193])
    }

    @Test fun badEntriesAreSkipped() {
        val keys: Map<Int, String> = parse(
                "com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", "")
        assertTrue(keys.isEmpty())
    }

    @Test fun theLastEntryForAKeycodeWins() {
        val keys: Map<Int, String> = parse("190:a.b", "190:c.d")
        assertEquals("c.d", keys[190])
    }

    @Test fun scancodeEntries() {
        val entries: List<String> = Arrays.asList(
                "scan:490:com.wbd.hbomax", " scan : 491 : a.b ", "scan:x:a.b", "scan:12:", "190:c.d", "scan:491:e.f")
        val scans: Map<Int, String> = RemoteKeys.parseAppScancodes(entries)
        assertEquals(2, scans.size)
        assertEquals("com.wbd.hbomax", scans[490])
        assertEquals("e.f", scans[491])
        // and the keycode parser leaves scancode entries alone
        assertEquals(1, parse(*entries.toTypedArray()).size)
    }
    @Test fun inputTargets() {
        val hdmi = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"
        val keys: Map<Int, String> = parse("4062:input:" + hdmi)
        assertEquals(hdmi, RemoteKeys.inputId(keys[4062]))
        assertEquals(hdmi, RemoteKeys.inputId(RemoteKeys.parseAppScancodes(
                Arrays.asList("scan:586:input:" + hdmi))[586]))
        assertEquals(null, RemoteKeys.inputId("com.netflix.ninja"))
        assertEquals(null, RemoteKeys.inputId("input: "))
    }

    @Test fun holdEntriesGetTheirOwnMap() {
        val holds: Map<Int, String> = parseHold(
                "hold:KEYCODE_BUTTON_8:org.xbmc.kodi", " hold:190:a.b ", "hold:KEYCODE_NOT_A_KEY:x.y",
                "hold:195:", "190:c.d", "hold:scan:490:d.e", "hold:")
        assertEquals(2, holds.size)
        assertEquals("org.xbmc.kodi", holds[195])
        assertEquals("a.b", holds[190])
        // the normal parser leaves hold entries alone
        assertTrue(parse("hold:190:a.b").isEmpty())
    }

    @Test fun holdScancodeEntries() {
        val scans: Map<Int, String> = RemoteKeys.parseHoldAppScancodes(Arrays.asList(
                "hold:scan:490:com.wbd.hbomax", "hold:scan:491:", "hold:190:a.b", "scan:492:c.d"))
        assertEquals(1, scans.size)
        assertEquals("com.wbd.hbomax", scans[490])
    }

    @Test fun holdTimePicksTheHoldApp() {
        assertEquals("hold.app", RemoteKeys.target("app", "hold.app", RemoteKeys.HOLD_MS))
        assertEquals("hold.app", RemoteKeys.target("app", "hold.app", 5000))
        assertEquals("app", RemoteKeys.target("app", "hold.app", RemoteKeys.HOLD_MS - 1))
        assertEquals("app", RemoteKeys.target("app", null, 5000))
        assertNull(RemoteKeys.target(null, "hold.app", 0))
    }

    @Test fun inputsTargetKeptForAScancode() {
        val scans: Map<Int, String> = RemoteKeys.parseAppScancodes(Arrays.asList("scan:365:inputs"))
        assertEquals(RemoteKeys.INPUTS, scans[365])
        assertNull(RemoteKeys.inputId(RemoteKeys.INPUTS))
    }

    @Test fun actionWordsAreTargets() {
        val keys: Map<Int, String> = parse("190:mute", "KEYCODE_BUTTON_8:screensaver")
        assertEquals("mute", keys[190])
        assertEquals("screensaver", keys[195])
        assertEquals("sleep", parseHold("hold:190:sleep")[190])
        for (word in arrayOf("back", "home", "recents", "mute", "sleep", "screensaver", "settings", "wifi", "ignore", "home_settings")) {
            assertTrue(word, RemoteKeys.isAction(word))
        }
        assertFalse(RemoteKeys.isAction("com.netflix.ninja"))
        assertFalse(RemoteKeys.isAction("input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1"))
        assertFalse(RemoteKeys.isAction("Mute"))
    }

    @Test fun homeSettingsScancodeEntry() {
        val scans: Map<Int, String> = RemoteKeys.parseAppScancodes(
                Arrays.asList("scan:240:home_settings"))
        assertEquals("home_settings", scans[240])
        assertTrue(RemoteKeys.isAction(scans[240]!!))
    }

    @Test fun scanSettingsKeysMigrated() {
        val `in`: List<String> = Arrays.asList(
                "scan:240:com.sadique.launcher/.LauncherSettingsActivity",
                "scan:240:com.sadique.launcher/.QuickSettingsActivity",
                "hold:scan:240:com.sadique.launcher/.LauncherSettingsActivity",
                "scan:240:com.netflix.ninja",
                "scan:241:com.sadique.launcher/.LauncherSettingsActivity",
                "190:home_settings",
                "scan:240:home_settings")
        val out: List<String> = RemoteKeys.migrateScanSettingsKeys(`in`)
        assertEquals("scan:240:home_settings", out[0])
        assertEquals("scan:240:home_settings", out[1])
        assertEquals("hold:scan:240:home_settings", out[2])
        // Unrelated entries are left untouched.
        assertEquals("scan:240:com.netflix.ninja", out[3])
        assertEquals("scan:241:com.sadique.launcher/.LauncherSettingsActivity", out[4])
        assertEquals("190:home_settings", out[5])
        assertEquals("scan:240:home_settings", out[6])
    }

    @Test fun globalActions() {
        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, RemoteKeys.globalAction("back", 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_HOME, RemoteKeys.globalAction("home", 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_RECENTS, RemoteKeys.globalAction("recents", 26))
        assertEquals(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, RemoteKeys.globalAction("sleep", 28))
        // No lock-screen action before Android 9: the key is consumed and nothing happens.
        assertEquals(0, RemoteKeys.globalAction("sleep", 27))
        assertEquals(0, RemoteKeys.globalAction("mute", 34))
        assertEquals(0, RemoteKeys.globalAction("com.netflix.ninja", 34))
    }

    @Test fun anInputTargetIsNotAnActivity() {
        // Its id has slashes, but it must reach the passthrough branch, not ComponentName.unflattenFromString().
        assertFalse(RemoteKeys.isActivity("input:com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413744640"))
        assertTrue(RemoteKeys.isActivity("io.github.maxhayim.brightness/.QuickActivity"))
        assertFalse(RemoteKeys.isActivity("com.netflix.ninja"))
        assertFalse(RemoteKeys.isActivity("/.QuickActivity"))
        assertFalse(RemoteKeys.isActivity(null))
    }

    /** Presses with a timer the test runs by hand, and the targets they open. */
    private class Remote {
        val holds: MutableList<Runnable> = ArrayList()
        val opened: MutableList<String> = ArrayList()
        val presses = RemoteKeys.Presses(object : RemoteKeys.Presses.Timer {
            override fun post(hold: Runnable, delayMs: Long) { holds.add(hold) }
            override fun cancel(hold: Runnable) { holds.remove(hold) }
        })

        fun down(button: String, at: Long, holdApp: String) {
            presses.down(button, at, 0, holdApp, this, opened::add)
        }

        /** A DOWN as one of the two helpers sees it; what it opens is noted with its name. */
        fun down(helper: String, button: String, at: Long, holdApp: String) {
            presses.down(button, at, 0, holdApp, helper) { target -> opened.add(helper + ": " + target) }
        }

        fun up(button: String, at: Long, downTime: Long, app: String, holdApp: String) {
            presses.up(button, at, downTime, false, app, holdApp, opened::add)
        }

        /** HOLD_MS has passed: every hold still pending fires. */
        fun holdTimeout() {
            for (hold in ArrayList(holds)) hold.run()
            holds.clear()
        }
    }

    @Test fun chordedKeysKeepTheirOwnHold() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "a.hold")
        remote.down("1:191:0", 1100, "b.hold")
        // A is released reporting B's down time; it is still A's tap, and only A's hold is cancelled.
        remote.up("1:190:0", 1200, 1100, "a.tap", "a.hold")
        remote.holdTimeout()
        remote.up("1:191:0", 1700, 1100, "b.tap", "b.hold")
        assertEquals(Arrays.asList("a.tap", "b.hold"), remote.opened)
    }

    @Test fun holdsOnTwoDevicesStayApart() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.down("2:190:0", 1000, "hold")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        remote.up("2:190:0", 1900, 1000, "tap", "hold")
        assertEquals(Arrays.asList("hold", "hold"), remote.opened)
    }

    @Test fun bothServicesAnswerEachPressOnce() {
        val remote = Remote()
        // A tap, each event delivered to both services, in either order.
        remote.down("1:190:0", 1000, "hold")
        remote.down("1:190:0", 1000, "hold")
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        // A hold, with the second service's DOWN arriving after the hold fired.
        remote.down("1:190:0", 2000, "hold")
        remote.holdTimeout()
        remote.down("1:190:0", 2000, "hold")
        remote.up("1:190:0", 2800, 2000, "tap", "hold")
        remote.up("1:190:0", 2800, 2000, "tap", "hold")
        assertEquals(Arrays.asList("tap", "hold"), remote.opened)
        assertTrue(remote.holds.isEmpty())
    }

    @Test fun aCanceledUpOpensNothingAndDropsTheHold() {
        val remote = Remote()
        remote.down("1:190:0", 1000, "hold")
        remote.presses.up("1:190:0", 1200, 1000, true, "tap", "hold", remote.opened::add)
        remote.holdTimeout()
        assertTrue(remote.opened.isEmpty())
    }

    @Test fun aHelperUnboundBeforeTheHoldCancelsIt() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.presses.release("home")   // onUnbind, then onDestroy
        remote.holdTimeout()
        assertTrue(remote.opened.isEmpty())
        assertTrue(remote.holds.isEmpty())
    }

    @Test fun losingOneHelperLeavesTheHoldWithTheOther() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.holdTimeout()
        remote.up("1:190:0", 1800, 1000, "tap", "hold")
        assertEquals(Arrays.asList("input: hold"), remote.opened)
    }

    @Test fun losingOneHelperKeepsTheOthersTap() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("input")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        assertEquals(Arrays.asList("tap"), remote.opened)
    }

    @Test fun aHelperThatStopsFilteringReleasesItsHold() {
        // gamepad_passthrough drops the key filter when a controller connects: the UP never reaches that helper.
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.down("input", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.presses.release("input")
        remote.holdTimeout()
        assertTrue(remote.opened.isEmpty())
    }

    @Test fun anUpAfterTheHoldWasReleasedOpensNothing() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "hold")
        remote.presses.release("home")
        remote.up("1:190:0", 1200, 1000, "tap", "hold")
        remote.holdTimeout()
        // The next press is answered as usual.
        remote.down("home", "1:190:0", 2000, "hold")
        remote.up("1:190:0", 2100, 2000, "tap", "hold")
        assertEquals(Arrays.asList("tap"), remote.opened)
    }

    @Test fun releasingAHelperLeavesAnotherButtonsHoldAlone() {
        val remote = Remote()
        remote.down("home", "1:190:0", 1000, "a.hold")
        remote.down("input", "1:191:0", 1100, "b.hold")
        remote.presses.release("home")
        remote.holdTimeout()
        assertEquals(Arrays.asList("input: b.hold"), remote.opened)
    }

    @Test fun anUpWithoutItsDownUsesTheEventsDownTime() {
        val remote = Remote()
        remote.up("1:190:0", 1100, 1000, "tap", "hold")
        remote.up("1:191:0", 1700, 1000, "tap", "hold")
        assertEquals(Arrays.asList("tap", "hold"), remote.opened)
    }

    @Test fun menuReleasedTakesXiaomisInjectedShortPress() {
        // ACTION_DOWN 0, ACTION_UP 1; FLAG_CANCELED 0x20, FLAG_CANCELED_LONG_PRESS 0x100, FLAG_FROM_SYSTEM 0x8.
        assertTrue(RemoteKeys.menuReleased(1, 0))
        assertTrue(RemoteKeys.menuReleased(1, 0x8))
        assertTrue(RemoteKeys.menuReleased(1, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS))
        assertFalse(RemoteKeys.menuReleased(0, 0))
        assertFalse(RemoteKeys.menuReleased(0, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS))
        // A cancelled press elsewhere, long press or not, still opens nothing.
        assertFalse(RemoteKeys.menuReleased(1, 0x20))
        assertFalse(RemoteKeys.menuReleased(1, 0x20 or 0x100 or 0x8))
        assertFalse(RemoteKeys.menuReleased(1, RemoteKeys.XIAOMI_SHORT_MENU_FLAGS and 0x4000.inv()))
    }
}
