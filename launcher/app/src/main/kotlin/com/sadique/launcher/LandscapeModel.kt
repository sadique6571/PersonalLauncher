package com.sadique.launcher

import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/** Sources and display rules shared by the screensaver and its settings. */
object LandscapeModel {
    const val CITY: String = "Okara, Pakistan"
    const val LATITUDE: Double = 30.81
    const val LONGITUDE: Double = 73.45

    @JvmStatic
    fun isLandscape(width: Int, height: Int): Boolean = height > 0 && width > height

    @JvmStatic
    fun bingCheckDue(lastCheck: Long, now: Long): Boolean {
        return lastCheck <= 0 || now < lastCheck || now - lastCheck >= 60 * 60_000L
    }

    @JvmStatic
    fun photoUrl(index: Int): String {
        val idx = Math.floorMod(index, 8)
        return "https://www.bing.com/HPImageArchive.aspx?format=js&idx=$idx&n=1&mkt=en-US"
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun bingImageUrl(json: String): String {
        val root = JSONObject(json)
        val images = root.getJSONArray("images")
        if (images.length() == 0) throw JSONException("No images")
        var url = images.getJSONObject(0).getString("url")
        if (url.startsWith("/")) url = "https://www.bing.com$url"
        if (!url.contains("1920x1080")) {
            url = url.replace("_\\d+x\\d+\\.jpg".toRegex(), "_1920x1080.jpg")
        }
        return url
    }

    @JvmStatic
    fun weatherUrl(lat: Double, lon: Double, fahrenheit: Boolean): String {
        if (!lat.isFinite() || !lon.isFinite() || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            throw IllegalArgumentException("Invalid city coordinates")
        }
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&temperature_unit=" + (if (fahrenheit) "fahrenheit" else "celsius") +
                "&timezone=auto"
    }

    @JvmStatic
    fun condition(code: Int): String {
        if (code == 0) return "Clear"
        if (code == 1) return "Mainly clear"
        if (code == 2) return "Partly cloudy"
        if (code == 3) return "Overcast"
        if (code == 45 || code == 48) return "Fog"
        if (code in 51..57) return "Drizzle"
        if (code in 61..67) return "Rain"
        if (code in 71..77) return "Snow"
        if (code in 80..82) return "Rain showers"
        if (code == 85 || code == 86) return "Snow showers"
        if (code in 95..99) return "Thunderstorm"
        return "Weather"
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun parseWeather(body: String, fahrenheit: Boolean): String {
        val current = JSONObject(body).getJSONObject("current")
        val temperature = current.getDouble("temperature_2m")
        if (!temperature.isFinite()) throw IllegalArgumentException("Invalid weather")
        return String.format(Locale.getDefault(), "%.0f°%s · %s", temperature, if (fahrenheit) "F" else "C", condition(current.getInt("weather_code")))
    }
}
