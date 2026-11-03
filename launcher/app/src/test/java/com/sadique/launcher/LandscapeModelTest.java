package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeModelTest {
    @Test public void screensaverScenesHaveDistinctPhotosAndMatchingDescriptions() {
        java.util.Set<String> urls=new java.util.HashSet<>();
        for (int i=0;i<LandscapeModel.landscapeCount();i++) {
            String url=LandscapeModel.landscapePhotoUrl(i);
            assertTrue(url.startsWith("https://images.unsplash.com/photo-"));
            assertTrue(urls.add(url));
            String caption=LandscapeModel.landscapeCaption(i);
            assertFalse(caption.isEmpty());
            assertFalse(caption.contains("Photo by"));
            assertFalse(caption.contains("Picsum"));
            assertFalse(caption.contains("1920"));
        }
        assertEquals("Boathouse on a mountain lake",LandscapeModel.landscapeCaption(1));
    }
    @Test public void screensaverRotationWrapsBothDirections() {
        int count=LandscapeModel.landscapeCount();
        assertEquals(LandscapeModel.landscapePhotoUrl(0),LandscapeModel.landscapePhotoUrl(count));
        assertEquals(LandscapeModel.landscapePhotoUrl(count-1),LandscapeModel.landscapePhotoUrl(-1));
        assertEquals(LandscapeModel.landscapeCaption(count-1),LandscapeModel.landscapeCaption(-1));
    }
    @Test public void portraitSquareAndInvalidImagesAreRejected() {
        assertTrue(LandscapeModel.isLandscape(1920,1080));
        assertFalse(LandscapeModel.isLandscape(1080,1920));
        assertFalse(LandscapeModel.isLandscape(1080,1080));
        assertFalse(LandscapeModel.isLandscape(0,0));
        assertFalse(LandscapeModel.isLandscape(1920,-1));
    }
    @Test public void photoSeedsAreUniqueAndAlwaysUseHttps() {
        // Bing daily wallpaper API
        assertTrue(LandscapeModel.photoUrl(0).startsWith("https://www.bing.com/HPImageArchive.aspx"));
        assertTrue(LandscapeModel.photoUrl(0).contains("idx=0"));
        assertNotEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(1));
        assertEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(8)); // wraps every 8
    }
    @Test public void okaraWeatherUsesTheChosenUnit() {
        String url = LandscapeModel.weatherUrl(LandscapeModel.LATITUDE, LandscapeModel.LONGITUDE, false);
        assertTrue(url.contains("latitude=30.81&longitude=73.45"));
        assertTrue(url.contains("temperature_unit=celsius"));
        assertTrue(LandscapeModel.weatherUrl(0, 0, true).contains("temperature_unit=fahrenheit"));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidCoordinatesAreRejected() { LandscapeModel.weatherUrl(Double.NaN, 0, false); }
    @Test public void weatherCodesDoNotInventSunshine() {
        assertEquals("Clear", LandscapeModel.condition(0));
        assertEquals("Rain", LandscapeModel.condition(65));
        assertEquals("Snow", LandscapeModel.condition(75));
        assertEquals("Thunderstorm", LandscapeModel.condition(95));
        assertEquals("Weather", LandscapeModel.condition(-1));
    }
}
