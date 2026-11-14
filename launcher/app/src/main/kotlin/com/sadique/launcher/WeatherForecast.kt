package com.sadique.launcher

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/** Bounded forecast snapshot with NaN for missing observations. */
class WeatherForecast(
    @JvmField val temperature: Double,
    @JvmField val feels: Double,
    @JvmField val humidity: Double,
    @JvmField val wind: Double,
    @JvmField val code: Int,
    @JvmField val day: Boolean,
    @JvmField val condition: String,
    @JvmField val source: String,
    hours: List<Hour>,
    days: List<Day>
) {
    class Hour(@JvmField val time: String, @JvmField val temperature: Double, @JvmField val code: Int, @JvmField val rain: Double)
    class Day(@JvmField val date: String, @JvmField val low: Double, @JvmField val high: Double, @JvmField val code: Int, @JvmField val rain: Double)

    @JvmField val hours: List<Hour> = Collections.unmodifiableList(ArrayList(hours))
    @JvmField val days: List<Day> = Collections.unmodifiableList(ArrayList(days))

    fun summary(fahrenheit: Boolean): String {
        return String.format(Locale.getDefault(), "%.0f°%s · %s", temperature, if (fahrenheit) "F" else "C", condition)
    }

    @Throws(JSONException::class)
    fun encode(): String {
        val current = JSONObject().put("temperature_2m", temperature).put("weather_code", code).put("is_day", if (day) 1 else 0)
        if (feels.isFinite()) current.put("apparent_temperature", feels)
        if (humidity.isFinite()) current.put("relative_humidity_2m", humidity)
        if (wind.isFinite()) current.put("wind_speed_10m", wind)
        val ht = JSONArray()
        val hv = JSONArray()
        val hc = JSONArray()
        val hr = JSONArray()
        for (hour in hours) {
            ht.put(hour.time)
            hv.put(hour.temperature)
            hc.put(hour.code)
            hr.put(if (hour.rain.isFinite()) hour.rain else JSONObject.NULL)
        }
        val dt = JSONArray()
        val dl = JSONArray()
        val dh = JSONArray()
        val dc = JSONArray()
        val dr = JSONArray()
        for (day in days) {
            dt.put(day.date)
            dl.put(day.low)
            dh.put(day.high)
            dc.put(day.code)
            dr.put(if (day.rain.isFinite()) day.rain else JSONObject.NULL)
        }
        return JSONObject()
            .put("current", current)
            .put("hourly", JSONObject().put("time", ht).put("temperature_2m", hv).put("weather_code", hc)
                .put("precipitation_probability", hr))
            .put("daily", JSONObject().put("time", dt).put("temperature_2m_min", dl).put("temperature_2m_max", dh)
                .put("weather_code", dc).put("precipitation_probability_max", dr))
            .put("widget_source", source).put("widget_condition", condition).toString()
    }

    companion object {
        private val HOURLY_TIME = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}.*")
        private val DAILY_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

        @JvmStatic
        fun url(lat: Double, lon: Double, fahrenheit: Boolean): String {
            LandscapeModel.weatherUrl(lat, lon, fahrenheit)
            val unit = if (fahrenheit) "fahrenheit" else "celsius"
            return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=7&forecast_hours=24&wind_speed_unit=kmh&temperature_unit=$unit&timezone=auto"
        }

        @JvmStatic
        @Throws(Exception::class)
        fun fetch(lat: Double, lon: Double, fahrenheit: Boolean): WeatherForecast =
            fetch(lat, lon, fahrenheit, OnlineContent::json)

        @JvmStatic
        @Throws(Exception::class)
        fun fetch(lat: Double, lon: Double, fahrenheit: Boolean, loader: WeatherClient.Loader): WeatherForecast {
            val primaryUrl = url(lat, lon, fahrenheit)
            try {
                return parse(loader.json(primaryUrl))
            } catch (primary: Exception) {
                if (Thread.currentThread().isInterrupted) throw primary
                try {
                    return fallback(loader.json("https://wttr.in/$lat,$lon?format=j1&lang=en"), fahrenheit)
                } catch (secondary: Exception) {
                    secondary.addSuppressed(primary)
                    throw secondary
                }
            }
        }

        private fun number(obj: JSONObject, key: String): Double = finite(obj.optDouble(key, Double.NaN))
        private fun value(array: JSONArray?, index: Int): Double = finite(array?.optDouble(index, Double.NaN) ?: Double.NaN)
        private fun finite(value: Double): Double = if (value.isFinite()) value else Double.NaN
        private fun probability(value: Double): Double = if (value in 0.0..100.0) value else Double.NaN

        @JvmStatic
        @Throws(JSONException::class)
        fun parse(json: String): WeatherForecast {
            val root = JSONObject(json)
            val current = root.getJSONObject("current")
            val temperature = number(current, "temperature_2m")
            require(temperature.isFinite()) { "Missing temperature" }
            val code = current.optInt("weather_code", -1)
            val now = current.optString("time", "")
            val hours = root.optJSONObject("hourly")?.let { readHours(it, now) }.orEmpty()
            val days = root.optJSONObject("daily")?.let(::readDays).orEmpty()
            return WeatherForecast(temperature, number(current, "apparent_temperature"),
                probability(number(current, "relative_humidity_2m")), number(current, "wind_speed_10m"),
                code, current.optInt("is_day", 1) == 1, LandscapeModel.condition(code), "Open-Meteo", hours, days)
        }

        private fun readHours(hourly: JSONObject, now: String): List<Hour> {
            val times = hourly.optJSONArray("time") ?: return emptyList()
            val temps = hourly.optJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val rain = hourly.optJSONArray("precipitation_probability")
            val result = ArrayList<Hour>()
            for (i in 0 until minOf(192, times.length())) {
                val time = times.optString(i, "")
                val temp = value(temps, i)
                if (!HOURLY_TIME.matches(time) || !temp.isFinite() || (now.isNotEmpty() && time < now)) continue
                result.add(Hour(time, temp, codes?.optInt(i, -1) ?: -1, probability(value(rain, i))))
                if (result.size == 12) break
            }
            return result
        }

        private fun readDays(daily: JSONObject): List<Day> {
            val dates = daily.optJSONArray("time") ?: return emptyList()
            val lows = daily.optJSONArray("temperature_2m_min")
            val highs = daily.optJSONArray("temperature_2m_max")
            val codes = daily.optJSONArray("weather_code")
            val rain = daily.optJSONArray("precipitation_probability_max")
            val result = ArrayList<Day>()
            for (i in 0 until minOf(7, dates.length())) {
                val date = dates.optString(i, "")
                val low = value(lows, i)
                val high = value(highs, i)
                if (!DAILY_DATE.matches(date) || !low.isFinite() || !high.isFinite() || low > high) continue
                result.add(Day(date, low, high, codes?.optInt(i, -1) ?: -1, probability(value(rain, i))))
            }
            return result
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun fallback(json: String, fahrenheit: Boolean): WeatherForecast {
            val current = JSONObject(json).getJSONArray("current_condition").getJSONObject(0)
            val temperature = number(current, if (fahrenheit) "temp_F" else "temp_C")
            require(temperature.isFinite()) { "Missing temperature" }
            val condition = HomeLibraryModel.title(current.getJSONArray("weatherDesc").getJSONObject(0).getString("value"))
            require(condition.isNotEmpty()) { "Missing condition" }
            val text = condition.lowercase(Locale.ROOT)
            val code = when {
                "thunder" in text -> 95
                "snow" in text -> 71
                "rain" in text -> 61
                "drizzle" in text -> 51
                "fog" in text || "mist" in text -> 45
                "overcast" in text -> 3
                "cloud" in text -> 2
                "sun" in text || "clear" in text -> 0
                else -> -1
            }
            return WeatherForecast(temperature, number(current, if (fahrenheit) "FeelsLikeF" else "FeelsLikeC"),
                probability(number(current, "humidity")), number(current, "windspeedKmph"), code, "night" !in text,
                condition, "wttr.in", emptyList(), emptyList())
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun decode(raw: String): WeatherForecast {
            val json = JSONObject(raw)
            val forecast = parse(raw)
            return WeatherForecast(forecast.temperature, forecast.feels, forecast.humidity, forecast.wind,
                forecast.code, forecast.day, json.optString("widget_condition", forecast.condition),
                json.optString("widget_source", forecast.source), forecast.hours, forecast.days)
        }
    }
}
