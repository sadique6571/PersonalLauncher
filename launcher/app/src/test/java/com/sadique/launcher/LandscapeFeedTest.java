package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeFeedTest {
    @Test public void originalImageRouteGetsFreshSeedsWithoutMetadataRequests() {
        LandscapeFeed.Scene first=LandscapeFeed.photo(1,100),next=LandscapeFeed.photo(1,101);
        assertEquals("https://picsum.photos/seed/pl1_100/1920/1080",first.url);
        assertNotEquals(first.url,next.url); assertNotEquals(first.id,next.id);
        assertTrue(LandscapeFeed.imageUrlAllowed(first.url));
    }
    @Test public void signedSeedsStayWithinTheOriginalHttpsSource() {
        assertTrue(LandscapeFeed.imageUrlAllowed(LandscapeFeed.photo(Integer.MIN_VALUE,Long.MIN_VALUE).url));
    }
    @Test public void unexpectedHostsCredentialsPathsAndHttpAreRejected() {
        assertFalse(LandscapeFeed.imageUrlAllowed("http://picsum.photos/seed/pl1_100/1920/1080"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://picsum.photos.example.org/seed/pl1_100/1920/1080"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://user@picsum.photos/seed/pl1_100/1920/1080"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://picsum.photos/seed/pl1_100/1920/1080/other"));
    }
}
