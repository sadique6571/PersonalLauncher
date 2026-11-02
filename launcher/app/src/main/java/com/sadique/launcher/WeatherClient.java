package com.sadique.launcher;

/** One bounded weather request with an independent provider when the first source fails. */
final class WeatherClient {
    interface Loader { String json(String address) throws Exception; }
    private WeatherClient() { }

    static String fetch(double lat, double lon, boolean fahrenheit) throws Exception {
        return fetch(lat, lon, fahrenheit, OnlineContent::json);
    }

    static String fetch(double lat, double lon, boolean fahrenheit, Loader loader) throws Exception {
        String primary = LandscapeModel.weatherUrl(lat, lon, fahrenheit);
        try {
            return LandscapeModel.parseWeather(loader.json(primary), fahrenheit);
        } catch (Exception firstFailure) {
            if (Thread.currentThread().isInterrupted()) throw firstFailure;
            try {
                return parseFallback(loader.json("https://wttr.in/" + lat + "," + lon + "?format=j1&lang=en"), fahrenheit);
            } catch (Exception fallbackFailure) {
                fallbackFailure.addSuppressed(firstFailure);
                throw fallbackFailure;
            }
        }
    }

    static String parseFallback(String body, boolean fahrenheit) throws org.json.JSONException {
        org.json.JSONObject current = new org.json.JSONObject(body).getJSONArray("current_condition").getJSONObject(0);
        double temperature = current.getDouble(fahrenheit ? "temp_F" : "temp_C");
        if (!Double.isFinite(temperature)) throw new IllegalArgumentException("Invalid weather");
        String condition = HomeLibraryModel.title(current.getJSONArray("weatherDesc").getJSONObject(0).getString("value"));
        if (condition.isEmpty()) throw new IllegalArgumentException("Missing weather condition");
        return String.format(java.util.Locale.getDefault(), "%.0f°%s · %s", temperature, fahrenheit ? "F" : "C", condition);
    }
}
