package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class LandscapeModelTest {
    @Test public void photoSeedsAreUniqueAndAlwaysUseHttps() {
        assertTrue(LandscapeModel.photoUrl(0).startsWith("https://picsum.photos/seed/"));
        assertNotEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(1));
        assertNotEquals(LandscapeModel.photoUrl(0), LandscapeModel.photoUrl(6));
        assertNotEquals(LandscapeModel.photoUrl(5), LandscapeModel.photoUrl(-1));
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
