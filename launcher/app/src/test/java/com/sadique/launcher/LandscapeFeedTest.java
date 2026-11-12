package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeFeedTest {
    @Test public void liveLandscapeFeedsRejectCouplesAndKissingEvenWhenTheTitleLooksSafe() {
        assertTrue(LandscapeFeed.landscapeSubject("North Window", "Arches National Park"));
        assertTrue(LandscapeFeed.landscapeSubject("Grand Canyon from space", "Seen from the ISS"));
        assertFalse(LandscapeFeed.landscapeSubject("The forest", "A couple kissing beside the trees"));
        assertFalse(LandscapeFeed.landscapeSubject("Lake in spring", "A crowd at a wedding"));
        assertFalse(LandscapeFeed.landscapeSubject("Evening", "A candid scene"));
    }

    @Test public void keepsLandscapeDimensionsAndNoAttributionLicenses() {
        assertTrue(LandscapeFeed.wide(1280, 853));
        assertFalse(LandscapeFeed.wide(853, 1280));
        assertTrue(LandscapeFeed.wide(1280, 960));
        assertFalse(LandscapeFeed.wide(1280, 1000));
        assertFalse(LandscapeFeed.wide(640, 360));
        assertTrue(LandscapeFeed.commonsLicense("Public domain"));
        assertTrue(LandscapeFeed.commonsLicense("CC0 1.0"));
        assertFalse(LandscapeFeed.commonsLicense("CC BY-SA 4.0"));
    }

    @Test public void imageRoutesStayOnTheExpectedHttpsHosts() {
        assertTrue(LandscapeFeed.imageUrlAllowed("https://www.bing.com/th?id=OHR.NorthWindow_EN-US1170338293_1920x1080.jpg"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://images.unsplash.com/photo-1745690720220-24e337e571c7?w=1600"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://thumb.wikimedia.org/wikipedia/commons/thumb/f/fb/Mountains.JPG/1920px-Mountains.JPG"));
        assertTrue(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov/image/iss074e0208848/iss074e0208848~medium.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://www.bing.com/HPImageArchive.aspx?format=js"));
        assertFalse(LandscapeFeed.imageUrlAllowed("http://thumb.wikimedia.org/wikipedia/commons/landscape.jpg"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://user@images.unsplash.com/photo-1745690720220-24e337e571c7"));
        assertFalse(LandscapeFeed.imageUrlAllowed("https://images-assets.nasa.gov.evil.example/image/id/id~medium.jpg"));
    }
}
