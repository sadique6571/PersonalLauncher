package com.sadique.launcher;

/** Sources and display rules shared by the screensaver and its settings. */
final class LandscapeModel {
    static final String CITY = "Okara, Pakistan";
    static final double LATITUDE = 30.81, LONGITUDE = 73.45;
    /** Visually reviewed nature landscapes; each caption describes that exact photo. */
    private static final String[][] LANDSCAPES = {
        {"1501785888041-af3ef285b470", "Turquoise lake beneath mountain cliffs"},
        {"1470770841072-f978cf4d019e", "Boathouse on a mountain lake"},
        {"1441974231531-c6227db76b6e", "Sunlight along a forest trail"},
        {"1507525428034-b723cf961d3e", "Waves on a sunlit sandy beach"},
        {"1506905925346-21bda4d32df4", "Snowy mountain peaks above the clouds"},
        {"1469474968028-56623f02e42e", "Sunlit rocky ridge above wooded valleys"},
        {"1470071459604-3b5ec3a7fe05", "Misty green valley beneath a golden sky"},
        {"1509316785289-025f5b846b35", "Red rock mesas in the desert"},
        {"1473448912268-2022ce9509d8", "Forest lake beneath mountain peaks"},
        {"1506744038136-46273834b3fb", "River through a mountain valley"},
        {"1464822759023-fed622ff2c3b", "Snowy mountain range and pine forest"}
    };

    static int landscapeCount() { return LANDSCAPES.length; }
    static int landscapeIndex(int index) { return Math.floorMod(index,LANDSCAPES.length); }
    static String landscapePhotoUrl(int index) {
        return "https://images.unsplash.com/photo-"+LANDSCAPES[landscapeIndex(index)][0]
                +"?fit=max&w=1920&h=1080&q=85&fm=jpg";
    }
    static String landscapeCaption(int index) { return LANDSCAPES[landscapeIndex(index)][1]; }
    static boolean isLandscape(int width,int height) { return height>0 && width>height; }
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
            url = url.replaceAll("_\\d+x\\d+\\.jpg", "_1920x1080.jpg");
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
