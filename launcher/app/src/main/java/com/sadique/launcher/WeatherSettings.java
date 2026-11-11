package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.Locale;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

final class WeatherSettings {
    private static final String LEGACY_PHONE_LABEL = "Phone location";
    private static final String COORDINATES_PREFIX = "Coordinates ";
    private static String attemptedLocation;

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
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint("Place name (optional)");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.addView(input);
        fields.addView(name);
        AlertDialog dialog = new ThemeDialogBuilder(activity)
                .setTitle("Weather location from phone")
                .setMessage("On Personal Launcher Mobile, open Settings and choose Weather code for TV. Place names © OpenStreetMap contributors.")
                .setView(fields)
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
            String nameOverride = name.getText().toString().trim();
            saveLocation(activity, nameOverride.isEmpty() ? coordinatesLabel(latitude, longitude) : nameOverride,
                    latitude, longitude);
            if (nameOverride.isEmpty()) resolvePhonePlace(activity, true);
            dialog.dismiss();
        });
    }

    private static String coordinatesLabel(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%s%.2f°, %.2f°", COORDINATES_PREFIX, latitude, longitude);
    }

    /** Called on the main thread. Save the weather coordinates independently of the place lookup. */
    static void resolvePhonePlace(Context context, boolean explicit) {
        SharedPreferences prefs = ThemePreferences.get(context);
        String label = prefs.getString("weather_city", "");
        if (!LEGACY_PHONE_LABEL.equals(label) && !label.startsWith(COORDINATES_PREFIX)) return;
        String latitude = prefs.getString("weather_lat", "");
        String longitude = prefs.getString("weather_lon", "");
        double lat, lon;
        try {
            lat = Double.parseDouble(latitude);
            lon = Double.parseDouble(longitude);
        } catch (NumberFormatException invalid) {
            return;
        }
        if (!(lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180)) return;
        String key = latitude + "," + longitude;
        if (!explicit && key.equals(attemptedLocation)) return;
        attemptedLocation = key;
        String fallback = coordinatesLabel(lat, lon);
        if (!fallback.equals(label)) prefs.edit().putString("weather_city", fallback).apply();
        new Thread(() -> {
            String place;
            try {
                JSONObject result = new JSONObject(OnlineContent.json(
                        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14"
                                + "&addressdetails=1&lat=" + lat + "&lon=" + lon));
                place = phonePlaceName(result);
            } catch (Exception unavailable) {
                return;
            }
            if (place.isEmpty()) return;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (latitude.equals(prefs.getString("weather_lat", ""))
                        && longitude.equals(prefs.getString("weather_lon", ""))
                        && fallback.equals(prefs.getString("weather_city", ""))) {
                    prefs.edit().putString("weather_city", place).apply();
                }
            });
        }, "weather-phone-place").start();
    }

    private static String phonePlaceName(JSONObject result) {
        JSONObject address = result.optJSONObject("address");
        if (address == null) return "";
        String place = "";
        for (String field : new String[]{"village", "hamlet", "town", "city", "municipality",
                "suburb", "county", "state_district"}) {
            place = address.optString(field, "").trim();
            if (!place.isEmpty()) break;
        }
        if (place.isEmpty()) return "";
        String country = address.optString("country", "").trim();
        return country.isEmpty() ? place : place + ", " + country;
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
