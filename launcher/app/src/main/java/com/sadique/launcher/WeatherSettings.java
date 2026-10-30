package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

final class WeatherSettings {
    static void show(Activity activity) {
        EditText city = new EditText(activity);
        city.setSingleLine(true);
        city.setHint("City name, e.g. Okara");
        new AlertDialog.Builder(activity).setTitle("Weather city").setView(city)
                .setPositiveButton("Search", (dialog, which) -> search(activity, city.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void search(Activity activity, String name) {
        if (name.isEmpty()) return;
        Toast.makeText(activity, "Looking up city…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONArray cities = new JSONObject(OnlineContent.json("https://geocoding-api.open-meteo.com/v1/search?name="
                        + Uri.encode(name) + "&count=5&language=en&format=json")).optJSONArray("results");
                if (cities == null || cities.length() == 0) throw new IllegalArgumentException("City not found");
                String[] labels = new String[cities.length()];
                double[] lat = new double[cities.length()], lon = new double[cities.length()];
                for (int i = 0; i < labels.length; i++) {
                    JSONObject place = cities.getJSONObject(i);
                    labels[i] = place.getString("name") + ", " + place.optString("admin1", "") + ", " + place.optString("country", "");
                    lat[i] = place.getDouble("latitude"); lon[i] = place.getDouble("longitude");
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity.isDestroyed() || activity.isFinishing()) return;
                    new AlertDialog.Builder(activity).setTitle("Choose weather location").setItems(labels, (dialog, which) -> {
                        SharedPreferences prefs = activity.getSharedPreferences("personal_home", 0);
                        prefs.edit().putString("weather_city", labels[which]).putString("weather_lat", Double.toString(lat[which]))
                                .putString("weather_lon", Double.toString(lon[which])).apply();
                        Toast.makeText(activity, "Weather location saved", Toast.LENGTH_SHORT).show();
                    }).show();
                });
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isDestroyed() && !activity.isFinishing())
                        Toast.makeText(activity, "City lookup unavailable. Check internet or try another city.", Toast.LENGTH_LONG).show();
                });
            }
        }, "weather-city-search").start();
    }
}
