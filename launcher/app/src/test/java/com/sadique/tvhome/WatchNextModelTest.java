package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class WatchNextModelTest {
    @Test public void finishedItemsAndWatchlistsAreNotResumeEntries() {
        assertTrue(WatchNextModel.resumable(0, 20, 100));
        assertFalse(WatchNextModel.resumable(0, 100, 100));
        assertFalse(WatchNextModel.resumable(1, 20, 100));
        assertTrue(WatchNextModel.resumable(0, 0, 0));
    }
    @Test public void progressHandlesMissingAndOutOfRangeDurations() {
        assertEquals(250, WatchNextModel.progress(25, 100));
        assertEquals(0, WatchNextModel.progress(20, 0));
        assertEquals(0, WatchNextModel.progress(-20, 100));
        assertEquals(1000, WatchNextModel.progress(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
