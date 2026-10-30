package com.sadique.launcher;

/** Sources and display rules shared by the screensaver and its settings. */
final class LandscapeModel {
    static final String CITY = "Okara, Pakistan";
    static final double LATITUDE = 30.81, LONGITUDE = 73.45;
    private static final String[] PHOTOS = {
        "photo-1470770841072-f978cf4d019e", "photo-1501785888041-af3ef285b470",
        "photo-1464822759023-fed622ff2c3b", "photo-1472396961693-142e6e269027",
        "photo-1441974231531-c6227db76b6e", "photo-1447752875215-b2761acb3c5d"
    };

    static String photoUrl(int index) {
        return "https://images.unsplash.com/" + PHOTOS[Math.floorMod(index, PHOTOS.length)]
                + "?auto=format&fit=crop&w=1920&h=1080&q=80";
    }

    static String weatherUrl(double lat, double lon, boolean fahrenheit) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IllegalArgumentException("Invalid city coordinates");
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&temperature_unit=" + (fahrenheit ? "fahrenheit" : "celsius")
                + "&timezone=auto";
    }

    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Weather";
    }

    static String parseWeather(String body, boolean fahrenheit) throws org.json.JSONException {
        org.json.JSONObject current = new org.json.JSONObject(body).getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        if (!Double.isFinite(temperature)) throw new IllegalArgumentException("Invalid weather");
        return String.format(java.util.Locale.getDefault(), "%.0f°%s · %s", temperature, fahrenheit ? "F" : "C", condition(current.getInt("weather_code")));
    }
}
