package com.sadique.launcher;

/** Sources and display rules shared by the screensaver and its settings. */
final class LandscapeModel {
    static final String CITY = "Okara, Pakistan";
    static final double LATITUDE = 30.81, LONGITUDE = 73.45;
    static String photoUrl(int index) {
        // Bing daily wallpaper: changes every day automatically.
        // idx=0 is today, 1=yesterday... up to 7 days back. (User 2026-10-31.)
        int idx = Math.floorMod(index, 8);
        return "https://www.bing.com/HPImageArchive.aspx?format=js&idx=" + idx + "&n=1&mkt=en-US";
    }

    /** Extracts the full image URL from Bing's HPImageArchive JSON response. */
    static String bingImageUrl(String json) throws org.json.JSONException {
        org.json.JSONObject root = new org.json.JSONObject(json);
        org.json.JSONArray images = root.getJSONArray("images");
        if (images.length() == 0) throw new org.json.JSONException("No images");
        String url = images.getJSONObject(0).getString("url");
        // Bing returns relative URLs like /th?id=OHR..._1920x1080.jpg
        if (url.startsWith("/")) url = "https://www.bing.com" + url;
        // Request 1920x1080 if not already
        if (!url.contains("1920x1080")) {
            url = url.replaceAll("_\d+x\d+\.jpg", "_1920x1080.jpg");
        }
        return url;
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
