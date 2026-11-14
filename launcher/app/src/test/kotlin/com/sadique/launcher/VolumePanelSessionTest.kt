package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class VolumePanelSessionTest {
    class Audio : VolumePanelSession.Endpoint<String> {
        var current = "stock"
        var writes = 0
        var fail = false
        override fun get(): String? { return current }
        override fun set(controller: String) {
            if (fail) throw Exception("permission refused")
            current = controller; writes++
        }
    }
    @Test fun normalStopRestoresOriginalOnce() {
        val audio = Audio()
        val session = VolumePanelSession(audio, "stock", "relay")
        session.install(); session.install()
        assertEquals("relay", audio.current)
        session.close(); session.close()
        assertEquals("stock", audio.current); assertEquals(2, audio.writes)
    }
    @Test fun systemUiRestartIsNeverOverwritten() {
        val audio = Audio()
        val session = VolumePanelSession(audio, "stock", "relay")
        session.install(); audio.current = "new-stock"; session.close()
        assertEquals("new-stock", audio.current); assertEquals(1, audio.writes)
    }
    @Test fun registrationRaceIsRefused() {
        val audio = Audio()
        val session = VolumePanelSession(audio, "stock", "relay")
        audio.current = "new-stock"
        try { session.install(); fail("must refuse stale controller") }
        catch (expected: IllegalStateException) { }
        session.close(); assertEquals(0, audio.writes)
    }
    @Test fun failedRegistrationDoesNotRestoreUnownedController() {
        val audio = Audio(); audio.fail = true
        val session = VolumePanelSession(audio, "stock", "relay")
        try { session.install(); fail("must propagate permission failure") }
        catch (expected: Exception) { }
        session.close(); assertEquals("stock", audio.current); assertEquals(0, audio.writes)
    }
    @Test fun onlyMediaShowUiFlagIsRemoved() {
        assertEquals(0, VolumePanelSession.flags(3, 1))
        assertEquals(0x8004, VolumePanelSession.flags(3, 0x8005))
        assertEquals(0, VolumePanelSession.flags(3, 0))
        assertEquals(0x8005, VolumePanelSession.flags(4, 0x8005))
    }
    @Test fun shutdownBeforeInstallCannotRegisterAfterCleanup() {
        val audio = Audio()
        val session = VolumePanelSession(audio, "stock", "relay")
        session.close()
        try { session.install(); fail("must refuse registration after cleanup") }
        catch (expected: IllegalStateException) { }
        assertEquals("stock", audio.current); assertEquals(0, audio.writes)
    }
}
