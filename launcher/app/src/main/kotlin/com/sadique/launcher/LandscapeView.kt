package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The activity preview and Android Dream use the same landscape screen. */
class LandscapeView(context: Context) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences
    private val connectivity: ConnectivityManager?
    private val reducedMotion: Boolean
    private var photo: ImageView
    private var previousPhoto: ImageView
    private var photoEffect = 0
    private var startupPhotoIndex = 0
    private val clock: TextView
    private val date: TextView
    private val wifi: TextView
    private val weather: TextView
    private val city: TextView
    private val status: TextView
    private val details: TextView
    private var worker: ExecutorService? = null
    private var weatherWorker: ExecutorService? = null
    private var running = false
    private var registered = false
    private var photoBusy = false
    private var weatherBusy = false
    private var hasPhoto = false
    private var networkOnline = false
    private var generation = 0

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { networkChanged() }
        }

        override fun onLost(network: Network) {
            handler.post { networkChanged() }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handler.post { networkChanged() }
        }
    }
    private val minuteTick = object : Runnable {
        override fun run() {
            if (!running) return
            updateClock()
            updateNetwork()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    private val nextPhoto = Runnable { requestPhoto() }
    private val weatherTick = object : Runnable {
        override fun run() {
            if (!running) return
            requestWeather()
        }
    }

    init {
        prefs = ThemePreferences.get(context)
        connectivity = context.getSystemService(ConnectivityManager::class.java)
        setBackgroundColor(0xff041225.toInt())
        reducedMotion = Config.load(context).reducedMotion
        val photos = FrameLayout(context)
        photo = photoLayer(context, photos)
        previousPhoto = photoLayer(context, photos)
        addView(photos, LayoutParams(-1, -1))
        val shade = View(context)
        shade.setBackgroundColor(0x24000000)
        addView(shade, LayoutParams(-1, -1))
        val timeBlock = LinearLayout(context)
        timeBlock.orientation = LinearLayout.VERTICAL
        clock = label(if (resources.configuration.screenHeightDp >= 650) 52 else 44)
        clock.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        clock.gravity = Gravity.LEFT
        clock.isSingleLine = true
        timeBlock.addView(clock, LinearLayout.LayoutParams(-2, -2))
        date = label(15)
        date.gravity = Gravity.LEFT
        timeBlock.addView(date)
        val clockPosition = LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT)
        clockPosition.setMargins(dp(24), dp(18), 0, 0)
        addView(timeBlock, clockPosition)
        wifi = label(14)
        wifi.setPadding(dp(10), dp(8), dp(10), dp(8))
        val networkPosition = LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        networkPosition.setMargins(dp(20), dp(20), dp(20), 0)
        addView(wifi, networkPosition)
        val conditions = LinearLayout(context)
        conditions.orientation = LinearLayout.VERTICAL
        city = label(16)
        city.gravity = Gravity.START
        weather = label(24)
        weather.gravity = Gravity.START
        weather.maxLines = 2
        weather.text = "Unavailable"
        conditions.addView(city)
        conditions.addView(weather)
        val availableWidth = Math.max(dp(240), resources.displayMetrics.widthPixels - dp(48))
        val weatherWidth = Math.min(dp(320), Math.round(availableWidth * .42f))
        val weatherPosition = LayoutParams(weatherWidth, -2, Gravity.BOTTOM or Gravity.START)
        weatherPosition.setMargins(dp(24), 0, 0, dp(24))
        addView(conditions, weatherPosition)
        status = label(12)
        status.text = "Connecting to online photos…"
        status.gravity = Gravity.END
        val statusPosition = LayoutParams(weatherWidth, -2, Gravity.TOP or Gravity.END)
        statusPosition.setMargins(0, dp(60), dp(24), 0)
        addView(status, statusPosition)
        // Wallpaper details caption at the bottom (user 2026-11-01).
        details = label(13)
        details.gravity = Gravity.END
        details.maxLines = 2
        val detailsPosition = LayoutParams(availableWidth - weatherWidth - dp(24), -2, Gravity.BOTTOM or Gravity.END)
        detailsPosition.setMargins(0, 0, dp(24), dp(24))
        addView(details, detailsPosition)
    }

    private fun clearPhotos() {
        photo.animate().cancel()
        previousPhoto.animate().cancel()
        photo.setImageDrawable(null)
        previousPhoto.setImageDrawable(null)
        hasPhoto = false
    }

    private fun showPhoto(image: Bitmap, caption: String) {
        photo.animate().cancel()
        previousPhoto.animate().cancel()
        val outgoing = photo
        photo = previousPhoto
        previousPhoto = outgoing
        val incoming = photo
        incoming.bringToFront()
        incoming.setImageBitmap(image)
        incoming.alpha = 0f
        incoming.translationX = 0f
        incoming.translationY = 0f
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        if (hasPhoto && !reducedMotion) {
            val horizontal = width * .085f
            val vertical = height * .10f
            var scale = 1f
            when (photoEffect++ % 7) {
                1 -> outgoing.animate().scaleX(.87f).scaleY(.87f).setDuration(620).start()
                2 -> scale = .87f
                3 -> {
                    scale = .80f
                    incoming.translationX = horizontal
                }
                4 -> {
                    scale = .80f
                    incoming.translationX = -horizontal
                }
                5 -> {
                    scale = .76f
                    incoming.translationY = vertical
                }
                6 -> {
                    scale = .76f
                    incoming.translationY = -vertical
                }
                else -> {
                    // Crossfade.
                }
            }
            incoming.scaleX = scale
            incoming.scaleY = scale
        }
        details.text = caption
        incoming.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(if (reducedMotion) Motion.REDUCED_MS.toLong() else 620L)
            .withEndAction { outgoing.setImageDrawable(null) }.start()
        hasPhoto = true
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)
    private fun label(size: Int): TextView {
        val label = TextView(context)
        label.setTextColor(Color.WHITE)
        label.setTextSize(size.toFloat())
        if (TvGlassStyle.enabled(prefs)) label.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        label.gravity = Gravity.CENTER
        label.setShadowLayer(3f, 0f, 1f, Color.BLACK)
        return label
    }

    fun start() {
        if (running) return
        running = true
        generation++
        photoBusy = false
        weatherBusy = false
        worker = Executors.newSingleThreadExecutor()
        ScreensaverPrefetch.schedule(context)
        weatherWorker = Executors.newSingleThreadExecutor()
        networkOnline = online()
        city.text = prefs.getString("weather_city", LandscapeModel.CITY)
        if (connectivity != null) {
            try {
                connectivity.registerDefaultNetworkCallback(networkCallback)
                registered = true
            } catch (unavailable: RuntimeException) {
                registered = false
            }
        }
        showStartupPhoto()
        minuteTick.run()
        requestPhoto()
        weatherTick.run()
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        if (registered && connectivity != null) {
            try {
                connectivity.unregisterNetworkCallback(networkCallback)
            } catch (ignored: RuntimeException) {
            }
            registered = false
        }
        worker?.shutdownNow()
        weatherWorker?.shutdownNow()
        clearPhotos()
        details.text = ""
    }

    private fun capabilities(): NetworkCapabilities? {
        val active = if (connectivity == null) null else connectivity.activeNetwork
        return if (active == null) null else connectivity?.getNetworkCapabilities(active)
    }

    private fun online(): Boolean {
        val caps = capabilities()
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateNetwork() {
        val caps = capabilities()
        val connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val color = if (connected) 0xff59e493.toInt() else 0xffff6c78.toInt()
        val image = if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) R.drawable.ic_wifi else R.drawable.ic_internet
        val icon = context.getDrawable(image).mutate()
        icon.setTint(color)
        icon.setBounds(0, 0, dp(24), dp(24))
        wifi.setCompoundDrawables(icon, null, null, null)
        wifi.compoundDrawablePadding = dp(8)
        wifi.text = if (connected) "Online" else "No internet"
        wifi.setTextColor(color)
        if (!connected) weather.text = "Unavailable"
    }

    private fun networkChanged() {
        if (!running) return
        updateNetwork()
        val connected = online()
        val recovered = connected && !networkOnline
        networkOnline = connected
        if (!hasPhoto || recovered) requestPhoto()
        // Weather recovery must not depend on whether a landscape has already loaded.
        if (recovered) requestWeather()
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        val value = PersonalHomeModel.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val clockText = SpannableString(value)
        clockText.setSpan(RelativeSizeSpan(.38f), value.length - 2, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        clock.text = clockText
        date.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now.time)
    }

    /** Fixed clock/date style (2026-11-01): white with black outline + drop shadow,
     *  readable on any photo. Replaces the old brightness-adaptive colors. */
    private fun adaptClockColors() {
        clock.setTextColor(0xFFFFFFFF.toInt())
        clock.setShadowLayer(6f, 0f, 3f, 0xE6000000.toInt())
        date.setTextColor(0xFFFFFFFF.toInt())
        date.setShadowLayer(5f, 0f, 3f, 0xE6000000.toInt())
    }

    private fun showStartupPhoto() {
        // Use a bundled photo as a placeholder while the worker checks the history and feeds.
        // It is not added to the unseen queue or used to reset photo history.
        val index = Random().nextInt(OFFLINE_RESOURCES.size)
        startupPhotoIndex = index
        photo.setImageResource(OFFLINE_RESOURCES[index])
        photo.alpha = 1f
        photo.translationX = 0f
        photo.translationY = 0f
        photo.scaleX = 1f
        photo.scaleY = 1f
        hasPhoto = true
        details.text = OFFLINE_SUBJECTS[index]
        status.text = "Looking for a new photo…"
    }

    private fun requestPhoto() {
        if (!running || photoBusy) return
        handler.removeCallbacks(nextPhoto)
        photoBusy = true
        val request = generation
        val placeholder = startupPhotoIndex
        worker!!.execute {
            var result: Bitmap? = null
            var subject = ""
            try {
                LandscapeHistory(context).use { history ->
                    var selected: LandscapeHistory.SavedPhoto?
                    var i = 0
                    while (result == null && i < LandscapeHistory.RESERVE_SIZE && !Thread.currentThread().isInterrupted) {
                        selected = history.takeOffline()
                        if (selected == null) break
                        result = ScreensaverPrefetch.decode(selected.bytes)
                        if (result != null) subject = selected.subject
                        i++
                    }
                    if (result == null) {
                        for (j in OFFLINE_RESOURCES.indices) {
                            if (result != null || Thread.currentThread().isInterrupted) break
                            if (j == placeholder || history.contains(OFFLINE_IDS[j])) continue
                            resources.openRawResource(OFFLINE_RESOURCES[j]).use { input ->
                                selected = LandscapeHistory.SavedPhoto(
                                    OFFLINE_IDS[j], OFFLINE_SUBJECTS[j],
                                    OnlineContent.readBounded(input, LandscapeHistory.RESERVE_PHOTO_LIMIT)
                                )
                                result = rememberPhoto(history, selected)
                                if (result != null) subject = selected!!.subject
                            }
                        }
                    }
                }
            } catch (unavailable: Exception) {
                Log.w("PersonalLandscape", "Photo history unavailable", unavailable)
            }
            val image = result
            val caption = subject
            handler.post {
                if (!running || request != generation) {
                    image?.recycle()
                    return@post
                }
                photoBusy = false
                ScreensaverPrefetch.schedule(context)
                if (image != null) {
                    startupPhotoIndex = -1
                    showPhoto(image, caption)
                    status.text = ""
                    adaptClockColors()
                    handler.postDelayed(nextPhoto, 30_000L)
                } else {
                    val connected = online()
                    status.text = if (connected) "Looking for a new photo…" else "Offline"
                    handler.postDelayed(nextPhoto, if (connected) 5_000L else 30_000L)
                }
            }
        }
    }

    private fun requestWeather() {
        if (!running || weatherBusy) return
        handler.removeCallbacks(weatherTick)
        if (!online()) {
            weather.text = "Unavailable"
            handler.postDelayed(weatherTick, 60_000L)
            return
        }
        weatherBusy = true
        city.text = prefs.getString("weather_city", LandscapeModel.CITY)
        val request = generation
        val fahrenheit = prefs.getBoolean("weather_fahrenheit", false)
        // Photo downloads cannot delay weather refreshes on a slow connection.
        weatherWorker!!.execute {
            var result: String? = null
            try {
                val lat = java.lang.Double.parseDouble(prefs.getString("weather_lat", java.lang.Double.toString(LandscapeModel.LATITUDE)))
                val lon = java.lang.Double.parseDouble(prefs.getString("weather_lon", java.lang.Double.toString(LandscapeModel.LONGITUDE)))
                result = WeatherClient.fetch(lat, lon, fahrenheit)
            } catch (unavailable: Exception) {
                Log.w("PersonalWeather", "Screensaver weather refresh failed", unavailable)
            }
            val value = result
            handler.post {
                if (!running || request != generation) return@post
                weatherBusy = false
                val success = value != null && online()
                if (success) {
                    weather.text = value
                } else {
                    weather.text = "Unavailable"
                }
                handler.removeCallbacks(weatherTick)
                handler.postDelayed(weatherTick, if (success) 15 * 60_000L else 60_000L)
            }
        }
    }

    companion object {
        private val OFFLINE_RESOURCES = intArrayOf(
            R.drawable.offline_autumn, R.drawable.offline_mountain_road, R.drawable.offline_mississippi
        )
        private val OFFLINE_IDS = arrayOf(
            "publicdomainpictures:200000/velka/fall-by-the-lake-14767797082J2.jpg",
            "publicdomainpictures:470000/velka/landschap-bergweg-haarspeldbocht-1665124155qIR.jpg",
            "nps:29fc4bc2-b103-4b1b-939f-19d4b91ce209"
        )
        private val OFFLINE_SUBJECTS = arrayOf(
            "Autumn by the lake", "Winding mountain road", "Mississippi River · U.S. National Park Service"
        )

        private fun photoLayer(context: Context, parent: FrameLayout): ImageView {
            val layer = ImageView(context)
            layer.scaleType = ImageView.ScaleType.FIT_CENTER
            layer.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            parent.addView(layer, FrameLayout.LayoutParams(-1, -1))
            return layer
        }

        private fun rememberPhoto(history: LandscapeHistory, photo: LandscapeHistory.SavedPhoto?): Bitmap? {
            if (photo == null || history.contains(photo.id) || Thread.currentThread().isInterrupted) return null
            val image = ScreensaverPrefetch.decode(photo.bytes) ?: return null
            var accepted = false
            try {
                accepted = history.remember(photo.id, photo.bytes, ScreensaverPrefetch.fingerprint(image))
                return if (accepted) image else null
            } finally {
                if (!accepted) image.recycle()
            }
        }
    }
}
