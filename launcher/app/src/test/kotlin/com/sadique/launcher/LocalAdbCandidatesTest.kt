package com.sadique.launcher

import java.io.IOException
import java.util.List
import org.junit.Test
import org.junit.Assert.*

class LocalAdbCandidatesTest {
    @Test fun staleFirstRecordDoesNotHideCurrentEndpoint() {
        val candidates = LocalAdbCandidates()
        candidates.add(31001); candidates.add(42002); candidates.add(31001)
        assertEquals(List.of(31001, 42002), candidates.await(100, 0))
    }
    @Test fun collectsLaterRecordDuringQuietWindow() {
        val candidates = LocalAdbCandidates(); candidates.add(31001)
        val later = Thread {
            try {
                Thread.sleep(20)
                candidates.add(42002)
            } catch (error: InterruptedException) {
            }
        }
        later.start()
        assertEquals(List.of(31001, 42002), candidates.await(1000, 100)); later.join()
    }
    @Test fun boundsRecordsAndRejectsInvalidPorts() {
        val candidates = LocalAdbCandidates(); candidates.add(0); candidates.add(65536)
        for (i in 1..30) candidates.add(i)
        assertEquals(16, candidates.await(100, 0).size)
    }
    @Test fun discoveryFailureDoesNotWaitFullTimeout() {
        val candidates = LocalAdbCandidates(); candidates.finish()
        try { candidates.await(15000, 750); fail() } catch (expected: IOException) { }
    }
}
