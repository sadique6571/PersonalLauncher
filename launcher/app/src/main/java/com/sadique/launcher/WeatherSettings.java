package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import java.util.Locale;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

final class WeatherSettings {
    static void show(Activity activity) {
        EditText city = new EditText(activity);
        city.setSingleLine(true);
        city.setHint("City name, e.g. Okara");
        new ThemeDialogBuilder(activity).setTitle("Weather city").setView(city)
                .setPositiveButton("Search", (dialog, which) -> search(activity, city.getText().toString().trim()))
                .setNeutralButton("Use phone code", (dialog, which) -> enterPhoneCode(activity))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void enterPhoneCode(Activity activity) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        input.setHint("7-character code");
        AlertDialog dialog = new ThemeDialogBuilder(activity)
                .setTitle("Weather location from phone")
                .setMessage("On Personal Launcher Mobile, open Settings and choose Weather code for TV.")
                .setView(input)
                .setPositiveButton("Save", null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = input.getText().toString().trim().toUpperCase(Locale.ROOT);
            long packed = unpackPhoneCode(code);
            if (packed < 0) {
                input.setError("Check the code on your phone");
                return;
            }
            double latitude = (packed / 7201) / 20.0 - 90;
            double longitude = (packed % 7201) / 20.0 - 180;
            saveLocation(activity, "Phone location", latitude, longitude);
            dialog.dismiss();
        });
    }

    private static long unpackPhoneCode(String code) {
        // W + five base-36 digits + a check digit. Each coordinate is rounded to 0.05°.
        if (!code.matches("W[0-9A-Z]{6}")) return -1;
        long packed = Long.parseLong(code.substring(1, 6), 36);
        int check = 0;
        for (int i = 1; i <= 5; i++) check += Character.digit(code.charAt(i), 36) * (i * 6 - 1);
        if (check % 36 != Character.digit(code.charAt(6), 36) || packed > 3600L * 7201 + 7200)
            return -1;
        return packed;
    }

    private static void saveLocation(Activity activity, String label, double latitude, double longitude) {
        SharedPreferences prefs = ThemePreferences.get(activity);
        prefs.edit().putString("weather_city", label)
                .putString("weather_lat", Double.toString(latitude))
                .putString("weather_lon", Double.toString(longitude)).apply();
        Toast.makeText(activity, "Weather location saved", Toast.LENGTH_SHORT).show();
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
                    new ThemeDialogBuilder(activity).setTitle("Choose weather location").setItems(labels, (dialog, which) -> {
                        saveLocation(activity, labels[which], lat[which], lon[which]);
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
