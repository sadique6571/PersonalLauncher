package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeModelTest {
    @Test public void photoSeedsAreUniqueAndAlwaysUseHttps() {
        // Bing daily wallpaper API
        assertTrue(LandscapeModel.photoUrl(0).startsWith("https://www.bing.com/HPImageArchive.aspx"));
        assertTrue(LandscapeModel.photoUrl(0).contains("idx=0"));
        assertNotEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(1));
        assertEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(8)); // wraps every 8
    }
    @Test public void bingImageUrlParsesJson() throws Exception {
        String json = "{\"images\":[{\"url\":\"/th?id=OHR.Test_1920x1080.jpg\"}]}";
        String url = LandscapeModel.bingImageUrl(json);
        assertTrue(url.startsWith("https://www.bing.com/th?id="));
        assertTrue(url.contains("1920x1080"));
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
