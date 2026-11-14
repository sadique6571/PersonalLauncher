package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

object WeatherSettings {
    private const val LEGACY_PHONE_LABEL = "Phone location"
    private const val COORDINATES_PREFIX = "Coordinates "
    private val phoneCode = Regex("W[0-9A-Z]{6}")
    private var attemptedLocation: String? = null

    @JvmStatic
    fun show(activity: Activity) {
        val city = EditText(activity).apply {
            setSingleLine(true)
            hint = "City name, e.g. Okara"
        }
        ThemeDialogBuilder(activity).setTitle("Weather city").setView(city)
            .setPositiveButton("Search") { _, _ -> search(activity, city.text.toString().trim { it <= ' ' }) }
            .setNeutralButton("Use phone code") { _, _ -> enterPhoneCode(activity) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun enterPhoneCode(activity: Activity) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(7))
            hint = "7-character code"
        }
        val name = EditText(activity).apply {
            setSingleLine(true)
            hint = "Place name (optional)"
            filters = arrayOf(InputFilter.LengthFilter(80))
        }
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(name)
        }
        val dialog = ThemeDialogBuilder(activity).setTitle("Weather location from phone")
            .setMessage("On Personal Launcher Mobile, open Settings and choose Weather code for TV. Place names © OpenStreetMap contributors.")
            .setView(fields).setPositiveButton("Save", null)
            .setNegativeButton(android.R.string.cancel, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = input.text.toString().trim { it <= ' ' }.uppercase(Locale.ROOT)
            val packed = unpackPhoneCode(code)
            if (packed < 0) {
                input.error = "Check the code on your phone"
                return@setOnClickListener
            }
            val latitude = (packed / 7201) / 20.0 - 90
            val longitude = (packed % 7201) / 20.0 - 180
            val nameOverride = name.text.toString().trim { it <= ' ' }
            saveLocation(activity, nameOverride.ifEmpty { coordinatesLabel(latitude, longitude) }, latitude, longitude)
            if (nameOverride.isEmpty()) resolvePhonePlace(activity, true)
            dialog.dismiss()
        }
    }

    private fun coordinatesLabel(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%s%.2f°, %.2f°", COORDINATES_PREFIX, latitude, longitude)

    @JvmStatic
    fun resolvePhonePlace(context: Context, explicit: Boolean) {
        val prefs = ThemePreferences.get(context)
        val label = prefs.getString("weather_city", "").orEmpty()
        if (label != LEGACY_PHONE_LABEL && !label.startsWith(COORDINATES_PREFIX)) return
        val latitude = prefs.getString("weather_lat", "") ?: return
        val longitude = prefs.getString("weather_lon", "") ?: return
        val lat = latitude.toDoubleOrNull() ?: return
        val lon = longitude.toDoubleOrNull() ?: return
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        val key = "$latitude,$longitude"
        if (!explicit && key == attemptedLocation) return
        attemptedLocation = key
        val fallback = coordinatesLabel(lat, lon)
        if (fallback != label) prefs.edit().putString("weather_city", fallback).apply()
        Thread({
            val place = try {
                phonePlaceName(JSONObject(OnlineContent.json(
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1&lat=$lat&lon=$lon"
                )))
            } catch (_: Exception) {
                return@Thread
            }
            if (place.isEmpty()) return@Thread
            Handler(Looper.getMainLooper()).post {
                if (latitude == prefs.getString("weather_lat", "") && longitude == prefs.getString("weather_lon", "") &&
                    fallback == prefs.getString("weather_city", "")) {
                    prefs.edit().putString("weather_city", place).apply()
                }
            }
        }, "weather-phone-place").start()
    }

    private fun phonePlaceName(result: JSONObject): String {
        val address = result.optJSONObject("address") ?: return ""
        val fields = listOf("village", "hamlet", "town", "city", "municipality", "suburb", "county", "state_district")
        val place = fields.firstNotNullOfOrNull { address.optString(it, "").trim { ch -> ch <= ' ' }.ifEmpty { null } }
            ?: return ""
        val country = address.optString("country", "").trim { it <= ' ' }
        return if (country.isEmpty()) place else "$place, $country"
    }

    private fun unpackPhoneCode(code: String): Long {
        if (!phoneCode.matches(code)) return -1
        val packed = code.substring(1, 6).toLong(36)
        val check = (1..5).sumOf { Character.digit(code[it], 36) * (it * 6 - 1) }
        if (check % 36 != Character.digit(code[6], 36) || packed > 3600L * 7201 + 7200) return -1
        return packed
    }

    private fun saveLocation(activity: Activity, label: String, latitude: Double, longitude: Double) {
        ThemePreferences.get(activity).edit().putString("weather_city", label)
            .putString("weather_lat", latitude.toString()).putString("weather_lon", longitude.toString()).apply()
        Toast.makeText(activity, "Weather location saved", Toast.LENGTH_SHORT).show()
    }

    private data class Place(val label: String, val latitude: Double, val longitude: Double)

    private fun search(activity: Activity, name: String) {
        if (name.isEmpty()) return
        Toast.makeText(activity, "Looking up city…", Toast.LENGTH_SHORT).show()
        Thread({
            val results = try {
                val response = OnlineContent.json("https://geocoding-api.open-meteo.com/v1/search?name=" +
                    Uri.encode(name) + "&count=5&language=en&format=json")
                val cities = JSONObject(response).optJSONArray("results") ?: error("City not found")
                check(cities.length() > 0) { "City not found" }
                List(cities.length()) { index ->
                    val city = cities.getJSONObject(index)
                    val label = city.getString("name") + ", " + city.optString("admin1", "") + ", " + city.optString("country", "")
                    Place(label, city.getDouble("latitude"), city.getDouble("longitude"))
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    if (!activity.isDestroyed && !activity.isFinishing) {
                        Toast.makeText(activity, "City lookup unavailable. Check internet or try another city.", Toast.LENGTH_LONG).show()
                    }
                }
                return@Thread
            }
            Handler(Looper.getMainLooper()).post {
                if (activity.isDestroyed || activity.isFinishing) return@post
                ThemeDialogBuilder(activity).setTitle("Choose weather location")
                    .setItems(results.map { it.label }.toTypedArray()) { _, which ->
                        val place = results[which]
                        saveLocation(activity, place.label, place.latitude, place.longitude)
                    }.show()
            }
        }, "weather-city-search").start()
    }
}
