package com.sadique.launcher;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherClientTest {
    private static final String PRIMARY = "{\"current\":{\"temperature_2m\":24,\"weather_code\":0}}";
    private static final String FALLBACK = "{\"current_condition\":[{\"temp_C\":\"22\",\"temp_F\":\"72\",\"weatherDesc\":[{\"value\":\"Partly cloudy\"}]}]}";

    @Test public void primarySuccessDoesNotCallFallback() throws Exception {
        int[] calls = {0};
        assertEquals("24°C · Clear", WeatherClient.fetch(30.81, 73.45, false, url -> { calls[0]++; return PRIMARY; }));
        assertEquals(1, calls[0]);
    }
    @Test public void server503UsesSameCoordinatesOnIndependentProvider() throws Exception {
        String value = WeatherClient.fetch(30.81, 73.45, false, url -> {
            if (url.startsWith("https://api.open-meteo.com/")) throw new IOException("HTTP 503");
            assertEquals("https://wttr.in/30.81,73.45?format=j1&lang=en", url);
            return FALLBACK;
        });
        assertEquals("22°C · Partly cloudy", value);
    }
    @Test public void fallbackHonoursFahrenheit() throws Exception {
        assertEquals("72°F · Partly cloudy", WeatherClient.parseFallback(FALLBACK, true));
    }
    @Test public void bothFailuresPreservePrimaryErrorForDiagnosis() throws Exception {
        try {
            WeatherClient.fetch(0, 0, false, url -> { throw new IOException(url); });
            fail("Expected provider failure");
        } catch (IOException failed) { assertEquals(1, failed.getSuppressed().length); }
    }
}
