package com.sadique.launcher;

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalAdbCandidatesTest {
    @Test public void staleFirstRecordDoesNotHideCurrentEndpoint() throws Exception {
        LocalAdbCandidates candidates=new LocalAdbCandidates();
        candidates.add(31001); candidates.add(42002); candidates.add(31001);
        assertEquals(List.of(31001,42002),candidates.await(100,0));
    }
    @Test public void collectsLaterRecordDuringQuietWindow() throws Exception {
        LocalAdbCandidates candidates=new LocalAdbCandidates(); candidates.add(31001);
        Thread later=new Thread(() -> { try { Thread.sleep(20); } catch (InterruptedException error) { return; } candidates.add(42002); });
        later.start();
        assertEquals(List.of(31001,42002),candidates.await(1000,100)); later.join();
    }
    @Test public void boundsRecordsAndRejectsInvalidPorts() throws Exception {
        LocalAdbCandidates candidates=new LocalAdbCandidates(); candidates.add(0); candidates.add(65536);
        for (int i=1;i<=30;i++) candidates.add(i);
        assertEquals(16,candidates.await(100,0).size());
    }
    @Test public void discoveryFailureDoesNotWaitFullTimeout() throws Exception {
        LocalAdbCandidates candidates=new LocalAdbCandidates(); candidates.finish();
        try { candidates.await(15000,750); fail(); } catch (IOException expected) { }
    }
}
