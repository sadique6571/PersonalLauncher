package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumePanelSessionTest {
    static final class Audio implements VolumePanelSession.Endpoint<String> {
        String current = "stock";
        int writes;
        boolean fail;
        public String get() { return current; }
        public void set(String controller) throws Exception {
            if (fail) throw new Exception("permission refused");
            current = controller; writes++;
        }
    }
    @Test public void normalStopRestoresOriginalOnce() throws Exception {
        Audio audio = new Audio();
        VolumePanelSession<String> session = new VolumePanelSession<>(audio,"stock","relay");
        session.install(); session.install();
        assertEquals("relay",audio.current);
        session.close(); session.close();
        assertEquals("stock",audio.current); assertEquals(2,audio.writes);
    }
    @Test public void systemUiRestartIsNeverOverwritten() throws Exception {
        Audio audio = new Audio();
        VolumePanelSession<String> session = new VolumePanelSession<>(audio,"stock","relay");
        session.install(); audio.current="new-stock"; session.close();
        assertEquals("new-stock",audio.current); assertEquals(1,audio.writes);
    }
    @Test public void registrationRaceIsRefused() throws Exception {
        Audio audio = new Audio();
        VolumePanelSession<String> session = new VolumePanelSession<>(audio,"stock","relay");
        audio.current="new-stock";
        try { session.install(); fail("must refuse stale controller"); }
        catch (IllegalStateException expected) { }
        session.close(); assertEquals(0,audio.writes);
    }
    @Test public void failedRegistrationDoesNotRestoreUnownedController() throws Exception {
        Audio audio = new Audio(); audio.fail=true;
        VolumePanelSession<String> session = new VolumePanelSession<>(audio,"stock","relay");
        try { session.install(); fail("must propagate permission failure"); }
        catch (Exception expected) { }
        session.close(); assertEquals("stock",audio.current); assertEquals(0,audio.writes);
    }
    @Test public void onlyMediaShowUiFlagIsRemoved() {
        assertEquals(0,VolumePanelSession.flags(3,1));
        assertEquals(0x8004,VolumePanelSession.flags(3,0x8005));
        assertEquals(0,VolumePanelSession.flags(3,0));
        assertEquals(0x8005,VolumePanelSession.flags(4,0x8005));
    }
}
