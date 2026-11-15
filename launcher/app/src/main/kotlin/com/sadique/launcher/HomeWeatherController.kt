package com.sadique.launcher

import android.app.Activity
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.Executors

/** Fetches, caches and presents weather while Home is visible. */
internal class HomeWeatherController(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val handler: Handler,
    private val connectivity: () -> ConnectivityManager?,
    private val resumed: () -> Boolean,
    private val harmony: () -> Boolean,
    private val weatherWidgetEnabled: () -> Boolean,
    private val homeWeather: () -> TextView?,
    private val weatherWidget: () -> WeatherWidgetView?,
) {
    private val weatherWorker = Executors.newSingleThreadExecutor()
    private var weatherBusy = false
    private var weatherForceRefresh = false
    private var forecastPanel: WeatherForecastPanel? = null
    private var weatherSnapshot: WeatherForecast? = null
    private var weatherSnapshotKey = ""
    private var weatherSnapshotAt = 0L
    private var nextWeatherAttempt = 0L
    private var lastWeatherRequestKey = ""

    fun preferencesChanged(key: String?) {
        when (key) {
            "weather_lat", "weather_lon", "weather_fahrenheit", "weather_city" -> {
                nextWeatherAttempt = 0
                handler.removeCallbacks(weatherRefresh)
                handler.post(weatherRefresh)
            }
        }
    }

    fun restoreWidget(widget: WeatherWidgetView) {
        val snapshot = weatherSnapshot ?: return
        val cacheKey = weatherCacheKey()
        if (HomeWeatherPolicy.fresh("forecast", weatherSnapshotKey, cacheKey,
                weatherSnapshotAt, System.currentTimeMillis(), WEATHER_CACHE_MS))
            widget.bind(snapshot, weatherCity(), prefs.getBoolean("weather_fahrenheit", false))
    }

    fun pause() {
        handler.removeCallbacks(weatherRefresh)
    }

    fun close() {
        forecastPanel?.close()
        pause()
        weatherWorker.shutdownNow()
    }

    /**
     * Small weather pill in the header. Shows cached weather immediately;
     * refreshes only when stale, or on city/unit change.
     */
    private fun weatherOnline(): Boolean {
        val conn = connectivity()
        val network = conn?.activeNetwork
        val caps = if (network == null) null else conn.getNetworkCapabilities(network)
        return HomeWeatherPolicy.canAttempt(network != null, caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET), caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }
    private fun weatherNeedsForecast(): Boolean {
        val panel = forecastPanel
        return weatherWidgetEnabled() || (panel != null && panel.isShowing())
    }
    private fun weatherCity(): String = prefs.getString("weather_city", LandscapeModel.CITY) ?: LandscapeModel.CITY
    fun showForecast() {
        forecastPanel = WeatherForecastPanel(activity, { weatherForceRefresh = true; nextWeatherAttempt = 0; refresh() }, { WeatherSettings.show(activity) })
        val available = if (weatherOnline() && weatherSnapshotKey == weatherCacheKey() && HomeWeatherPolicy.fresh("forecast", weatherSnapshotKey, weatherCacheKey(), weatherSnapshotAt, System.currentTimeMillis(), WEATHER_CACHE_MS)) weatherSnapshot else null
        forecastPanel?.show(available, weatherCity(), prefs.getBoolean("weather_fahrenheit", false))
        refresh()
    }
    private fun bindWeatherForecast(model: WeatherForecast?) {
        weatherWidget()?.bind(model, weatherCity(), prefs.getBoolean("weather_fahrenheit", false))
        forecastPanel?.update(model, weatherCity(), prefs.getBoolean("weather_fahrenheit", false))
    }
    fun refresh() {
        val weatherView = homeWeather()
        if (weatherView == null || !resumed() || activity.isDestroyed) return
        val cacheKey = weatherCacheKey()
        val now = System.currentTimeMillis()
        val widget = weatherWidgetEnabled()
        val forecastNeeded = weatherNeedsForecast()
        val requestKey = cacheKey + if (forecastNeeded) "|forecast" else "|current"
        handler.removeCallbacks(weatherRefresh)
        weatherView.visibility = if (widget || harmony()) View.GONE else View.VISIBLE
        if (!weatherOnline()) { setHomeWeather("Unavailable"); bindWeatherForecast(null); nextWeatherAttempt = 0; handler.postDelayed(weatherRefresh, 60_000L); return }
        WeatherSettings.resolvePhonePlace(activity, false)
        val cached = prefs.getString("weather_cached", null)
        val cachedAt = prefs.getLong("weather_cached_at", 0)
        val currentFresh = HomeWeatherPolicy.fresh(cached, prefs.getString("weather_cache_key", ""), cacheKey, cachedAt, now, WEATHER_CACHE_MS)
        val modelFresh = weatherSnapshot != null && HomeWeatherPolicy.fresh("forecast", weatherSnapshotKey, cacheKey, weatherSnapshotAt, now, WEATHER_CACHE_MS)
        setHomeWeather(if (currentFresh && cached != null) cached else "Unavailable")
        bindWeatherForecast(if (modelFresh) weatherSnapshot else null)
        if (!weatherForceRefresh && currentFresh && (!forecastNeeded || modelFresh)) {
            handler.postDelayed(weatherRefresh, Math.max(60_000L, WEATHER_CACHE_MS - (now - if (forecastNeeded) Math.min(cachedAt, weatherSnapshotAt) else cachedAt)))
            return
        }
        if (weatherBusy) return
        if (!weatherForceRefresh && requestKey == lastWeatherRequestKey && now < nextWeatherAttempt) { handler.postDelayed(weatherRefresh, Math.max(1000L, nextWeatherAttempt - now)); return }
        val force = weatherForceRefresh
        weatherForceRefresh = false
        weatherBusy = true
        lastWeatherRequestKey = requestKey
        val latitude = prefs.getString("weather_lat", LandscapeModel.LATITUDE.toString())
        val longitude = prefs.getString("weather_lon", LandscapeModel.LONGITUDE.toString())
        val fahrenheit = prefs.getBoolean("weather_fahrenheit", false)
        weatherWorker.execute {
            var forecast: WeatherForecast? = null
            var summary: String? = null
            var encoded: String? = null
            var fetched = System.currentTimeMillis()
            try {
                if (forecastNeeded) {
                    val persisted = prefs.getString("weather_forecast_json", null)
                    val timestamp = prefs.getLong("weather_forecast_at", 0)
                    if (!force && HomeWeatherPolicy.fresh(persisted, prefs.getString("weather_forecast_key", ""), cacheKey, timestamp, fetched, WEATHER_CACHE_MS)) {
                        try { forecast = WeatherForecast.decode(persisted!!); fetched = timestamp } catch (invalid: Exception) { forecast = null }
                    }
                    if (forecast == null) { forecast = WeatherForecast.fetch(latitude!!.toDouble(), longitude!!.toDouble(), fahrenheit); fetched = System.currentTimeMillis() }
                    WeatherOrbView.prepareMoon(activity)
                    WeatherImages.prepare(activity)
                    encoded = forecast.encode()
                    summary = forecast.summary(fahrenheit)
                } else {
                    summary = WeatherClient.fetch(latitude!!.toDouble(), longitude!!.toDouble(), fahrenheit)
                    fetched = System.currentTimeMillis()
                }
            } catch (unavailable: Exception) {
                Log.w("PersonalWeather", "Weather unavailable (" + unavailable.javaClass.simpleName + ")")
            }
            val result = forecast
            val value = summary
            val cache = encoded
            val fetchedAt = fetched
            handler.post {
                weatherBusy = false
                if (activity.isDestroyed) return@post
                if (cacheKey != weatherCacheKey()) { nextWeatherAttempt = 0; refresh(); return@post }
                if (value != null) {
                    val saved = prefs.edit().putString("weather_cached", value).putLong("weather_cached_at", fetchedAt).putString("weather_cache_key", cacheKey)
                    if (result != null) {
                        weatherSnapshot = result
                        weatherSnapshotKey = cacheKey
                        weatherSnapshotAt = fetchedAt
                        saved.putString("weather_forecast_json", cache).putLong("weather_forecast_at", fetchedAt).putString("weather_forecast_key", cacheKey)
                    }
                    saved.apply()
                } else {
                    weatherSnapshot = null
                    weatherSnapshotKey = ""
                    prefs.edit().putLong("weather_cached_at", 0).putLong("weather_forecast_at", 0).apply()
                }
                nextWeatherAttempt = System.currentTimeMillis() + if (value == null) 60_000L else WEATHER_CACHE_MS
                if (!resumed()) return@post
                val online = weatherOnline()
                setHomeWeather(if (online && value != null) value else "Unavailable")
                val snapshot = weatherSnapshot
                val ready = result ?: if (snapshot != null && HomeWeatherPolicy.fresh("forecast", weatherSnapshotKey, cacheKey, weatherSnapshotAt, System.currentTimeMillis(), WEATHER_CACHE_MS)) snapshot else null
                bindWeatherForecast(if (online) ready else null)
                homeWeather()?.visibility = if (weatherWidgetEnabled() || harmony()) View.GONE else View.VISIBLE
                handler.removeCallbacks(weatherRefresh)
                if ((weatherNeedsForecast() && !forecastNeeded) || weatherForceRefresh) { nextWeatherAttempt = 0; handler.post(weatherRefresh) }
                else handler.postDelayed(weatherRefresh, if (value == null) 60_000L else Math.max(60_000L, WEATHER_CACHE_MS - (System.currentTimeMillis() - fetchedAt)))
            }
        }
    }

    private fun setHomeWeather(value: String) {
        val weatherView = homeWeather() ?: return
        weatherView.text = if (ThemeStyle.custom(prefs)) value.replace(" · ", " ") else value
        if (ThemeStyle.custom(prefs) && "Unavailable" != value) {
            val glyph = StatusWeatherGlyph(value, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), HeaderAppearance.foreground(prefs))
            glyph.setBounds(0, 0, dp(22), dp(22))
            weatherView.compoundDrawablePadding = dp(6)
            weatherView.setCompoundDrawablesRelative(glyph, null, null, null)
        } else weatherView.setCompoundDrawablesRelative(null, null, null, null)
    }

    /** Cache key: changes when city or units change, invalidating the cache. */
    private fun weatherCacheKey(): String {
        return prefs.getString("weather_lat", "") + "," +
                prefs.getString("weather_lon", "") + "," +
                prefs.getBoolean("weather_fahrenheit", false)
    }

    private fun dp(value: Int): Int = Math.round(value * activity.resources.displayMetrics.density)

    private val weatherRefresh: Runnable = Runnable(::refresh)

    private companion object {
        const val WEATHER_CACHE_MS = 15 * 60_000L
    }
}
