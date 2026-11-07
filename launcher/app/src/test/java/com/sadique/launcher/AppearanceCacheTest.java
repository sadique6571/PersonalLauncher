package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppearanceCacheTest {
    @Test public void lightThemeSwitchInvalidatesClockEvenWithTheSameWallpaper() {
        Object photo=new Object();
        ClockSampleKey key=new ClockSampleKey(photo,20,30,0,0xff242d30);
        assertTrue(key.matches(photo,20,30,0,0xff242d30));
        assertFalse(key.matches(photo,20,30,0,0xff0d2b1a));
        assertFalse(key.matches(new Object(),20,30,0,0xff242d30));
        assertFalse(key.matches(photo,21,30,0,0xff242d30));
        assertFalse(key.matches(photo,20,30,10,0xff242d30));
    }
    @Test public void oldArtworkCompletionCannotClearNewThemeRequest() {
        ArtworkRequestState request=new ArtworkRequestState();
        request.begin(1);
        assertTrue(request.pending(1));
        assertFalse(request.pending(2));
        request.begin(2);
        assertFalse(request.finish(1));
        assertTrue(request.pending(2));
        assertTrue(request.finish(2));
        assertFalse(request.pending(2));
        assertFalse(request.finish(2));
    }
    @Test public void appRescanCanRetryAnUnfinishedOlderRequest() {
        ArtworkRequestState request=new ArtworkRequestState();
        request.begin(5);
        assertFalse(request.pending(6));
        request.begin(6);
        assertFalse(request.finish(5));
        assertTrue(request.finish(6));
    }
}
