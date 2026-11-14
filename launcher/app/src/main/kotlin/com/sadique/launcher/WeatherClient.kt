package com.sadique.launcher

import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

/** Bounded weather request with an independent fallback provider. */
object WeatherClient {
    fun interface Loader {
        @Throws(Exception::class)
        fun json(address: String): String
    }

    @JvmStatic
    @Throws(Exception::class)
    fun fetch(lat: Double, lon: Double, fahrenheit: Boolean): String {
        return fetch(lat, lon, fahrenheit, OnlineContent::json)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun fetch(lat: Double, lon: Double, fahrenheit: Boolean, loader: Loader): String {
        val primary = LandscapeModel.weatherUrl(lat, lon, fahrenheit)
        try {
            return LandscapeModel.parseWeather(loader.json(primary), fahrenheit)
        } catch (firstFailure: Exception) {
            if (Thread.currentThread().isInterrupted) throw firstFailure
            try {
                return parseFallback(loader.json("https://wttr.in/$lat,$lon?format=j1&lang=en"), fahrenheit)
            } catch (fallbackFailure: Exception) {
                fallbackFailure.addSuppressed(firstFailure)
                throw fallbackFailure
            }
        }
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun parseFallback(body: String, fahrenheit: Boolean): String {
        val current = JSONObject(body).getJSONArray("current_condition").getJSONObject(0)
        val temperature = current.getDouble(if (fahrenheit) "temp_F" else "temp_C")
        require(temperature.isFinite()) { "Invalid weather" }
        val condition = HomeLibraryModel.title(current.getJSONArray("weatherDesc").getJSONObject(0).getString("value"))
        require(condition.isNotEmpty()) { "Missing weather condition" }
        return String.format(Locale.getDefault(), "%.0f°%s · %s", temperature, if (fahrenheit) "F" else "C", condition)
    }
}
