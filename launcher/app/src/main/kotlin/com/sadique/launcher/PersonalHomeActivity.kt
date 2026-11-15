package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.UiModeManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.function.IntConsumer

/** Responsive personal Home. Profile/search stay local; online scenery/weather use their own view. */
class PersonalHomeActivity : Activity() {
    /** Dark-on-light text scheme, used when the wallpaper behind the text is bright. */
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    /** Dedicated app-loading worker: icon scans never block avatar/wallpaper/import, and a new scan cancels the old. */
    private val appWorker = Executors.newSingleThreadExecutor()
    private var clockDatesInline = false
    private var weatherWidget: WeatherWidgetView? = null
    private var analogClock: SweepAnalogClock? = null
    private var harmonyClock: HarmonyVisionClockView? = null
    private var harmonyDateValue: TextView? = null
    private var harmonyGreetingValue: TextView? = null
    private var harmonyQuoteValue: TextView? = null
    private var harmonyWeatherCard: View? = null
    private fun weatherWidgetEnabled(): Boolean = ((harmony() && tv) || prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) && !prefs.getBoolean("apps_only", false)
    private fun clockAlignment(): Int = WeatherWidgetLayout.clockAlignment(weatherWidgetEnabled(), ThemeStyle.clockAlignment(prefs))
    private lateinit var homeWeatherController: HomeWeatherController
    private val homeBingWallpaper = HomeBingWallpaper(this)
    private val homePreferences = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ("ambient_strength" == key) updateAmbient()
        homeWeatherController.preferencesChanged(key)
    }
    private lateinit var prefs: SharedPreferences
    private var page: LinearLayout? = null
    private var header: LinearLayout? = null
    private var actions: LinearLayout? = null
    private var clockLayout: LinearLayout? = null
    private var appBody: LinearLayout? = null
    private var grid: HomeAppGrid<App>? = null
    private var searchButton: ImageButton? = null
    private var selectedCategory = ""
    private var gridState: Parcelable? = null
    private var profileEpoch = 0
    private var userName: TextView? = null
    private var time: TextView? = null
    private var date: TextView? = null
    private var ampm: TextView? = null
    private var networkStatus: TextView? = null
    private var appHeading: TextView? = null
    private var greeting: TextView? = null
    private var homeWeather: TextView? = null
    private var avatar: ImageView? = null
    private var bluetoothStatus: TextView? = null
    private var islamicDate: TextView? = null
    private var focusedSpotlight: TextView? = null
    private var spotlightName = ""
    private var spotlightCategory = ""
    private var bluetooth: GlassBluetooth? = null
    private var telemetry: GlassTelemetry? = null
    private var appLibrary: AppLibraryDialog? = null
    private var glassAccent = 0xffe50914.toInt()
    private fun harmony(): Boolean = ThemeStyle.harmony(prefs)
    private fun glassOs(): Boolean = ThemeStyle.id(prefs) == ThemeStyle.GLASS || harmony()
    private fun glassDefaults() {
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_os_defaults_v1", false))
            prefs.edit().putBoolean("glass_os_defaults_v1", true).putString("wallpaper_mode", "solid")
                .putInt("wallpaper_source", 0).putInt("header_bg_mode", 0).putBoolean("header_glass_v2", true).putInt("clock_size", 82)
                .putBoolean("focus_glow", true).putInt("ambient_strength", 100).apply()
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_resume_v3", false))
            prefs.edit().putBoolean("glass_resume_v3", true).putBoolean("continue_watching", true).apply()
        if (ThemeStyle.id(prefs) == ThemeStyle.GLASS && !prefs.getBoolean("glass_reference_v2", false))
            prefs.edit().putBoolean("glass_reference_v2", true).putInt("header_bg_mode", 1).putBoolean("header_glass_v2", true).apply()
        // Dedicated opt-in profile: no change to previously saved styles or TV services.
        if (harmony() && !prefs.getBoolean("harmony_defaults_v1", false))
            prefs.edit().putBoolean("harmony_defaults_v1", true)
                .putString("wallpaper_mode", "mountains").putInt("wallpaper_source", 1)
                .putInt("background_dim", 16).putInt("header_bg_mode", 0).putBoolean("header_glass_v2", true)
                .putInt("clock_size", 88).putInt("clock_align", 0).putInt("clock_style", 0)
                .putBoolean("weather_widget", true).putBoolean("clock_seconds", false)
                .putBoolean("clock_shadow", true).putBoolean("focus_glow", false)
                .putInt("tv_columns", 4).putInt("tile_corners", 2).putInt("tile_spacing", 1)
                .putInt("glass_alpha", 58).putInt("glass_finish", 0).putInt("glass_edge", 1)
                .putBoolean("panel_transparency", true).apply()
    }
    /** Lightweight English service shortcuts with D-pad return to the app dock. */
    private fun harmonyShortcuts(): View {
        val links = LinearLayout(this)
        links.orientation = LinearLayout.VERTICAL
        val apps = GlassSettings.wideCard(this, "All apps", R.drawable.ic_apps,
                ThemeStyle.focusAccent(prefs), this::showAppLibrary)
        val scenery = GlassSettings.wideCard(this, "Scenery", R.drawable.ic_picture,
                ThemeStyle.focusAccent(prefs)) { launch(Intent(this, LandscapeActivity::class.java)) }
        for (entry in arrayOf(apps, scenery)) {
            entry.setOnKeyListener { _, key, event ->
                if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN && grid != null) {
                    grid?.focusKey(focusedPkg)
                    true
                } else {
                    false
                }
            }
            val params = LinearLayout.LayoutParams(-1, dp(64))
            params.setMargins(0, dp(3), 0, dp(3))
            links.addView(entry, params)
        }
        return links
    }
/** A dedicated Huawei Vision-inspired scene, not a recoloured Glass OS home. */
    /** Three balanced zones: the original watch, one daily thought and a real weather widget. */
    private fun harmonyHero(): View {
        val stage = LinearLayout(this)
        stage.orientation = LinearLayout.HORIZONTAL
        stage.gravity = Gravity.CENTER_VERTICAL
        stage.clipChildren = false
        stage.clipToPadding = false
        val screenH = resources.configuration.screenHeightDp
        val screenW = resources.configuration.screenWidthDp
        val side = dp(HarmonyVisionLayout.clockDiameterDp(screenH, prefs.getInt("harmony_clock_size", 1)))
        val clock = HarmonyVisionClockView(this)
        harmonyClock = clock
        clock.setOnClickListener { harmonyClockSettings() }
        clock.setOnKeyListener { _, key, event -> harmonyHeroKey(key, event) }
        if (!prefs.getBoolean("hide_clock", false)) {
            val face = LinearLayout.LayoutParams(side, side)
            face.marginEnd = dp(if (screenW < 780) 12 else 22)
            stage.addView(clock, face)
        }
        val thought = LinearLayout(this)
        thought.orientation = LinearLayout.VERTICAL
        thought.gravity = Gravity.CENTER_VERTICAL
        stage.addView(thought, LinearLayout.LayoutParams(0, -2, 1f))
        val greetingValue = text(greetingText(), if (screenH < 470) 12 else 14, 0xffd7eaff.toInt())
        harmonyGreetingValue = greetingValue
        greetingValue.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        thought.addView(greetingValue)
        val quoteValue = text(HarmonyDailyWords.forCalendar(Calendar.getInstance()),
                if (screenW < 780) 19 else if (screenH < 470) 21 else 25, 0xffffffff.toInt())
        harmonyQuoteValue = quoteValue
        quoteValue.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        quoteValue.maxLines = 3
        quoteValue.ellipsize = TextUtils.TruncateAt.END
        quoteValue.setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0x88061627.toInt())
        val quote = LinearLayout.LayoutParams(-1, -2)
        quote.topMargin = dp(7)
        thought.addView(quoteValue, quote)
        val dateValue = text("", 12, 0xffc0d3e3.toInt())
        harmonyDateValue = dateValue
        val dateParams = LinearLayout.LayoutParams(-1, -2)
        dateParams.topMargin = dp(10)
        thought.addView(dateValue, dateParams)
        quoteValue.visibility = if (prefs.getBoolean("harmony_daily_quote", true)) View.VISIBLE else View.GONE
        if (HarmonyVisionLayout.showNativeWeatherWidget()) {
            val widget = WeatherWidgetView(this, homeWeatherController::showForecast)
            weatherWidget = widget
            harmonyWeatherCard = widget
            widget.setOnKeyListener { _, key, event -> harmonyHeroKey(key, event) }
            val weatherWidth = Math.max(210, Math.min(285, Math.round(screenW * 0.29f)))
            val wp = LinearLayout.LayoutParams(dp(weatherWidth), -2)
            wp.marginStart = dp(if (screenW < 780) 8 else 24)
            stage.addView(widget, wp)
            widget.active(resumed)
            homeWeatherController.restoreWidget(widget)
        }
        clock.active(resumed && !prefs.getBoolean("hide_clock", false))
        return stage
    }
    private fun harmonyHeroKey(key: Int, event: KeyEvent): Boolean {
        val horizontal = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
        if (!horizontal && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return true
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            searchButton?.requestFocus()
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusHarmonyBelowHero()
        } else {
            val rtl = page?.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val target: View? = if ((key == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl) weatherWidget else harmonyClock
            if (target != null && target.isShown) target.requestFocus()
        }
        return true
    }

    private fun focusHarmonyHero(): Boolean {
        val clock = harmonyClock
        if (clock != null && clock.isShown && clock.requestFocus()) return true
        val widget = weatherWidget
        return widget != null && widget.isShown && widget.requestFocus()
    }

    private fun focusHarmonyBelowHero(): Boolean {
        val g = grid
        if (g == null || g.appCount() == 0) return false
        g.focusKey(focusedPkg)
        return true
    }

    private fun fixedHarmonyDock(): Boolean {
        return harmony() && tv && !prefs.getBoolean("apps_only", false)
    }

    private fun focusBelowToolbar() {
        val g = grid
        if (g != null && (!g.isHorizontalDock() || !focusHarmonyHero())) g.focusKey(focusedPkg)
    }
    private fun harmonyClockSettings() {
        if (!harmony()) return
        val names = arrayOf("Watch face", "Metal finish", "Clock accent", "Second hand", "Time format", "Clock size", "Hide clock", "Daily thoughts")
        val hints = arrayOf("Heritage, Chronograph, Skeleton", "Space Blue, Obsidian, Titanium, Ceramic",
                "Ice blue, Gold or Rose", "Smooth sweep or Quartz tick", "12-hour or 24-hour",
                "Fit the clock on the TV", "Show or hide the dial", "An original thought for every day")
        val icons = intArrayOf(R.drawable.ic_clock, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_clock,
                R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock)
        GlassSettings.options(this, "Harmony Vision Clock", names, hints, icons, { which ->
            when (which) {
                0 -> chooseSetting("Watch face", arrayOf("Heritage", "Chronograph", "Skeleton")) { i ->
                    prefs.edit().putInt("harmony_clock_face", i).apply()
                    harmonyClock?.configure(prefs)
                }
                1 -> chooseSetting("Metal finish", arrayOf("Space Blue", "Obsidian", "Titanium", "Ceramic")) { i ->
                    prefs.edit().putInt("harmony_clock_finish", i).apply()
                    harmonyClock?.configure(prefs)
                }
                2 -> chooseSetting("Clock accent", arrayOf("Ice Blue", "Gold", "Rose")) { i ->
                    prefs.edit().putInt("harmony_clock_accent", i).apply()
                    harmonyClock?.configure(prefs)
                }
                3 -> chooseSetting("Second hand", arrayOf("Smooth sweep", "Quartz tick")) { i ->
                    prefs.edit().putBoolean("harmony_clock_sweep", i == 0).apply()
                    harmonyClock?.configure(prefs)
                }
                4 -> chooseSetting("Time format", arrayOf("12-hour", "24-hour")) { i ->
                    prefs.edit().putBoolean("time_24h", i == 1).apply()
                    harmonyClock?.configure(prefs)
                    updateClock()
                }
                5 -> chooseSetting("Harmony clock size", arrayOf("Small", "Normal", "Large")) { i ->
                    prefs.edit().putInt("harmony_clock_size", i).apply()
                    buildHomeSoon()
                }
                6 -> chooseSetting("Hide clock", arrayOf("Show", "Hide")) { i ->
                    prefs.edit().putBoolean("hide_clock", i == 1).apply()
                    buildHomeSoon()
                }
                7 -> chooseSetting("Daily thoughts", arrayOf("On", "Off")) { i ->
                    prefs.edit().putBoolean("harmony_daily_quote", i == 0).apply()
                    buildHomeSoon()
                }
            }
        }, { which -> which >= 6 || clockVisible() })
    }
    private var avatarBitmap: Bitmap? = null
    private var customWallpaper: Bitmap? = null
    private var contrastKey: ClockSampleKey? = null
    private var wallpaperClockInk = 0xffffffff.toInt()
    private var liquidBackdrop: LiquidBackdrop? = null
    private var headerBackdrop: LiquidBackdrop? = null
    private var continueWatching: ContinueWatchingView? = null
    private var sky: HomeWallpaperView? = null
    private var connectivity: ConnectivityManager? = null
    private var networkRegistered = false
    private var resumed = false
    private var tv = false
    private val appChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { loadApps() }
    }
    private var pageWidth = 0
    private var settingsRemoteKeys: MutableSet<Int> = mutableSetOf()
    /** Wallpaper-adaptive text: true while the region behind the text is bright (dark text in use). */
    /** Effective clock text size in sp (after the compact-screen cap), for fitClockText(). */
    private var clockTextSize = 68
    private fun tvGlass(): Boolean = tv && TvGlassStyle.enabled(prefs)
    /** Debounced rebuild: coalesces rapid settings changes into one buildHome(). */
    private var buildHomePending = false
    /** elapsedRealtime at onCreate entry, for first-frame timing (BootLog). */
    private var onCreateStartMs = 0L
    /** First buildHome() also logs the first drawn frame; rebuilds only log their own duration. */
    private var firstBuild = true
    private var cachedApps: List<App>? = null
    /** Move mode: the app being carried, and the visible package order while it moves. Null when off. */
    private var carriedApp: App? = null
    private var movingRendered = false
    private var moveOrder: MutableList<String>? = null
    /** Live tiles by package, for progressive icon fill-in. Rebuilt with the grid. */
    private val tileViews = HashMap<String, View>()
    /** Package of the focused tile, restored after grid rebuilds. */
    private var focusedPkg: String? = null
    /** Last day/night state; when it flips, tile surfaces are refreshed to match. */
    private var lastLight = false
    private var panel: AlertDialog? = null
    private var profile: AlertDialog? = null
    private var searchDialog: AlertDialog? = null
    private var searchInput: EditText? = null
    private var searchRefresh: Runnable? = null
    private val idleScreensaver = Runnable {
        if (resumed && hasWindowFocus() && !GitHubUpdates.manualActive()) launch(Intent(this, LandscapeActivity::class.java))
        else armIdleScreensaver()
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { handler.post { updateNetwork() } }
        override fun onLost(network: Network) { handler.post { updateNetwork() } }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            handler.post { updateNetwork() }
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            updateClock()
            val light = appearanceDaylight() >= 0.5f
            if (light != lastLight) {
                lastLight = light
                refreshTileSurfaces()
            }
            updateOnlineWallpaper()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    /** Re-applies backgrounds so tiles match the current day/night theme without a rebuild. */
    private fun refreshTileSurfaces() {
        for (entry in tileViews.entries) {
            val tile = entry.value
            val focused = tile.isFocused
            val tag = tile.tag
            val accent = if (tag is Int) tag else CYAN
            if (tile is HarmonyAppTile) tile.appearance(focused)
            else if (tile is GlassIconTile) tile.appearance(accent, focused)
            else { tile.background = appSurface(accent, focused); tile.foreground = tileBorder(accent, focused) }
        }
        sky?.invalidate()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onCreateStartMs = SystemClock.elapsedRealtime()
        BootLog.mark("home onCreate")
        prefs = ThemePreferences.get(this)
        val config = Config.load(this)
        settingsRemoteKeys = HashSet()
        settingsRemoteKeys.add(KeyEvent.KEYCODE_SETTINGS)
        for (key in config.settingsKeycodes) settingsRemoteKeys.add(key)
        RemoteKeys.parseAppKeys(config.appKeys).forEach { (key, action) ->
            if ("home_settings" == action) settingsRemoteKeys.add(key)
        }
        homeWeatherController = HomeWeatherController(
            activity = this,
            prefs = prefs,
            handler = handler,
            connectivity = { connectivity },
            resumed = { resumed },
            harmony = this::harmony,
            weatherWidgetEnabled = this::weatherWidgetEnabled,
            homeWeather = { homeWeather },
            weatherWidget = { weatherWidget },
        )
        prefs.registerOnSharedPreferenceChangeListener(homePreferences)
        if (!prefs.getBoolean("bing_daily_home_v67", false)) {
            prefs.edit().putBoolean("bing_daily_home_v67", true)
                    .putInt("wallpaper_source", 1).putString("wallpaper_mode", "mountains").apply()
        }
        if (!prefs.getBoolean("reference_layout_v42", false)) {
            prefs.edit().putBoolean("reference_layout_v42", true).putString("theme_mode", "dark")
                    .apply()
        }
        if (!prefs.getBoolean("reference_layout_v43", false)) {
            prefs.edit().putBoolean("reference_layout_v43", true).putInt("background_dim", 0).apply()
        }
        val modes = getSystemService(UiModeManager::class.java)
        tv = modes != null && modes.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        ThemeStyle.demoDefaults(prefs, tv)
        connectivity = getSystemService(ConnectivityManager::class.java)
        bluetooth = GlassBluetooth(this, this::updateBluetooth)
        val packages = IntentFilter()
        packages.addAction(Intent.ACTION_PACKAGE_ADDED)
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED)
        packages.addDataScheme("package")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(appChanges, packages, Context.RECEIVER_EXPORTED)
        else registerReceiver(appChanges, packages)
        lastLight = appearanceDaylight() >= 0.5f
        focusedPkg = prefs.getString("home_focus_pkg", null)
        if (state != null && !state.getBoolean("home_grid_has_header")
                && state.getBoolean("home_grid_horizontal_dock") == fixedHarmonyDock())
            gridState = state.getParcelable("home_grid_state")
        loadAvatar()
        buildHome()
        loadWallpaper()
        if (!prefs.getBoolean("setup", false)) handler.post { editProfile() }
        else { handleHomeAction(intent); handler.post { maybeOfferDefaultHome() } }
    }

    private fun dp(value: Float): Int = Math.round(value * resources.displayMetrics.density)
    /** Int overload for the call sites that pass sizes: same rounding as dp(float). */
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun appearanceDaylight(): Float {
        val now = Calendar.getInstance()
        return HomeAppearance.daylight(prefs.getString("theme_mode", "dark"), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    private fun applyAppearance() {
        // Recreate surfaces as well as the wallpaper; changing only the canvas left cards unchanged.
        buildHomeSoon()
    }

    private val buildHomeTask = object : Runnable {
        override fun run() {
            buildHomePending = false
            buildHome()
        }
    }

    /**
     * Coalesces rapid settings changes into a single rebuild (250 ms window).
     * A direct buildHome() call cancels any pending debounced one.
     */
    private fun buildHomeSoon() {
        if (buildHomePending) return
        buildHomePending = true
        handler.postDelayed(buildHomeTask, 250)
    }

    /**
     * Width the app grid can actually use: the page content width minus the
     * appBody panel's side padding. Sizing tiles from pageWidth alone overflowed
     * the panel and clipped the rightmost column (portrait and landscape).
     */
    private fun gridWidth(): Int {
        val body = appBody
        val pad = if (body == null) dp(16) else body.paddingLeft + body.paddingRight
        return Math.max(dp(120), pageWidth - pad)
    }

    /** Logo size inside tiles from settings: small / normal / large. */
    private fun logoScale(): Float {
        return floatArrayOf(0.8f, 1f, 1.2f)[Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))]
    }

    /** Current theme accent color: Midnight Cyan, Sunset Orange, Deep Purple, or Custom. */
    private fun accentColor(): Int {
        if (ThemeStyle.custom(prefs) || harmony()) return GlassSettings.themeAccent(this)
        return when (prefs.getInt("theme_name", 0)) {
            1 -> 0xffff9a5c.toInt() // Sunset Orange
            2 -> 0xffb388ff.toInt() // Deep Purple
            3 -> prefs.getInt("custom_accent", CYAN)
            else -> CYAN // Midnight Cyan
        }
    }

    /** Tile corner radius from settings: sharp / rounded / round. */
    private fun tileRadius(): Int {
        return dp(HomeGeometry.tileRadius(prefs.getInt("tile_corners", 1)))
    }

    private fun surface(focused: Boolean): GradientDrawable = surface(focused, false)
    private fun appSurface(focused: Boolean): GradientDrawable = surface(focused, true)
    private fun appSurface(accent: Int, focused: Boolean): GradientDrawable = if (glassOs()) GlassAppCard.surface(this, accent, focused, tileRadius()) else appSurface(focused)
    private fun surface(focused: Boolean, drawer: Boolean): GradientDrawable {
        if (TvGlassStyle.enabled(prefs)) return if (drawer)
                TvGlassStyle.drawerCard(tileRadius(), dp(1), focused, prefs)
                else TvGlassStyle.surface(tileRadius(), dp(if (focused) 2 else 1), focused, prefs)
        val shape = GradientDrawable()
        val day = appearanceDaylight()
        // Theme-tinted glass (user 2026-11-01): the dark base takes the theme's hue.
        val accent = accentColor()
        val tint = 0.3f
        val night = HomeAppearance.blend(if (focused) 0xff153d5b.toInt() else 0xff092038.toInt(), accent, tint)
        val dayC = HomeAppearance.blend(if (focused) 0xff407596.toInt() else 0xff335975.toInt(), accent, tint)
        shape.setColor(HomeAppearance.blend(night, dayC, day))
        // Glass intensity (user 2026-11-01): 0=airy, 100=solid. Focused stays a touch brighter.
        val intensity = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)))
        shape.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(intensity, focused), prefs, drawer))
        shape.cornerRadius = tileRadius().toFloat()
        shape.setStroke(dp(if (focused) 3 else 1), if (focused) accent else 0xff28435e.toInt())
        return shape
    }

    private fun avatarSurface(focused: Boolean): GradientDrawable {
        return avatarSurface(focused, if (ThemeStyle.custom(prefs) && tv) 52 else 44)
    }
    private fun avatarSurface(focused: Boolean, size: Int): GradientDrawable {
        val shape = surface(focused)
        if (prefs.getInt("avatar_shape", 0) == 0) shape.shape = GradientDrawable.OVAL
        else shape.cornerRadius = dp(HomeGeometry.avatarRadius(size)).toFloat()
        return shape
    }
    private fun avatarOutline(photo: ImageView) {
        photo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (prefs.getInt("avatar_shape", 0) == 0) outline.setOval(0, 0, view.width, view.height)
                else outline.setRoundRect(0, 0, view.width, view.height,
                        HomeGeometry.avatarRadius(Math.min(view.width, view.height)))
            }
        }
        photo.clipToOutline = true
    }
    private fun chooseAvatarShape() {
        chooseSetting("Avatar shape", arrayOf("Round", "Rounded square")) { which ->
            prefs.edit().putInt("avatar_shape", which).apply()
            avatar?.apply { background = avatarSurface(isFocused); invalidateOutline() }
            refreshProfileAvatarPreview()
        }
    }

    private fun text(value: String, size: Int, color: Int): TextView {
        // Outline removed 2026-11-01 (user: "buri lag rahi hai") — plain text with drop shadow only.
        val view = TextView(this)
        view.text = value
        view.setTextSize(size.toFloat())
        view.setTextColor(ThemeStyle.contentColor(prefs, color))
        applyAppFont(view)
        return view
    }

    private fun button(label: String, action: Runnable): Button {
        val view = Button(this)
        view.text = label
        view.setTextSize(14f)
        view.setTextColor(ThemeStyle.foreground(prefs))
        view.isAllCaps = false
        view.minHeight = dp(48)
        view.minimumWidth = 0
        view.setPadding(dp(14), dp(8), dp(14), dp(8))
        view.background = surface(false)
        view.setOnFocusChangeListener { v, focus -> v.background = surface(focus) }
        view.setOnClickListener { action.run() }
        return view
    }

    private fun toolbarSurface(focused: Boolean): GradientDrawable {
        val shape = surface(focused)
        shape.cornerRadius = dp(if (tvGlass()) 24 else 16).toFloat()
        return shape
    }

    private fun iconAction(title: String, resource: Int, action: Runnable): ImageButton {
        val icon = ImageButton(this)
        icon.setImageDrawable(TvSymbols.icon(this, resource))
        icon.setColorFilter(HeaderAppearance.foreground(prefs))
        icon.contentDescription = title
        icon.tooltipText = title
        val glassIcons = TvGlassStyle.enabled(prefs)
        val symbolInset = if (glassIcons) dp(if (tv) 7 else 6) else dp(11)
        icon.setPadding(symbolInset, symbolInset, symbolInset, symbolInset)
        // CENTER_INSIDE capped these custom 24px drawables at their intrinsic size on dense screens.
        icon.scaleType = if (glassIcons) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_INSIDE
        icon.background = if (glassIcons) null else toolbarSurface(false)
        icon.clipToOutline = !glassIcons
        icon.isFocusable = true
        icon.setOnFocusChangeListener { v, focus ->
            if (glassIcons) {
                // Remote focus is visible through scale and colour, never a surrounding box.
                icon.setColorFilter(if (focus) ThemeStyle.focusAccent(prefs) else HeaderAppearance.foreground(prefs))
                icon.scaleX = if (focus) 1.14f else 1f
                icon.scaleY = if (focus) 1.14f else 1f
            } else v.background = toolbarSurface(focus)
        }
        icon.setOnClickListener { action.run() }
        icon.setOnKeyListener { v, _, event -> toolbarKey(v, event) }
        val p = LinearLayout.LayoutParams(dp(if (tv) 48 else 44), dp(if (tv) 48 else 44))
        p.setMargins(dp(2), 0, dp(2), 0)
        val bar = actions!!
        if (ThemeStyle.id(prefs) == ThemeStyle.COBALT && bar.childCount > 0) {
            val separator = View(this)
            separator.setBackgroundColor(0x6672a0ed)
            bar.addView(separator, LinearLayout.LayoutParams(dp(1), dp(24)))
        }
        bar.addView(icon, p)
        return icon
    }

    private fun toolbarKey(focused: View, event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT
                && code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return true
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusBelowToolbar()
        } else if (code != KeyEvent.KEYCODE_DPAD_UP) {
            val bar = actions!!
            val buttons = ArrayList<View>()
            for (i in 0 until bar.childCount)
                if (bar.getChildAt(i).isFocusable && bar.getChildAt(i).isShown) buttons.add(bar.getChildAt(i))
            val index = buttons.indexOf(focused)
            if (index >= 0) {
                val avatarParent = avatar?.parent
                if (glassOs() && index == 0 && code == KeyEvent.KEYCODE_DPAD_LEFT && avatarParent is View) {
                    grid?.cancelPendingFocus()
                    avatarParent.requestFocus()
                    return true
                }
                val next = Math.max(0, Math.min(buttons.size - 1, index + if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1))
                grid?.cancelPendingFocus()
                buttons[next].requestFocus()
            }
        }
        return true
    }

    private fun buildHome() {
        glassDefaults()
        telemetry?.stop()
        telemetry = null
        weatherWidget?.active(false)
        weatherWidget = null
        analogClock?.active(false)
        analogClock = null
        harmonyClock?.active(false)
        harmonyClock = null
        harmonyDateValue = null
        harmonyGreetingValue = null
        harmonyQuoteValue = null
        harmonyWeatherCard = null
        clockDatesInline = glassOs() && resources.configuration.screenWidthDp >= 700 && !weatherWidgetEnabled() && prefs.getInt("clock_style", 0) != 1
        islamicDate = null
        bluetoothStatus = null
        focusedSpotlight = null
        HeaderAppearance.clockDefaults(prefs)
        liquidBackdrop?.close()
        liquidBackdrop = null
        headerBackdrop?.close()
        headerBackdrop = null
        val buildMs = SystemClock.elapsedRealtime()
        handler.removeCallbacks(buildHomeTask)
        buildHomePending = false
        continueWatching?.stop()
        val harmonyHome = fixedHarmonyDock()
        val oldGrid = grid
        if (oldGrid != null) {
            val lm = oldGrid.layoutManager
            gridState = if (lm != null && oldGrid.isHorizontalDock() == harmonyHome) lm.onSaveInstanceState() else null
        }
        tileViews.clear()
        pageWidth = 0
        val root = FrameLayout(this)
        root.setBackgroundColor(0xff031126.toInt())
        val oldSky = sky
        oldSky?.stopCrossfade()
        val sky = HomeWallpaperView(
            context = this,
            prefs = prefs,
            tv = tv,
            handler = handler,
            worker = worker,
            homeBingWallpaper = homeBingWallpaper,
            previous = oldSky,
            favoriteFile = { profileAsset("favorite_wallpaper.jpg") },
            customWallpaper = { customWallpaper },
            gradientAccent = { if (gradientWallpaper()) glassAccent else null },
            clockGlowAccent = {
                if (clockVisible() && !(harmony() && tv) && prefs.getBoolean("clock_glow", false))
                    accentColor()
                else null
            },
            isCurrent = { this.sky === it },
            onWallpaperChanged = this::adaptTextColors,
        )
        this.sky = sky
        // Keep the already-loaded wallpaper across rebuilds: a settings change
        // must never flash the default wallpaper before the online photo
        // re-fetches. (User 2026-11-01.)
        if (oldSky != null) sky.adoptWallpapers(oldSky)
        if (!tv) {
            sky.scaleX = 1.06f
            sky.scaleY = 1.06f
        }
        root.addView(sky, ViewGroup.LayoutParams(-1, -1))
        if (harmony() && tv) {
            val tint = View(this)
            tint.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x180d243b, 0x7c071c34))
            root.addView(tint, ViewGroup.LayoutParams(-1, -1))
        }
        val scroll = ScrollView(this)
        // Harmony's hero follows the header naturally; fill+centering introduced a huge void.
        scroll.isFillViewport = glassOs() && !harmony()
        scroll.clipToPadding = false
        val page = LinearLayout(this)
        this.page = page
        page.orientation = LinearLayout.VERTICAL
        val inset = dp(if (tv) if (harmony()) 24 else 32 else 16)
        page.setPadding(inset, dp(if (harmony()) 8 else 14), inset, dp(if (harmony()) 4 else 12))
        if (harmony()) { page.clipChildren = true; page.clipToPadding = true }
        root.addView(page, ViewGroup.LayoutParams(-1, -1))
        // Cinematic shade: subtle dark gradients top and bottom for a film-like frame.
        val topShade = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(if (ThemeStyle.light(prefs)) 0x00000000 else if (TvGlassStyle.enabled(prefs)) 0x22000000 else ClassicGlassAlpha.panel(TvGlassStyle.intensity(prefs)) shl 24, 0x00000000))
        val topBar = View(this)
        topBar.background = topShade
        topBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(topBar, FrameLayout.LayoutParams(-1, dp(110), Gravity.TOP))
        val bottomShade = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(if (ThemeStyle.light(prefs)) 0x00000000 else 0x66000000, 0x00000000))
        val bottomBar = View(this)
        bottomBar.background = bottomShade
        bottomBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(bottomBar, FrameLayout.LayoutParams(-1, dp(110), Gravity.BOTTOM))
        root.setOnApplyWindowInsetsListener { _, insets ->
            page.setPadding(inset + insets.systemWindowInsetLeft, dp(if (harmony()) 8 else 14) + insets.systemWindowInsetTop,
                    inset + insets.systemWindowInsetRight, dp(if (harmony()) 4 else 12) + insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        val header = LinearLayout(this)
        this.header = header
        header.gravity = Gravity.CENTER_VERTICAL
        applyHeaderBackground()
        val photo: ImageView = if (glassOs()) AppAccentAvatar(this) else ImageView(this)
        avatar = photo
        photo.id = R.id.profileAvatar
        photo.contentDescription = getString(R.string.h_edit_profile)
        photo.scaleType = ImageView.ScaleType.CENTER_CROP
        photo.background = avatarSurface(false)
        if (!glassOs()) avatarOutline(photo)
        if (tvGlass()) { photo.setPadding(dp(2), dp(2), dp(2), dp(2)); photo.elevation = dp(2).toFloat() }
        photo.isFocusable = false
        photo.setOnClickListener { LauncherPin.require(this, this::editProfile) }
        photo.setOnFocusChangeListener { v, focus -> v.background = avatarSurface(focus) }
        val identityCard = LinearLayout(this)
        identityCard.gravity = Gravity.CENTER_VERTICAL
        // The header already supplies the glass; a second profile-card fill hid the wallpaper detail.
        if (tvGlass()) identityCard.setPadding(dp(6), dp(5), dp(14), dp(5))
        identityCard.setOnClickListener { LauncherPin.require(this, this::editProfile) }
        if (glassOs()) {
            identityCard.id = R.id.leftBlock
            identityCard.background = null
            identityCard.isFocusable = true
            identityCard.setOnKeyListener { _, c, e ->
                if (c == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN) { networkStatus?.requestFocus(); true }
                else if (c == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN) { focusBelowToolbar(); true }
                else c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_RIGHT || c == KeyEvent.KEYCODE_DPAD_DOWN
            }
            identityCard.setOnFocusChangeListener { v, f -> v.alpha = if (f) 1f else 0.88f }
        }
        identityCard.addView(photo, LinearLayout.LayoutParams(dp(if (glassOs()) 42 else if (ThemeStyle.custom(prefs) && tv) 52 else 44), dp(if (glassOs()) 42 else if (ThemeStyle.custom(prefs) && tv) 52 else 44)))
        if (ThemeStyle.id(prefs) == ThemeStyle.PEARL) {
            val rule = View(this)
            rule.setBackgroundColor(0x7778807c)
            val rp = LinearLayout.LayoutParams(dp(1), dp(40))
            rp.marginStart = dp(10)
            identityCard.addView(rule, rp)
        }
        val identity = LinearLayout(this)
        identity.setPadding(dp(12), 0, dp(4), 0)
        identity.orientation = LinearLayout.VERTICAL
        val nameView = text("", if (tvGlass()) 20 else if (tv) 24 else 20, TEXT)
        userName = nameView
        nameView.maxLines = 1
        nameView.ellipsize = TextUtils.TruncateAt.END
        applyAppFont(nameView)
        if (ThemeStyle.custom(prefs)) nameView.setTypeface(nameView.typeface, if (ThemeStyle.id(prefs) == ThemeStyle.DUNE) Typeface.NORMAL else Typeface.BOLD)
        identity.addView(nameView)
        val greetView = text(greetingText(), 13, MUTED)
        greeting = greetView
        applyAppFont(greetView)
        if (prefs.getBoolean("hide_greeting", false) || (harmony() && !HarmonyVisionLayout.showSecondGreeting())) greetView.visibility = View.GONE
        identity.addView(greetView)
        if (glassOs()) {
            nameView.id = R.id.profileName
            nameView.setTextSize(19f)
            nameView.setTypeface(nameView.typeface, Typeface.BOLD)
            greetView.id = R.id.greeting
            greetView.setTextSize(12f)
            greetView.isAllCaps = false
            greetView.setTypeface(greetView.typeface, Typeface.BOLD)
            identity.removeView(greetView)
            identity.addView(greetView)
        }
        identityCard.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        val identityParams = LinearLayout.LayoutParams(0, -2, 1f)
        identityParams.marginEnd = dp(10)
        header.addView(identityCard, identityParams)
        val netStatus = text("", 13, MUTED)
        networkStatus = netStatus
        netStatus.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER
        netStatus.compoundDrawablePadding = dp(5)
        netStatus.isFocusable = false
        netStatus.setOnFocusChangeListener { v, focus ->
            if (glassOs()) updateNetwork()
            else if (focus) v.background = surface(true)
            else v.setBackgroundColor(Color.TRANSPARENT)
        }
        netStatus.setPadding(dp(if (tv) 13 else 11), dp(8), dp(if (tv) 13 else 11), dp(8))
        netStatus.setOnClickListener { openSetting(Settings.ACTION_WIFI_SETTINGS) }
        page.addView(header)
        val bar = LinearLayout(this)
        actions = bar
        bar.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        if (glassOs()) { bar.id = R.id.topButtons; netStatus.isFocusable = true; netStatus.setOnKeyListener { v, _, e -> toolbarKey(v, e) } }
        bar.addView(netStatus, LinearLayout.LayoutParams(-2, dp(44)))
        val weatherView = text("Unavailable", 13, MUTED)
        homeWeather = weatherView
        weatherView.gravity = Gravity.CENTER
        weatherView.setPadding(dp(10), dp(8), dp(10), dp(8))
        if (harmony()) weatherView.visibility = View.GONE
        bar.addView(weatherView, 0, LinearLayout.LayoutParams(-2, dp(44)))
        if (glassOs() && !harmony()) {
            val btStatus = text("Bluetooth", 11, 0xffdbeafe.toInt())
            bluetoothStatus = btStatus
            btStatus.id = R.id.btnBluetooth
            btStatus.maxWidth = dp(130)
            btStatus.isSingleLine = true
            btStatus.ellipsize = TextUtils.TruncateAt.END
            btStatus.setPadding(dp(10), dp(8), dp(10), dp(8))
            btStatus.gravity = Gravity.CENTER
            val btIcon = TvSymbols.icon(this, android.R.drawable.stat_sys_data_bluetooth)
            btIcon?.setBounds(0, 0, dp(18), dp(18))
            btStatus.compoundDrawablePadding = dp(5)
            btStatus.setCompoundDrawables(btIcon, null, null, null)
            btStatus.background = GlassOsUi.panel(this, 999)
            btStatus.isFocusable = true
            btStatus.setOnFocusChangeListener { v, f -> v.background = surface(f) }
            btStatus.setOnClickListener { bluetooth?.show() }
            btStatus.setOnKeyListener { v, _, e -> toolbarKey(v, e) }
            bar.addView(btStatus, LinearLayout.LayoutParams(-2, dp(40)))
            iconAction("Apps", R.drawable.ic_apps, this::showAppLibrary)
        } else if (harmony()) {
            iconAction("All apps", R.drawable.ic_apps, this::showAppLibrary)
        }
        searchButton = iconAction(getString(R.string.h_search), R.drawable.ic_search, this::searchApps)
        iconAction(getString(R.string.h_notifications), R.drawable.ic_notifications, this::showNotifications)
        if (!ThemeStyle.custom(prefs) && !harmony()) iconAction(getString(R.string.h_screensaver), R.drawable.ic_picture) { launch(Intent(this, LandscapeActivity::class.java)) }
        val controls = iconAction("Control Center", R.drawable.ic_settings, this::showSettings)
        if (glassOs()) controls.id = R.id.btnControlCenter
        if (tv) {
            header.addView(bar, LinearLayout.LayoutParams(-2, -2))
        } else {
            styleStatusBar(bar)
            page.addView(bar, LinearLayout.LayoutParams(-1, -2))
        }
        val clock = LinearLayout(this)
        clockLayout = clock
        clock.orientation = LinearLayout.VERTICAL
        clock.gravity = if (weatherWidgetEnabled() || glassOs()) Gravity.START else intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[clockAlignment()]
        val compact = resources.configuration.screenHeightDp < 480
        clock.setPadding(0, dp(if (compact) 0 else if (tv) if (ThemeStyle.custom(prefs)) 32 else 10 else 16), 0, dp(if (compact) 8 else 12))
        var clockSize = prefs.getInt("clock_size", if (tv) 52 else 44)
        // One-time migration (2026-10-31): old sizes were too big, map to new scale.
        val oldSizes = if (tv) intArrayOf(66, 80, 96) else intArrayOf(56, 68, 80)
        val newSizes = if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52)
        for (i in oldSizes.indices) {
            if (clockSize == oldSizes[i]) {
                clockSize = newSizes[i]
                prefs.edit().putInt("clock_size", clockSize).apply()
                break
            }
        }
        clockSize = HomeGeometry.clockSize(clockSize, compact)
        val timeView = text("", clockSize, TEXT)
        time = timeView
        timeView.gravity = Gravity.CENTER
        if (glassOs()) timeView.id = R.id.clock
        timeView.isSingleLine = true
        // Clock shadow for readability on bright wallpapers (toggle).
        if (prefs.getBoolean("clock_shadow", true)) {
            timeView.setShadowLayer(dp(8).toFloat(), 0f, dp(3).toFloat(), 0xcc000000.toInt())
        }
        // Use the regular family with bold weight; the light family overrides the intended weight.
        timeView.typeface = Typeface.create(
                ThemeStyle.clockFontFamily(prefs), if (ThemeStyle.clockBold(prefs)) Typeface.BOLD else Typeface.NORMAL)
        // Analog mode uses a bounded independent dial, including on the Glass gradient.
        val analogClockMode = prefs.getInt("clock_style", 0) == 1 && !harmony()
        // AM/PM is a separate view, not a span: it can never be clipped when
        // seconds make the string longest. (User 2026-11-01.)
        val ampmView = text("", Math.max(14, (clockSize * 0.42f).toInt()), TEXT)
        ampm = ampmView
        ampmView.isSingleLine = true
        if (prefs.getBoolean("clock_shadow", true)) {
            ampmView.setShadowLayer(dp(4).toFloat(), 0f, dp(2).toFloat(), 0xcc000000.toInt())
        }
        if (analogClockMode) {
            timeView.visibility = View.INVISIBLE
            ampmView.visibility = View.INVISIBLE
        }
        // No auto-size: fitClockText() sizes precisely.
        timeView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        // Horizontal padding prevents clipping from the shadow layer.
        timeView.setPadding(dp(12), dp(2), dp(4), dp(10))
        clockTextSize = clockSize
        // Analog face radius follows the clock size (diameter ~= digital height).
        sky.clockRadius = dp(clockSize * 0.85f).toFloat()
        val dateView = text("", if (ThemeStyle.custom(prefs)) if (tv) 20 else 16 else if (tv) 14 else 13, MUTED)
        date = dateView
        if (prefs.getBoolean("clock_shadow", true)) {
            dateView.setShadowLayer(dp(6).toFloat(), 0f, dp(2).toFloat(), 0xcc000000.toInt())
        }
        dateView.gravity = if (weatherWidgetEnabled()) Gravity.START else Gravity.CENTER
        if (glassOs()) { dateView.id = R.id.date; ampmView.id = R.id.ampm; ampmView.setTextSize(18f) }
        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER_VERTICAL or intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[clockAlignment()]
        timeRow.addView(timeView, LinearLayout.LayoutParams(-2, -2))
        val ampmParams = LinearLayout.LayoutParams(-2, -2)
        ampmParams.gravity = if (glassOs()) Gravity.CENTER_VERTICAL else Gravity.BOTTOM
        ampmParams.bottomMargin = dp(if (glassOs()) 0 else 6)
        if (glassOs()) ampmParams.marginStart = dp(8)
        timeRow.addView(ampmView, ampmParams)
        clock.addView(timeRow, LinearLayout.LayoutParams(-1, dp(clockSize + if (compact) 10 else 36)))
        clock.addView(dateView)
        if (glassOs()) {
            timeView.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            ampmView.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val ampmBadge = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(if (harmony()) 0xff406c92.toInt() else 0xff1b3855.toInt(), if (harmony()) 0xff183b65.toInt() else 0xff102941.toInt()))
            ampmBadge.cornerRadius = dp(10).toFloat()
            ampmBadge.setStroke(dp(1), 0x7796bdf5)
            ampmView.setTextColor(Color.WHITE)
            ampmView.setTextSize(20f)
            ampmView.background = ampmBadge
            ampmView.setPadding(dp(8), dp(4), dp(8), dp(4))
            ampmView.minWidth = dp(56)
            ampmView.minHeight = dp(36)
            ampmView.gravity = Gravity.CENTER
            ampmView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            clock.setPadding(0, 0, 0, 0)
            val islamicView = text("", 13, MUTED)
            islamicDate = islamicView
            islamicView.id = R.id.lunarDate
            clock.addView(islamicView)
            if (clockDatesInline) {
                clock.removeView(dateView)
                clock.removeView(islamicView)
                val divider = View(this)
                divider.id = R.id.clockDivider
                divider.setBackgroundColor(0x38ffffff)
                val line = LinearLayout.LayoutParams(dp(1.5f), dp(48))
                line.setMargins(dp(12), 0, dp(12), 0)
                timeRow.addView(divider, line)
                val dates = LinearLayout(this)
                dates.orientation = LinearLayout.VERTICAL
                dateView.setTextSize(18f)
                dateView.setTypeface(dateView.typeface, Typeface.BOLD)
                dateView.gravity = Gravity.START
                dates.addView(dateView)
                dates.addView(islamicView)
                timeRow.addView(dates, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        if (!glassOs()) {
            val islamicView = text("", 12, MUTED)
            islamicDate = islamicView
            islamicView.id = R.id.lunarDate
            clock.addView(islamicView)
        }
        if (analogClockMode) {
            timeRow.visibility = View.GONE
            clock.removeView(dateView)
            clock.removeView(islamicDate)
            val analogRow = LinearLayout(this)
            analogRow.gravity = Gravity.CENTER_VERTICAL
            analogRow.setPadding(dp(if (glassOs()) 16 else 8), 0, 0, 0)
            val dial = SweepAnalogClock(this)
            analogClock = dial
            val preferred = dp(if (glassOs()) 208 else 196)
            analogRow.addView(dial, LinearLayout.LayoutParams(preferred, preferred))
            val dates = LinearLayout(this)
            dates.orientation = LinearLayout.VERTICAL
            dates.gravity = Gravity.CENTER_VERTICAL
            dateView.setTextSize(15f)
            dateView.gravity = Gravity.START
            dateView.maxLines = 2
            islamicDate?.setTextSize(13f)
            islamicDate?.maxLines = 2
            islamicDate?.setPadding(0, dp(10), 0, 0)
            dates.addView(dateView, LinearLayout.LayoutParams(-1, -2))
            dates.addView(islamicDate, LinearLayout.LayoutParams(-1, -2))
            val dpDates = LinearLayout.LayoutParams(0, -2, 1f)
            dpDates.marginStart = dp(16)
            analogRow.addView(dates, dpDates)
            clock.addView(analogRow, 0, LinearLayout.LayoutParams(-1, -2))
            dial.active(resumed)
            analogRow.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                if (r - l <= 0) return@addOnLayoutChangeListener
                val available = Math.max(1, r - l - analogRow.paddingLeft)
                var diameter = Math.min(preferred, Math.max(dp(144), available - dp(132)))
                diameter = Math.min(diameter, available)
                val lp = dial.layoutParams as LinearLayout.LayoutParams
                if (lp.width != diameter) { lp.width = diameter; lp.height = diameter; dial.layoutParams = lp }
                val pos = IntArray(2)
                val origin = IntArray(2)
                dial.getLocationInWindow(pos)
                root.getLocationInWindow(origin)
                sky.clockX = pos[0] - origin[0] + dial.width / 2f
                sky.clockY = pos[1] - origin[1] + dial.height / 2f
                sky.invalidate()
            }
        }
        timeRow.addOnLayoutChangeListener { _, l, _, r, _, ol, _, orOld, _ ->
            if (r - l != orOld - ol && timeView.text.isNotEmpty()) fitClockText(timeView.text)
        }
        if (glassOs() && !harmony() && !analogClockMode) {
            val spotlight = text("", 13, 0xffffffff.toInt())
            focusedSpotlight = spotlight
            spotlight.isSingleLine = true
            spotlight.ellipsize = TextUtils.TruncateAt.END
            spotlight.setPadding(dp(13), dp(7), dp(13), dp(7))
            spotlight.background = GlassOsUi.panel(this, 999)
            val sp = LinearLayout.LayoutParams(-2, -2)
            sp.topMargin = dp(8)
            clock.addView(spotlight, sp)
            updateSpotlight()
        }
        timeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val position = IntArray(2)
            val origin = IntArray(2)
            timeView.getLocationInWindow(position)
            root.getLocationInWindow(origin)
            sky.clockX = position[0] - origin[0] + timeView.width / 2f
            sky.clockY = position[1] - origin[1] + timeView.height / 2f
            sky.invalidate()
            if (updateWallpaperClockInk()) adaptVisibleTextColors()
        }
        val heading = text("Your apps", prefs.getInt("focus_title_size", if (tv) 32 else 26), TEXT)
        appHeading = heading
        heading.isSingleLine = true
        heading.ellipsize = TextUtils.TruncateAt.END
        heading.setPadding(dp(4), dp(8), 0, dp(12))
        val grid = HomeAppGrid<App>(this, { app -> app.info.activityInfo.packageName },
                { app, width ->
                    val carried = carriedApp
                    createAppTile(app, width, carried != null && app.info.activityInfo.packageName == carried.info.activityInfo.packageName)
                },
                harmonyHome)
        this.grid = grid
        grid.setReferenceExpansion(glassOs() && !harmony())
        grid.setUpFromApps {
            if (harmony() && focusHarmonyHero()) return@setUpFromApps
            val widget = weatherWidget
            if (widget != null && widget.isShown) widget.requestFocus()
            else searchButton?.requestFocus()
        }
        grid.clipChildren = false
        if (glassOs()) {
            grid.id = R.id.grid
            grid.setPadding(0, dp(if (harmony()) 5 else 16), 0, dp(if (harmony()) 4 else 18))
            if (!harmony()) grid.addItemDecoration(DockDecoration.Reflections())
        }
        grid.clipToPadding = glassOs()
        if (harmony()) grid.clipChildren = true
        val body = LinearLayout(this)
        appBody = body
        body.orientation = LinearLayout.VERTICAL
        if (harmony()) { body.clipChildren = true; body.clipToPadding = true }
        if (harmonyHome) body.background = InsetDrawable(HarmonyVisionSurface.dock(this), 0, dp(18), 0, 0)
        body.setPadding(0, 0, 0, 0)
        body.clipToOutline = false
        if (ThemeStyle.id(prefs) == ThemeStyle.COBALT) grid.addItemDecoration(CobaltRails(dp(1).toFloat()))
        if (ThemeStyle.custom(prefs) && tv) grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeDemoCards() }
        val watching = if (harmony()) null else ContinueWatchingView(this) { label ->
            if (!prefs.getBoolean("apps_only", false)) appHeading?.text = label
        }
        continueWatching = watching
        if (watching != null && !prefs.getBoolean("apps_only", false)) grid.setFooter(watching)
        body.addView(grid, LinearLayout.LayoutParams(-1, -1))
        val scrollContent = LinearLayout(this)
        scrollContent.orientation = LinearLayout.VERTICAL
        if (glassOs() && !harmony()) scrollContent.gravity = Gravity.CENTER_VERTICAL
        if (!clockVisible()) clock.visibility = View.GONE
        val align = clockAlignment()
        val clockParams = LinearLayout.LayoutParams(-1, -2)
        clockParams.gravity = intArrayOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)[align]
        if (harmonyHome) {
            scrollContent.addView(harmonyHero(), LinearLayout.LayoutParams(-1, -2))
        } else if (glassOs() || weatherWidgetEnabled()) {
            val stage = LinearLayout(this)
            stage.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            val wide = resources.configuration.screenWidthDp >= 700
            stage.orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) stage.addView(clock, LinearLayout.LayoutParams(0, -2, 1f)) else stage.addView(clock, clockParams)
            val instruments = LinearLayout(this)
            instruments.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            if (!wide) instruments.orientation = LinearLayout.VERTICAL
            val available = resources.configuration.screenWidthDp - if (tv) 64 else 32
            if (weatherWidgetEnabled()) {
                val widget = WeatherWidgetView(this, homeWeatherController::showForecast)
                weatherWidget = widget
                val wp = LinearLayout.LayoutParams(dp(Math.min(available, WeatherWidgetLayout.width(available, glassOs()))), -2)
                if (glassOs() && wide) wp.marginEnd = dp(12) else if (!wide) wp.bottomMargin = dp(10)
                instruments.addView(widget, wp)
                widget.setOnKeyListener { _, key, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN) { grid.focusKey(focusedPkg); return@setOnKeyListener true }
                    if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_LEFT) { searchButton?.requestFocus(); return@setOnKeyListener true }
                    key == KeyEvent.KEYCODE_DPAD_RIGHT
                }
                widget.active(resumed)
            }
            if (glassOs()) {
                val cardWidth = if (wide) (if (weatherWidgetEnabled()) WeatherWidgetLayout.telemetryWidth(available) else Math.min(315, resources.configuration.screenWidthDp / 3)) else Math.min(280, available)
                if (harmony()) instruments.addView(harmonyShortcuts(), LinearLayout.LayoutParams(dp(cardWidth), -2))
                else {
                    val tele = GlassTelemetry(this)
                    telemetry = tele
                    instruments.addView(tele, LinearLayout.LayoutParams(dp(cardWidth), -2))
                    if (resumed) tele.start()
                }
            }
            stage.addView(instruments)
            if (prefs.getBoolean("apps_only", false)) stage.visibility = View.GONE
            scrollContent.addView(stage, LinearLayout.LayoutParams(-1, -2))
        } else scrollContent.addView(clock, clockParams)
        val referenceStage = glassOs() && !prefs.getBoolean("apps_only", false)
        scroll.addView(scrollContent, ViewGroup.LayoutParams(-1, -2))
        page.addView(scroll, if (referenceStage) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2))
        if (harmony() || ThemeStyle.custom(prefs) || glassOs()) heading.visibility = View.GONE
        page.addView(heading)
        page.addView(body, if (referenceStage) LinearLayout.LayoutParams(-1, referenceDockHeight()) else LinearLayout.LayoutParams(-1, 0, 1f))
        // Focus-glow tracker: keep the wallpaper glow on the focused tile no matter
        // which scroller moved.
        val glowTracker = View.OnScrollChangeListener { _, _, _, _, _ ->
            val focused = currentFocus
            if (focused != null && focused.tag is Int && prefs.getBoolean("focus_glow", true)) {
                val position = IntArray(2)
                val origin = IntArray(2)
                focused.getLocationInWindow(position)
                sky.getLocationInWindow(origin)
                sky.focusX = position[0] - origin[0] + focused.width / 2f
                sky.focusY = position[1] - origin[1] + focused.height / 2f
                sky.invalidate()
            }
        }
        // Per-view adaptive text: update each label's dark/light scheme as it moves
        // over the wallpaper during scroll. (User request 2026-10-31.)
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                adaptVisibleTextColors()
                glowTracker.onScrollChange(view, 0, 0, 0, 0)
            }
        })
        scroll.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            adaptVisibleTextColors()
            glowTracker.onScrollChange(v, scrollX, scrollY, oldScrollX, oldScrollY)
        }
        page.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = page.width - page.paddingLeft - page.paddingRight
            if (width > 0 && width != pageWidth) {
                pageWidth = width
                if (referenceStage) {
                    val bodyParams = body.layoutParams as LinearLayout.LayoutParams
                    val height = referenceDockHeight()
                    if (bodyParams.height != height) { bodyParams.height = height; body.layoutParams = bodyParams }
                }
                val apps = cachedApps
                if (apps != null) renderApps(apps)
                else loadApps()
            }
        }
        refreshProfile()
        updateClock()
        updateNetwork()
        // No wallpaper-capture layer is needed behind the open app grid.
        if (!glassOs() && headerBgMode() != 0 && (!ThemeStyle.custom(prefs) || headerBgMode() == 2)) headerBackdrop = if (tv)
                LiquidBackdrop(sky, appWorker, handler, if (headerBgMode() == 2) 2 else -1, header)
                else LiquidBackdrop(sky, appWorker, handler, if (headerBgMode() == 2) 2 else -1, header, bar)
        adaptTextColors()
        // Reload custom wallpaper if it has not been decoded, and check Bing for a new image.
        if ("custom" == prefs.getString("wallpaper_mode", "mountains") && customWallpaper == null) {
            loadWallpaper()
        }
        updateOnlineWallpaper()
        val brightness = window.attributes
        brightness.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = brightness
        if (resumed && watching != null && !prefs.getBoolean("apps_only", false)) watching.start()
        BootLog.mark("home buildHome", buildMs)
        if (firstBuild) {
            firstBuild = false
            // First actually-drawn frame, once: onCreate duration alone is not visible startup time.
            root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    BootLog.mark("home first frame", onCreateStartMs)
                    return true
                }
            })
        }
    }

    /** Time-based greeting, e.g. "Subah Bakhair". */
    private fun greetingText(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (glassOs()) return if (hour >= 5 && hour < 12) "Good morning" else if (hour < 17 && hour >= 12) "Good afternoon" else if (hour >= 17 && hour < 22) "Good evening" else "Good night"
        if (hour >= 5 && hour < 12) return "Subah Bakhair"
        if (hour >= 12 && hour < 17) return "Dopahar Bakhair"
        if (hour >= 17 && hour < 22) return "Shaam Bakhair"
        return "Shab Bakhair"
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        val seconds = prefs.getBoolean("clock_seconds", false)
        val is24 = prefs.getBoolean("time_24h", false)
        var value: String
        val ampmText: String
        if (is24) {
            value = String.format(Locale.getDefault(), "%02d:%02d",
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND))
            ampmText = ""
        } else {
            val h12 = if (now.get(Calendar.HOUR) == 0) 12 else now.get(Calendar.HOUR)
            value = String.format(Locale.getDefault(), "%02d:%02d", h12, now.get(Calendar.MINUTE))
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND))
            ampmText = if (now.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
        fitClockText(value)
        val ampmView = ampm
        if (ampmView != null) {
            ampmView.text = ampmText
            val analog = prefs.getInt("clock_style", 0) == 1
            ampmView.visibility = if (analog) View.INVISIBLE else if (ampmText.isEmpty()) View.GONE else View.VISIBLE
        }
        date?.text = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(now.time)
        harmonyDateValue?.text = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(now.time)
        harmonyGreetingValue?.text = greetingText()
        val quoteView = harmonyQuoteValue
        if (quoteView != null && prefs.getBoolean("harmony_daily_quote", true)) {
            val today = HarmonyDailyWords.forCalendar(now)
            if (!today.contentEquals(quoteView.text)) quoteView.text = today
        }
        val islamicView = islamicDate
        if (islamicView != null) try {
            val today = LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
            islamicView.text = HijriCalendar.format(today, prefs.getInt("hijri_adjustment", 0))
        } catch (unavailable: DateTimeException) {
            islamicView.text = "Islamic date unavailable"
        }
        val skyView = sky
        if (skyView != null) {
            skyView.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            skyView.clockStyle = 0 // The independent SweepAnalogClock draws analog mode in every theme.
            skyView.invalidate()
        }
        greeting?.text = greetingText()
    }

    /**
     * Sizes the clock text so the whole string always fits. AM/PM lives in its
     * own view now, so there is no span left to mismeasure. Measured precisely
     * with a StaticLayout, then scaled down only if needed; never below 26sp.
     */
    private fun fitClockText(text: CharSequence) {
        val timeView = time ?: return
        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        var maxWidth = (if (pageWidth > 0) pageWidth else screenW) - dp(24)
        if (maxWidth <= 0) maxWidth = screenW - dp(24)
        val parent = timeView.parent
        if ((glassOs() || weatherWidgetEnabled()) && parent is View && parent.width > 0)
            maxWidth = Math.max(dp(70), parent.width - dp(if (clockDatesInline) 260 else 80))
        val paint = TextPaint()
        paint.typeface = timeView.typeface
        var sizePx = clockTextSize * density
        paint.textSize = sizePx
        val layout = StaticLayout.Builder.obtain(
                text, 0, text.length, paint, Int.MAX_VALUE)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        var w = 0f
        for (i in 0 until layout.lineCount) w = Math.max(w, layout.getLineWidth(i))
        if (w > maxWidth && w > 0) sizePx = Math.max(sizePx * maxWidth / w, 26 * density)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        timeView.text = text
    }

    /** Per-second ticker, only active when clock seconds are enabled. */
    private val secondTick = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!resumed || !prefs.getBoolean("clock_seconds", false)) return
            updateClock()
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000))
        }
    }

    /** Loads today's Bing wallpaper when the online source is selected. */
    private fun updateOnlineWallpaper() {
        val skyView = sky
        if (skyView == null || prefs.getInt("wallpaper_source", 0) != 1) return
        skyView.loadOnlinePhoto(0)
    }

    /** Refreshes the Continue Watching row (banner rows were removed 2026-11-01). */
    private fun updateContentRows() {
        continueWatching?.reload()
    }

    private fun updateNetwork() {
        val status = networkStatus ?: return
        val conn = connectivity
        val active = conn?.activeNetwork
        val caps = if (active == null) null else conn?.getNetworkCapabilities(active)
        val online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        status.text = if (ThemeStyle.custom(prefs)) "" else if (online) "Connected" else if (caps == null) "Disconnected" else "No internet access"
        status.contentDescription = if (online) "Network connected" else if (caps == null) "Network disconnected" else "Network without internet"
        status.tooltipText = if (online) (if (wifi) "Wi-Fi · Internet available" else "Internet available") else "No internet"
        val color = if (online) 0xff59e493.toInt() else 0xffff6c78.toInt()
        status.setTextColor(if (ThemeStyle.custom(prefs)) HeaderAppearance.foreground(prefs) else color)
        val iconId = if (wifi || caps == null) R.drawable.ic_wifi
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) R.drawable.ic_ethernet else R.drawable.ic_internet
        val icon = TvSymbols.icon(this, iconId)
        icon?.setTint(if (ThemeStyle.custom(prefs)) HeaderAppearance.foreground(prefs) else color)
        icon?.setBounds(0, 0, dp(28), dp(24))
        status.setCompoundDrawables(icon, null, null, null)
        status.contentDescription = if (online) "Internet available. Open Wi-Fi settings" else "No validated internet access. Open Wi-Fi settings"
        if (glassOs()) {
            val transport = if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) "Ethernet" else if (wifi || caps == null) "Wi-Fi" else "Internet"
            status.text = "● " + transport + " • " + if (online) "Online" else "No Internet"
            status.setTextSize(11f)
            icon?.setBounds(0, 0, dp(18), dp(18))
            status.setCompoundDrawables(icon, null, null, null)
            status.setTextColor(if (online) 0xffdcfce7.toInt() else 0xfffee2e2.toInt())
            val state = GradientDrawable()
            state.setColor(if (online) 0x8014532d.toInt() else 0x947f1d1d.toInt())
            state.cornerRadius = dp(999).toFloat()
            state.setStroke(dp(if (status.isFocused) 2 else 1), if (online) 0xb322c55e.toInt() else 0xccef4444.toInt())
            status.background = state
            val photo = avatar
            if (photo is AppAccentAvatar) photo.online(online)
        }
        homeWeatherController.refresh()
    }

    @Volatile
    private var appGeneration = 0
    private fun loadApps() {
        if (carriedApp != null) { carriedApp = null; moveOrder = null }
        val generation = ++appGeneration
        val prior = cachedApps
        appWorker.execute {
            val found = ArrayList<ResolveInfo>()
            if (tv) found.addAll(packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0))
            found.addAll(packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
            val packages = HashSet<String>()
            val apps = ArrayList<App>()
            val previous = HashMap<String, App>()
            val snapshot = prior
            if (snapshot != null) for (app in snapshot) previous[app.info.activityInfo.packageName] = app
            // Phase 1: labels only — the grid renders immediately with letter placeholders.
            for (info in found) {
                if (generation != appGeneration) return@execute
                val pkg = info.activityInfo.packageName
                if (pkg == packageName || !packages.add(pkg)) continue
                try {
                    val label = info.loadLabel(packageManager).toString()
                    val old = previous[pkg]
                    if (old != null && old.iconReady && old.name == label
                            && java.util.Objects.equals(old.info.activityInfo.applicationInfo.sourceDir, info.activityInfo.applicationInfo.sourceDir)) {
                        apps.add(App(label, info, old.icon, old.accent, true))
                    } else {
                        apps.add(App(label, info, letterIcon(label), CYAN, false))
                    }
                } catch (removedDuringQuery: RuntimeException) {
                    // A package can be replaced or removed between queryIntentActivities and loading its resources.
                }
            }
            apps.sortWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            handler.post {
                if (!isDestroyed && generation == appGeneration && !sameApps(cachedApps, apps)) renderApps(apps)
            }
        }
    }

    /** Only bound/visible tiles request artwork. Serial work and generation checks bound concurrency. */
    private fun requestAppIcon(app: App) {
        if (app.iconReady || app.iconRequest.pending(appGeneration) || isDestroyed) return
        val generation = appGeneration
        app.iconRequest.begin(generation)
        appWorker.execute {
            if (generation != appGeneration || isDestroyed) return@execute
            val pkg = app.info.activityInfo.packageName
            try {
                if (harmony()) {
                    val nativeIcon = app.info.loadIcon(packageManager)
                    handler.post {
                        if (!app.iconRequest.finish(generation)) return@post
                        if (!isDestroyed && generation == appGeneration)
                            updateTileIcon(pkg, nativeIcon, ThemeStyle.accent(prefs))
                    }
                    return@execute
                }
                val cache = IconCache(this)
                val pm = packageManager
                val version = IconCache.version(pm, pkg)
                val cachedCard = cache.load(pkg, version)
                val finished: Bitmap
                val finishedAccent: Int
                if (cachedCard != null) {
                    finished = cachedCard
                    finishedAccent = cache.accent(pkg, version, CYAN)
                } else {
                    var logo = app.info.loadIcon(pm)
                    if (pkg == "com.google.android.youtube.tv" || pkg == "com.google.android.youtube") logo = getDrawable(R.drawable.logo_youtube)!!
                    if (pkg == "com.netflix.ninja" || pkg == "com.netflix.mediaclient") logo = getDrawable(R.drawable.logo_netflix)!!
                    if (cache.mark()) logo = DemoBrandMark.choose(pkg, logo)
                    val artwork = AppArtwork.create(resources, logo, cache.logoScale(), cache.mark())
                    finished = (artwork.image as BitmapDrawable).bitmap
                    finishedAccent = artwork.accent
                    cache.save(pkg, version, finished, finishedAccent)
                }
                handler.post {
                    if (!app.iconRequest.finish(generation)) return@post
                    if (!isDestroyed && generation == appGeneration) {
                        updateTileIcon(pkg, BitmapDrawable(resources, finished), finishedAccent)
                        trimAppIcons()
                    }
                }
            } catch (removed: RuntimeException) { handler.post { app.iconRequest.finish(generation) } }
        }
    }

    private fun trimAppIcons() {
        val apps = cachedApps ?: return
        var bytes = 0L
        for (app in apps) {
            val icon = app.icon
            if (icon is BitmapDrawable) bytes += icon.bitmap.allocationByteCount
        }
        val budget = MemoryProfile.iconCacheBytes(MemoryProfile.effectiveTier(this, prefs))
        for (app in apps) {
            if (bytes <= budget) break
            if (!app.iconReady || tileViews.containsKey(app.info.activityInfo.packageName)) continue
            val icon = app.icon
            if (icon is BitmapDrawable) bytes -= icon.bitmap.allocationByteCount
            app.icon = null
            app.iconReady = false // Visible tiles will reload from disk; never recycle a drawn bitmap.
        }
    }

    /** Cheap letter placeholder so the grid is useful before icons finish. Runs on the worker thread. */
    private fun letterIcon(name: String): Drawable {
        val square = harmony()
        val bitmap = Bitmap.createBitmap(if (square) 96 else 160, if (square) 96 else 90, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (square) {
            paint.color = 0xff537d9a.toInt()
            canvas.drawRoundRect(5f, 5f, 91f, 91f, 21f, 21f, paint)
            paint.color = 0xfff5fbff.toInt()
            paint.textSize = 47f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            val letter = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(Locale.ROOT)
            canvas.drawText(letter, 48f, 64f, paint)
        } else {
            if (!ThemeStyle.custom(prefs)) canvas.drawColor(0xff0d2036.toInt())
            paint.color = if (ThemeStyle.custom(prefs)) accentColor() else 0x6640d9ff
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            val letter = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase(Locale.ROOT)
            canvas.drawText(letter, 80f, 60f, paint)
        }
        return BitmapDrawable(resources, bitmap)
    }

    /** Swaps a finished icon into the live tile and the cached App. Runs on the UI thread. */
    private fun updateTileIcon(pkg: String, icon: Drawable, accent: Int) {
        val apps = cachedApps
        if (apps != null) {
            for (app in apps) {
                if (app.info.activityInfo.packageName == pkg) {
                    app.icon = icon
                    app.accent = accent
                    app.iconReady = true
                    break
                }
            }
        }
        if (carriedApp != null) return
        val tile = tileViews[pkg]
        if (tile is HarmonyAppTile) { tile.artwork(icon, accent); tile.setTag(accent) }
        else if (tile is GlassIconTile) { tile.artwork(icon, accent); tile.setTag(accent); if (tile.isFocused) updateGlassAccent(accent) }
        else if (tile is FrameLayout) {
            val iconView = tile.getChildAt(0)
            if (iconView is ImageView) iconView.setImageDrawable(icon)
            tile.foreground = tileBorder(accent, tile.isFocused)
            tile.setTag(accent)
            if (glassOs()) tile.background = appSurface(accent, tile.isFocused)
            if (glassOs() && tile.isFocused) updateGlassAccent(accent)
        }
    }

    private class App(
        val name: String,
        val info: ResolveInfo,
        var icon: Drawable?,
        var accent: Int,
        var iconReady: Boolean
    ) {
        val iconRequest = ArtworkRequestState()
    }

    private fun renderApps(apps: List<App>) {
        val g = grid!!
        val restoreFocus = !g.footerFocusedOrPending() && (g.hasFocus() || (focusedPkg != null && !hasWindowFocus()))
        cachedApps = apps
        appHeading?.text = if (harmony()) "YOUR APPS" else getString(R.string.h_your_apps)
        val library = HomeLibraryStore(this)
        val visible = orderedApps(apps)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { app -> hidden?.contains(app.info.activityInfo.packageName) == true
                || (selectedCategory.isNotEmpty() && selectedCategory != library.category(app.info.activityInfo.packageName)) }
        val avail = gridWidth()
        val columns = if (harmony()) HarmonyVisionLayout.dockColumns(Math.round(avail / resources.displayMetrics.density))
                else PersonalHomeModel.columns((avail / resources.displayMetrics.density).toInt(),
                prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0), prefs.getBoolean("large_tiles", false))
        submitHomeApps(visible, columns, avail)
        if (movingRendered) { movingRendered = false; g.rebind() }
        val state = gridState
        if (state != null) { g.layoutManager?.onRestoreInstanceState(state); gridState = null }
        appHeading?.setText(if (visible.isEmpty()) R.string.h_no_apps else R.string.h_your_apps)
        if (restoreFocus && (panel == null || panel?.isShowing != true) && (searchDialog == null || searchDialog?.isShowing != true))
            g.focusKey(focusedPkg)
        searchRefresh?.run()
    }

    private fun submitHomeApps(apps: List<App>, columns: Int, availableWidth: Int) {
        val g = grid!!
        if (!g.isHorizontalDock()) {
            g.submit(apps, columns, availableWidth / columns)
            return
        }
        val height = resources.configuration.screenHeightDp
        val cellWidth = Math.min(availableWidth, dp(HarmonyVisionLayout.dockCellWidthDp(height)))
        val side = HarmonyVisionLayout.dockSidePadding(availableWidth, cellWidth, apps.size)
        g.setPadding(side, dp(4), side, dp(4))
        g.submit(apps, 1, cellWidth)
    }

    private fun chooseCategoryFilter() {
        val names = HomeLibraryStore(this).categories()
        names.add(0, getString(R.string.h_all_apps))
        ThemeDialogBuilder(this).setTitle(R.string.h_categories).setItems(names.toTypedArray()) { _, which ->
            selectedCategory = if (which == 0) "" else names[which]
            gridState = null
            cachedApps?.let { renderApps(it) }
        }.show()
    }

    private fun profileAsset(name: String): File = File(filesDir, HomeLibraryStore(this).assetName(name))

    /** Direct move mode: long-press > Move, then arrows carry the tile, OK drops it, Back cancels. */
    private fun startMoveMode(app: App) {
        val apps = cachedApps ?: return
        val visible = orderedApps(apps)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { a -> hidden?.contains(a.info.activityInfo.packageName) == true }
        val order = ArrayList<String>()
        moveOrder = order
        for (a in visible) order.add(a.info.activityInfo.packageName)
        selectedCategory = ""
        carriedApp = app
        renderMoveGrid()
        Toast.makeText(this, "Arrows move · OK places · Back cancels", Toast.LENGTH_LONG).show()
    }

    private fun renderMoveGrid() {
        movingRendered = true
        val byPackage = LinkedHashMap<String, App>()
        for (app in cachedApps!!) byPackage[app.info.activityInfo.packageName] = app
        val carried = carriedApp!!
        appHeading?.text = "Move " + carried.name
        val avail = gridWidth()
        val g = grid!!
        val columns = g.columnCount
        val movingPkg = carried.info.activityInfo.packageName
        val moved = ArrayList<App>()
        for (pkg in moveOrder!!) {
            val app = byPackage[pkg]
            if (app != null) moved.add(app)
        }
        submitHomeApps(moved, columns, avail)
        g.rebind()
        g.focusKey(movingPkg)
    }

    private fun commitMove() {
        // Hidden apps keep their slots; visible ones take the new order.
        val order = moveOrder!!
        val visibleSet = HashSet(order)
        val moved = order.iterator()
        val result = ArrayList<String>()
        for (app in orderedApps(cachedApps!!)) {
            val pkg = app.info.activityInfo.packageName
            result.add(if (visibleSet.contains(pkg)) moved.next() else pkg)
        }
        prefs.edit().putString("app_order", org.json.JSONArray(result).toString()).apply()
        carriedApp = null
        moveOrder = null
        renderApps(cachedApps!!)
        focusFirstApp()
        Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show()
    }

    private fun cancelMove() {
        carriedApp = null
        moveOrder = null
        renderApps(cachedApps!!)
        focusFirstApp()
    }

    /** System navigation sounds: tick on arrows, click on OK. */
    private fun playNavSound(keyCode: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        var fx = -1
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> fx = AudioManager.FX_FOCUS_NAVIGATION_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> fx = AudioManager.FX_KEY_CLICK
        }
        if (fx != -1) audio.playSoundEffect(fx)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (settingsRemoteKeys.contains(key) && !SettingsKeyPolicy.isNumberEntryKey(key)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) RemoteKeys.launchHomeSettings(this)
            return true
        }
        if (VolumeKeys.direction(event.keyCode) != 0
                && !PassiveHomeService.enabled(this) && VolumeKeys.control(this, event)) return true
        if (event.action == KeyEvent.ACTION_DOWN) playNavSound(event.keyCode)
        // Press effect: tile dips slightly the moment OK is pressed.
        if (event.action == KeyEvent.ACTION_DOWN && carriedApp == null
                && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
                || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val focused = currentFocus
            if (focused != null && focused !is HarmonyAppTile && tileViews.containsValue(focused)) {
                focused.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            }
        }
        val carried = carriedApp
        if (carried != null && event.action == KeyEvent.ACTION_DOWN) {
            val pkg = carried.info.activityInfo.packageName
            val order = moveOrder!!
            val from = order.indexOf(pkg)
            val g = grid!!
            val cols = Math.max(1, g.columnCount)
            val code = event.keyCode
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                commitMove()
                return true
            }
            if (code == KeyEvent.KEYCODE_BACK) { cancelMove(); return true }
            var to = -1
            if (g.isHorizontalDock() && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                val forward = (code == KeyEvent.KEYCODE_DPAD_RIGHT) == (g.layoutDirection != View.LAYOUT_DIRECTION_RTL)
                to = AppGridNavigation.horizontal(from, order.size, forward)
                if (to == from) to = -1
            } else if (g.isHorizontalDock() && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                return true
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (from % cols != 0) to = from - 1 }
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (from % cols != cols - 1 && from + 1 < order.size) to = from + 1 }
            else if (code == KeyEvent.KEYCODE_DPAD_UP) { if (from - cols >= 0) to = from - cols }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) { if (from + cols < order.size) to = from + cols }
            else return super.dispatchKeyEvent(event)
            if (to >= 0) {
                Collections.swap(order, from, to)
                renderMoveGrid()
            }
            return true
        }
        val g = grid
        if (g != null && g.navigateKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private val demoPlacement: Runnable = Runnable(::placeDemoCards)
    private fun placeDemoCards() {
        val g = grid
        if (isDestroyed || g == null || !ThemeStyle.custom(prefs) || !tv || g.childCount == 0) return
        val row = g.getChildAt(0).height
        val top = Math.max(dp(10), Math.min(g.height / 4, g.height - row * 2 - dp(14)))
        if (g.paddingTop != top) g.setPadding(0, top, 0, dp(14))
    }

    private fun referenceCardHeight(cellWidth: Int): Int = referenceCardHeight(cellWidth, grid?.columnCount ?: 4)
    private fun referenceCardHeight(cellWidth: Int, columns: Int): Int {
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val margin = (if (tvGlass()) intArrayOf(dp(6), dp(10), dp(16)) else intArrayOf(dp(4), dp(8), dp(14)))[spacing]
        val widthDp = Math.round((GlassDockLayout.compactCellWidth(cellWidth * columns, columns) - margin * 2) / resources.displayMetrics.density)
        var height = GlassDockLayout.iconTileHeight(widthDp, resources.configuration.screenHeightDp, referenceCaptionHeight())
        if (harmony()) height = HarmonyVisionLayout.dockTileHeightDp(resources.configuration.screenHeightDp)
        return dp(height)
    }
    private fun referenceCaptionHeight(): Int = GlassDockLayout.captionHeight(prefs.getInt("app_label_size", 16), resources.configuration.fontScale)
    private fun referenceDockHeight(): Int {
        if (harmony()) return dp(HarmonyVisionLayout.dockHeightDp(resources.configuration.screenHeightDp))
        val available = if (pageWidth > 0) gridWidth() else dp(Math.max(160, resources.configuration.screenWidthDp - if (tv) 64 else 32))
        val cols = if (harmony()) HarmonyVisionLayout.dockColumns(Math.round(available / resources.displayMetrics.density)) else PersonalHomeModel.columns(Math.round(available / resources.displayMetrics.density), prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0), prefs.getBoolean("large_tiles", false))
        val card = referenceCardHeight(available / cols, cols)
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val margin = intArrayOf(6, 8, 12)[spacing]
        return card + dp(GlassDockLayout.reflectionHeight(Math.round(card / resources.displayMetrics.density)) + margin * 2 + 44)
    }
    private fun createAppTile(app: App, width: Int, moving: Boolean): View {
        requestAppIcon(app)
        val g = grid!!
        val tile: FrameLayout = if (harmony()) HarmonyAppTile(this, app.name, app.icon, app.accent, HarmonyVisionLayout.dockPlateDp(resources.configuration.screenHeightDp), g.isHorizontalDock())
                else if (glassOs()) GlassIconTile(this, app.name, app.icon, app.accent, referenceCardHeight(width) - dp(referenceCaptionHeight()), dp(referenceCaptionHeight()), tileRadius(), moving)
                else if (ThemeStyle.custom(prefs)) DemoTile(this, ThemeStyle.id(prefs), tileRadius().toFloat())
                else FrameLayout(this)
        if (tile !is GlassIconTile && tile !is HarmonyAppTile) {
            tile.background = appSurface(app.accent, false)
            tile.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, tileRadius().toFloat())
                }
            }
            tile.clipToOutline = !ThemeStyle.custom(prefs)
        } else tile.clipToOutline = false
        tile.elevation = (if (perfAllows("parallax")) dp(if (tvGlass()) 4 else 3) else 0).toFloat()
        tile.isFocusable = true
        tile.isClickable = true
        tile.contentDescription = app.name
        tile.setTag(app.accent)
        if (tile !is GlassIconTile && tile !is HarmonyAppTile) tile.foreground = tileBorder(app.accent, false)
        if (moving) {
            tile.scaleX = 1.07f
            tile.scaleY = 1.07f
            tile.elevation = dp(10).toFloat()
            if (tile is HarmonyAppTile) tile.appearance(true)
            else if (tile is GlassIconTile) tile.appearance(app.accent, true)
            else tile.foreground = tileBorder(accentColor(), true)
        }
        val icon = ImageView(this)
        if (app.icon != null) icon.setImageDrawable(app.icon)
        else icon.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_apps))
        // Artwork is composed at the card's 16:9 ratio; no second inset or crop here.
        icon.scaleType = if (ThemeStyle.custom(prefs)) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.FIT_XY
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (ThemeStyle.custom(prefs)) {
            val logoWidth = Math.max(dp(26), Math.round(width * 0.25f))
            val logo = FrameLayout.LayoutParams(logoWidth, -1, Gravity.START or Gravity.CENTER_VERTICAL)
            logo.marginStart = dp(10)
            logo.topMargin = dp(10)
            logo.bottomMargin = dp(10)
            tile.addView(icon, logo)
            val title = text(app.name, prefs.getInt("app_label_size", if (tv) 17 else 14), ThemeStyle.foreground(prefs))
            title.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), if (ThemeStyle.light(prefs)) 0xeeffffff.toInt() else 0xee000000.toInt())
            title.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            title.maxLines = 2
            title.ellipsize = TextUtils.TruncateAt.END
            title.setTypeface(title.typeface, if (ThemeStyle.id(prefs) == ThemeStyle.DUNE) Typeface.NORMAL else Typeface.BOLD)
            val caption = FrameLayout.LayoutParams(-1, -1)
            if (ThemeStyle.id(prefs) != ThemeStyle.DUNE) {
                val separator = View(this)
                separator.setBackgroundColor(if (ThemeStyle.light(prefs)) 0x44708070 else 0x6682a2cf)
                val rule = FrameLayout.LayoutParams(dp(1), -1)
                rule.marginStart = logoWidth + dp(15)
                rule.topMargin = dp(18)
                rule.bottomMargin = dp(18)
                tile.addView(separator, rule)
            }
            caption.marginStart = logoWidth + dp(24)
            caption.marginEnd = dp(10)
            tile.addView(title, caption)
        } else if (tile !is GlassIconTile && tile !is HarmonyAppTile) tile.addView(icon, FrameLayout.LayoutParams(-1, -1))
        tile.setOnFocusChangeListener { v, focus ->
            if (tile is HarmonyAppTile) tile.appearance(focus || moving)
            else if (tile is GlassIconTile) tile.appearance(app.accent, focus || moving)
            else { tile.background = appSurface(app.accent, focus); if (!moving) tile.foreground = tileBorder(app.accent, focus) }
            if (focus) focusedPkg = app.info.activityInfo.packageName
            // Java passed null to clear the expansion; expandKey takes a non-null String,
            // and "" behaves identically (HomeAppGrid.index returns -1 for both).
            if (glassOs() && !harmony() && !moving) g.expandKey(if (focus) app.info.activityInfo.packageName else "")
            if (!moving) {
                // Focus zoom: gentle scale animation.
                val scale = if (focus) (if (glassOs()) 1f else if (tvGlass()) 1.06f else 1.05f) else 1f
                val sy = if (glassOs()) 1f else scale
                val lift = 0f
                v.animate().cancel()
                if (harmony()) {
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.translationY = 0f
                } else if (MemoryProfile.effectiveTier(this, prefs) == MemoryProfile.TIER_LIGHT) {
                    v.scaleX = scale
                    v.scaleY = sy
                    v.translationY = lift
                } else v.animate().scaleX(scale).scaleY(sy).translationY(lift).setUpdateListener { _ -> g.refreshReflections() }.setDuration(if (tvGlass()) 180 else 150).start()
                if (glassOs() && !harmony()) g.refreshReflections()
            }
            tile.translationZ = (if (focus && prefs.getBoolean("focus_glow", true)) dp(10) else 0).toFloat()
            val tileParent = tile.parent
            if (glassOs() && !harmony() && tileParent is View) tileParent.translationZ = (if (focus) dp(10) else 0).toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                tile.outlineAmbientShadowColor = app.accent
                tile.outlineSpotShadowColor = app.accent
            }
            if (focus) {
                if (glassOs()) { spotlightName = app.name; spotlightCategory = appCategory(app); updateGlassAccent(app.accent) }
                if (!TvGlassStyle.enabled(prefs)) appHeading?.text = app.name
                appHeading?.setTextColor(ThemeStyle.foreground(prefs))
                val position = IntArray(2)
                val origin = IntArray(2)
                tile.getLocationInWindow(position)
                val skyView = sky
                if (skyView != null) {
                    skyView.getLocationInWindow(origin)
                    skyView.focusX = position[0] - origin[0] + tile.width / 2f
                    skyView.focusY = position[1] - origin[1] + tile.height / 2f
                    skyView.focusRadius = tile.width.toFloat()
                    skyView.focusAccent = if (prefs.getBoolean("focus_glow", true)) app.accent else 0
                    if (!tv) {
                        // Parallax: wallpaper drifts slightly opposite to the focused tile,
                        // vertical only — never sideways. (User 2026-11-01.)
                        val cy = position[1] + tile.height / 2f
                        val size = Point()
                        windowManager.defaultDisplay.getSize(size)
                        skyView.animate().translationX(0f)
                                .translationY(if (perfAllows("parallax")) (size.y / 2f - cy) / size.y * dp(36) else 0f)
                                .setDuration(350).start()
                    }
                }
            } else sky?.focusAccent = 0
            sky?.invalidate()
        }
        tile.setOnClickListener { v ->
            val open = Runnable {
                launch(Intent(Intent.ACTION_MAIN)
                        .setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            }
            if (carriedApp != null) { open.run(); return@setOnClickListener }
            if (v is HarmonyAppTile && g.isHorizontalDock()) {
                v.launchEffect(open)
                return@setOnClickListener
            }
            v.animate().scaleX(if (glassOs()) 1f else 1.08f).scaleY(if (glassOs()) 1f else 1.08f).alpha(0.7f).setDuration(120).withEndAction {
                v.scaleX = if (glassOs()) 1f else 1.05f
                v.scaleY = if (glassOs()) 1f else 1.05f
                v.alpha = 1f
                open.run()
            }.start()
        }
        tile.setOnLongClickListener { _ ->
            ThemeDialogBuilder(this).setTitle(app.name).setItems(arrayOf("App info", "Hide from launcher", "Move app", getString(R.string.h_category))) { _, which ->
                if (which == 0) launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.activityInfo.packageName)))
                else if (which == 2) startMoveMode(app)
                else if (which == 3) HomeLibraryUi.category(this, app.info.activityInfo.packageName) { cachedApps?.let { renderApps(it) } }
                else {
                    val hidden = HashSet(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())
                    hidden.add(app.info.activityInfo.packageName)
                    prefs.edit().putStringSet("hidden_apps", hidden).apply()
                    refreshApps()
                    focusFirstApp()
                }
            }.show()
            true
        }
        val position = FrameLayout.LayoutParams(-1, -2)
        val spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)))
        val hm = if (harmony()) dp(3) else (if (tvGlass()) intArrayOf(dp(6), dp(10), dp(16)) else intArrayOf(dp(4), dp(8), dp(14)))[spacing]
        val vm = if (harmony()) dp(4) else (if (glassOs()) intArrayOf(dp(6), dp(8), dp(12)) else if (tvGlass()) intArrayOf(dp(8), dp(12), dp(18)) else intArrayOf(dp(5), dp(10), dp(16)))[spacing]
        position.width = width - hm * 2
        position.height = if (ThemeStyle.custom(prefs)) Math.max(dp(54), Math.round(position.width / DemoGeometry.ratio())) + Math.round(DemoGeometry.footer(ThemeStyle.id(prefs), dp(1).toFloat())) else Math.round(position.width * 9f / 16f)
        if (harmony()) { position.height = dp(HarmonyVisionLayout.dockTileHeightDp(resources.configuration.screenHeightDp)); position.width = -1 }
        else if (glassOs()) { position.height = referenceCardHeight(width); position.width = -1 }
        if (g.isHorizontalDock()) position.height += dp(HarmonyVisionLayout.DOCK_HEADROOM_DP)
        position.setMargins(hm, vm, hm, vm)
        tile.layoutParams = position
        tile.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                tileViews[app.info.activityInfo.packageName] = view
                if (ThemeStyle.custom(prefs) && tv) { g.removeCallbacks(demoPlacement); g.post(demoPlacement) }
            }
            override fun onViewDetachedFromWindow(view: View) {
                if (tileViews[app.info.activityInfo.packageName] === view) tileViews.remove(app.info.activityInfo.packageName)
            }
        })
        return tile
    }

    private fun searchApps() {
        val existing = searchDialog
        if (existing != null && existing.isShowing) return
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(16), dp(8), dp(16), dp(8))
        val input = EditText(this)
        input.isSingleLine = true
        input.hint = "Search installed apps"
        searchInput = input
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setTextColor(ThemeStyle.foreground(prefs))
        input.setHintTextColor(ThemeStyle.muted(prefs))
        applyAppFont(input)
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.background = toolbarSurface(false)
        form.addView(input)
        val results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        val matches = ScrollView(this)
        matches.addView(results)
        form.addView(matches, LinearLayout.LayoutParams(-1, dp(if (tv) 250 else 300)))
        val dialog = ThemeDialogBuilder(this).setTitle("Search apps").setView(form)
                .setPositiveButton("Find on Play Store") { _, _ -> openStore(input.text.toString().trim()) }
                .setNeutralButton("Voice search") { _, _ -> recognizeIntoSearch() }
                .setNegativeButton("Close", null).create()
        searchDialog = dialog
        searchRefresh = Runnable {
            results.removeAllViews()
            val query = input.text.toString().trim().lowercase(Locale.ROOT)
            val hidden = prefs.getStringSet("hidden_apps", emptySet())
            var count = 0
            val apps = cachedApps
            if (apps != null) for (app in apps) {
                if (hidden?.contains(app.info.activityInfo.packageName) == true || !(app.name + " " + app.info.activityInfo.packageName).lowercase(Locale.ROOT).contains(query)) continue
                val result = GlassSettings.row(this, app.name, null, R.drawable.ic_apps, GlassSettings.themeAccent(this)) {
                    searchDialog?.dismiss()
                    launch(Intent(Intent.ACTION_MAIN).setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                val position = LinearLayout.LayoutParams(-1, -2)
                position.setMargins(0, dp(4), 0, dp(4))
                val appIcon = app.icon
                if (result is LinearLayout && appIcon != null) {
                    val resultIcon = result.getChildAt(0) as ImageView
                    val state = appIcon.constantState
                    resultIcon.setImageDrawable(if (state == null) appIcon else state.newDrawable(resources))
                    resultIcon.clearColorFilter()
                    resultIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                }
                results.addView(result, position)
                if (++count == 16) break
            }
            if (count == 0) results.addView(text(if (cachedApps == null) "Loading apps…" else "No installed matches. Find new apps on Play Store.", 15, MUTED))
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) { searchRefresh?.run() }
            override fun afterTextChanged(value: Editable?) { }
        })
        dialog.setOnDismissListener { searchRefresh = null; searchDialog = null; searchInput = null }
        dialog.show()
        GlassSettings.styleDialog(this, dialog)
        searchRefresh?.run()
    }

    private fun launchAssistant(): Boolean {
        return tryLaunch(Intent(Intent.ACTION_ASSIST)) || tryLaunch(Intent(Intent.ACTION_VOICE_COMMAND))
                || tryLaunch(Intent(RecognizerIntent.ACTION_WEB_SEARCH))
    }

    private fun voiceSearch() { if (!launchAssistant()) recognizeIntoSearch() }

    private fun recognizeIntoSearch() {
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search launcher apps"), 9)
        } catch (unavailable: ActivityNotFoundException) {
            if (!launchAssistant()) Toast.makeText(this, "Enable your TV's Assistant or speech service in system settings", Toast.LENGTH_LONG).show()
        } catch (unavailable: SecurityException) {
            if (!launchAssistant()) Toast.makeText(this, "Enable your TV's Assistant or speech service in system settings", Toast.LENGTH_LONG).show()
        }
    }

    /** YouTube search: tries the YouTube TV app first, falls back to browser. */
    private fun youtubeSearch(query: String) {
        val encoded = Uri.encode(query)
        // Try YouTube TV app.
        val appSearch = Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube.tv")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryLaunch(appSearch)) return
        // Try regular YouTube app.
        val mobileSearch = Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryLaunch(mobileSearch)) return
        // Fall back to browser.
        launch(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + encoded))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Returns true if any installed (non-hidden) app matches the query. */
    private fun appMatches(query: String): Boolean {
        val apps = cachedApps ?: return true // Apps not loaded yet; show search.
        val q = query.trim().lowercase(Locale.ROOT)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        for (app in apps) {
            if (hidden?.contains(app.info.activityInfo.packageName) == true) continue
            if ((app.name + " " + app.info.activityInfo.packageName).lowercase(Locale.ROOT).contains(q)) return true
        }
        return false
    }

    private fun openStore(query: String) {
        if (query.isEmpty()) {
            val store = packageManager.getLaunchIntentForPackage("com.android.vending")
            if (store != null && tryLaunch(store)) return
        }
        val suffix = if (query.isEmpty()) "" else "search?q=" + Uri.encode(query) + "&c=apps"
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(if (query.isEmpty()) "market://apps" else "market://" + suffix))
                .setPackage("com.android.vending")
        if (!tryLaunch(market)) launch(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/" + if (suffix.isEmpty()) "apps" else suffix)))
    }

    private fun tryLaunch(target: Intent): Boolean {
        try {
            startActivity(target)
            return true
        } catch (unavailable: ActivityNotFoundException) { return false }
        catch (unavailable: SecurityException) { return false }
    }

    private fun launch(target: Intent) {
        if (!tryLaunch(target)) Toast.makeText(this, "This app or setting is unavailable on this device", Toast.LENGTH_LONG).show()
    }

    private fun openSetting(action: String) { launch(SystemSettings.intent(this, action)) }

    private fun showInputs() {
        val manager = getSystemService(TvInputManager::class.java)
        val sources = manager?.tvInputList ?: emptyList()
        val labels = ArrayList<String>()
        val targets = ArrayList<Intent>()
        for (source in sources) {
            if (source.isHidden(this)) continue
            labels.add(source.loadLabel(this).toString())
            targets.add(Intent(Intent.ACTION_VIEW, if (source.isPassthroughInput)
                    TvContract.buildChannelUriForPassthroughInput(source.id)
                    else TvContract.Channels.CONTENT_URI))
        }
        if (targets.isEmpty()) {
            launch(Intent("com.android.tv.action.VIEW_INPUTS"))
            return
        }
        chooseSetting("Inputs", labels.toTypedArray()) { which -> launch(targets[which]) }
    }

    private fun orderedApps(apps: List<App>): MutableList<App> {
        val byPackage = LinkedHashMap<String, App>()
        for (app in apps) byPackage[app.info.activityInfo.packageName] = app
        val saved = ArrayList<String>()
        try {
            val array = org.json.JSONArray(prefs.getString("app_order", "[]"))
            for (i in 0 until array.length()) saved.add(array.getString(i))
        } catch (malformed: org.json.JSONException) { /* Fall back to alphabetical order. */ }
        val result = ArrayList<App>()
        for (pkg in AppOrder.merge(ArrayList(byPackage.keys), saved)) result.add(byPackage[pkg]!!)
        return result
    }

    private fun rearrangeApps() {
        val apps = cachedApps ?: return
        val visible = orderedApps(apps)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { app -> hidden?.contains(app.info.activityInfo.packageName) == true }
        if (visible.isEmpty()) { Toast.makeText(this, "Unhide an app first", Toast.LENGTH_SHORT).show(); return }
        val names = Array(visible.size) { "" }
        val hints = Array(visible.size) { "" }
        for (i in visible.indices) { names[i] = visible[i].name; hints[i] = "Select to move this app" }
        GlassSettings.options(this, "Rearrange apps", names, hints) { which -> moveApp(visible[which]) }
    }

    private fun moveApp(moving: App) {
        val apps = cachedApps ?: return
        val ordered = orderedApps(apps)
        val visible = ArrayList(ordered)
        val hidden = prefs.getStringSet("hidden_apps", emptySet())
        visible.removeIf { app -> hidden?.contains(app.info.activityInfo.packageName) == true }
        val positions = Array(visible.size) { "" }
        for (i in visible.indices) positions[i] = "${i + 1} · ${visible[i].name}"
        chooseSetting("Move " + moving.name + " to position", positions) { which ->
            val packages = ArrayList<String>()
            for (app in orderedApps(cachedApps!!)) packages.add(app.info.activityInfo.packageName)
            val destination = packages.indexOf(visible[which].info.activityInfo.packageName)
            val moved = AppOrder.move(packages, moving.info.activityInfo.packageName, destination)
            prefs.edit().putString("app_order", org.json.JSONArray(moved).toString()).apply()
            refreshApps()
            Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNotifications() {
        val current = panel
        if (current != null && current.isShowing) current.dismiss()
        panel = HomeNotificationsUi.show(this)
    }

    private fun updateAmbient() { sky?.invalidate() }
    private fun updateGlassAccent(color: Int) {
        glassAccent = color
        val photo = avatar
        if (photo is AppAccentAvatar) photo.accent(color)
        sky?.invalidate()
        updateSpotlight()
    }
    private fun appCategory(app: App): String {
        val value = ApplicationInfo.getCategoryTitle(this, app.info.activityInfo.applicationInfo.category)
        return value?.toString() ?: "App"
    }
    private fun updateSpotlight() {
        val spotlight = focusedSpotlight ?: return
        val name = if (spotlightName.isEmpty()) getString(R.string.h_your_apps) else spotlightName
        val caption = "●  " + name + if (spotlightCategory.isEmpty()) "" else "  • " + spotlightCategory
        val text = SpannableString(caption)
        text.setSpan(ForegroundColorSpan(glassAccent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val secondary = caption.indexOf("  • ")
        if (secondary >= 0) text.setSpan(ForegroundColorSpan(MUTED), secondary, caption.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spotlight.text = text
    }
    private fun updateBluetooth() {
        val status = bluetoothStatus
        val bt = bluetooth
        if (status != null && bt != null) status.text = bt.connectedName()
    }
    fun libraryVisibility(pkg: String, visible: Boolean): Boolean {
        val hidden = HashSet(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())
        if (visible) hidden.remove(pkg) else hidden.add(pkg)
        prefs.edit().putStringSet("hidden_apps", hidden).apply()
        return true
    }
    private fun showAppLibrary() {
        appLibrary?.close()
        appLibrary = AppLibraryDialog(this, false, true, false, true, { pkg -> prefs.getStringSet("hidden_apps", emptySet())?.contains(pkg) != true }, this::refreshApps, {}, emptySet())
        appLibrary?.show()
    }
    private fun showSettings() {
        val current = panel
        if (current != null && current.isShowing) { current.dismiss(); return }
        LauncherPin.require(this, this::showSettingsUnlocked)
    }

    private fun showSettingsUnlocked() {
        panel = HomeDashboard.show(this, networkStatus?.text.toString() ?: "", { gridState = null; buildHome() }, { action ->
            when (action) {
                HomeDashboard.SETTINGS -> launcherSettings()
                HomeDashboard.SEARCH -> searchApps()
                HomeDashboard.STORE -> openStore("")
                HomeDashboard.SCREENSAVER -> launch(Intent(this, LandscapeActivity::class.java))
                HomeDashboard.CATEGORIES -> chooseCategoryFilter()
                HomeDashboard.WIFI -> openSetting(Settings.ACTION_WIFI_SETTINGS)
                HomeDashboard.BLUETOOTH -> SystemSettings.openBluetoothAccessories(this)
                HomeDashboard.INPUTS -> showInputs()
                HomeDashboard.DISPLAY -> panel = TvControlPreview.show(this, true)
                HomeDashboard.SOUND -> panel = TvControlPreview.show(this, false)
                HomeDashboard.IDENTITY -> editProfile()
                else -> openSetting(Settings.ACTION_SETTINGS)
            }
        })
    }

    private fun addQuickCard(strip: LinearLayout, title: String, icon: Int, tint: Int, action: Runnable) {
        val card = GlassSettings.wideCard(this, title, icon, tint) { panel?.dismiss(); action.run() }
        val params = LinearLayout.LayoutParams(0, -2, 1f)
        val margin = dp(4)
        params.setMargins(margin, 0, margin, 0)
        strip.addView(card, params)
    }

    private fun addPanelRow(rows: LinearLayout, label: String, hint: String, icon: Int, color: Int, action: Runnable) {
        rows.addView(GlassSettings.row(this, label, hint, icon, color) { if (label != "Launcher settings") panel?.dismiss(); action.run() })
    }

    /** Custom accent color picker (8 colors). Applies the Custom theme. */
    private fun chooseAccentColor() {
        val names = arrayOf("Cyan", "Orange", "Purple", "Green", "Red", "Yellow", "Pink", "White", "Black", "Graphite", "Silver", "Blue", "Teal", "Gold", "Rose", "Indigo")
        val colors = intArrayOf(0xff40d9ff.toInt(), 0xffff9a5c.toInt(), 0xffb388ff.toInt(), 0xff69f0ae.toInt(), 0xffff5252.toInt(), 0xffffea00.toInt(), 0xffff80ab.toInt(), 0xffffffff.toInt(),
                0xff000000.toInt(), 0xff343b46.toInt(), 0xffcbd3df.toInt(), 0xff508dff.toInt(), 0xff44c6bc.toInt(), 0xffe8c66a.toInt(), 0xffd890a5.toInt(), 0xff7986ed.toInt())
        chooseSetting("Custom accent color", names) { which ->
            prefs.edit().putInt("theme_name", 3).putInt("custom_accent", colors[which]).apply()
            applyAppearance()
            refreshApps()
        }
    }

    private fun launcherSettings() {
        val cats = arrayOf("Tiles", "Clock", "Theme", "Wallpaper", "Weather", "Apps", "System")
        val catHints = arrayOf("App cards layout", "Time display", "Colors & appearance", "Background", "Widget, city and forecast", "Your apps", "Launcher system")
        val catIcons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_clock, R.drawable.ic_palette,
                R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_apps, R.drawable.ic_settings)
        GlassSettings.options(this, "Launcher settings", cats, catHints, catIcons, { which ->
            when (which) {
                0 -> tileSettings()
                1 -> clockSettings()
                2 -> themeSettings()
                3 -> wallpaperMenu()
                4 -> weatherMenu()
                5 -> appMenu()
                else -> systemMenu()
            }
        }, { which -> showsSetting(cats[which]) })
    }

    /** Subtle background behind the header (toggle in settings). */
    /**
     * Applies the performance profile. In Light mode, disables expensive effects:
     * crossfade, parallax, entrance cascade, clock seconds, auto-scroll, and shadows.
     */
    private fun applyPerformanceProfile() {
        val tier = MemoryProfile.effectiveTier(this, prefs)
        if (tier == MemoryProfile.TIER_LIGHT) {
            // Light mode defaults: static, minimal.
            // Note: we don't force-overwrite user prefs here; the drawing code
            // checks MemoryProfile.allow*() at draw time. This just ensures
            // the crossfade buffer isn't allocated.
            sky?.releaseTransient()
        }
    }

    /** Returns true if the named effect is allowed in the current performance tier. */
    private fun perfAllows(effect: String): Boolean {
        val tier = MemoryProfile.effectiveTier(this, prefs)
        return when (effect) {
            "crossfade" -> MemoryProfile.allowCrossfade(tier)
            "parallax" -> MemoryProfile.allowParallax(tier)
            "autoscroll" -> MemoryProfile.allowAutoScroll(tier)
            else -> true
        }
    }

    /** Off, glass or frosted: independently saved for each visual style. */
    private fun headerBgMode(): Int = HeaderAppearance.mode(prefs)

    fun liquidPanel(target: View, radius: Int) { liquidPanel(target, radius, false) }
    fun liquidDrawerPanel(target: View, radius: Int) { liquidPanel(target, radius, true) }
    private fun liquidPanel(target: View, radius: Int, drawer: Boolean) {
        if (drawer) { target.background = null; target.clipToOutline = false; return }
        if (!TvGlassStyle.enabled(prefs)) return
        target.background = if (drawer) TvGlassStyle.drawer(dp(radius), dp(1), prefs)
                else TvGlassStyle.liquid(dp(radius), dp(1), prefs)
        target.clipToOutline = true
        liquidBackdrop?.bind(target)
    }

    private fun applyHeaderBackground() {
        val h = header ?: return
        styleStatusBar(h)
    }
    private fun styleStatusBar(bar: View) {
        val mode = headerBgMode()
        val background: GradientDrawable? = if (mode == 0) null else if (glassOs()) GlassOsUi.panel(this, 26) else TvGlassStyle.header(dp(if (tvGlass()) 28 else 18), dp(1), prefs, mode == 2)
        bar.background = background
        bar.clipToOutline = background != null && !ThemeStyle.custom(prefs)
        if (bar === header) bar.setPadding(if (background == null) 0 else dp(12), if (background == null) 0 else dp(8),
                if (background == null) 0 else dp(12), if (background == null) 0 else dp(8))
        else bar.setPadding(0, if (background == null) 0 else dp(5), 0, if (background == null) 0 else dp(5))
    }

    /** Applies the launcher-wide font to a TextView. */
    private fun applyAppFont(view: TextView) {
        view.typeface = Typeface.create(
                ThemeStyle.fontFamily(prefs), Typeface.NORMAL)
    }

    /** Submenu helper with per-row icons. */
    private fun subMenu(title: String, labels: Array<String>, hints: Array<String>, icons: IntArray, choices: Array<Runnable>) {
        GlassSettings.options(this, title, labels, hints, icons, { which -> choices[which].run() },
                { which -> showsSetting(labels[which]) })
    }

    private fun showsSetting(label: String): Boolean {
        return when (label) {
            "Tiles" -> !harmony()
            "App columns", "Tile size", "Tile spacing", "Tile corners", "Logo size", "App label size" -> !harmony()
            "Focus border" -> ThemeStyle.id(prefs) == ThemeStyle.CLASSIC
            "Wallpaper focus glow" -> !gradientWallpaper()
            "Launcher font" -> !ThemeStyle.fixedTypography(prefs)
            "Clock style", "Clock shadow", "Hijri day adjustment" -> clockVisible()
            "Clock size", "Time format", "Clock seconds" -> digitalClockVisible()
            "Clock font" -> digitalClockVisible() && !ThemeStyle.fixedTypography(prefs)
            "Clock alignment" -> digitalClockVisible() && !ThemeStyle.fixedTypography(prefs) && !weatherWidgetEnabled()
            "Clock glow" -> clockVisible() && !gradientWallpaper()
            "Glass edge" -> ThemeStyle.id(prefs) != ThemeStyle.CLASSIC || headerBgMode() != 0
            "Glass finish" -> ThemeStyle.id(prefs) != ThemeStyle.CLASSIC || headerBgMode() == 1
                        || (PanelTransparency.enabled(prefs) && GlassSettings.frostedAvailable(this))
            "Wallpaper darkness", "Bottom shade" -> "solid" != prefs.getString("wallpaper_mode", if (glassOs()) "solid" else "mountains")
            else -> true
        }
    }

    private fun clockVisible(): Boolean {
        return !prefs.getBoolean("hide_clock", false) && !prefs.getBoolean("apps_only", false)
    }

    private fun digitalClockVisible(): Boolean {
        return clockVisible() && prefs.getInt("clock_style", 0) != 1
    }

    private fun gradientWallpaper(): Boolean {
        return glassOs() && "solid" == prefs.getString("wallpaper_mode", "solid")
    }

    private fun appTitleSizeLabel(): String {
        return if (ThemeStyle.id(prefs) == ThemeStyle.CLASSIC) "Focused app title size" else "App label size"
    }

    private fun appLabelSizes(): IntArray {
        return if (glassOs()) intArrayOf(14, 16, 20, 22) else intArrayOf(14, 17, 20, 24)
    }

    private fun tileSettings() {
        val labels = arrayOf("App columns", "Tile size", "Tile spacing", "Tile corners", "Logo size", appTitleSizeLabel(), "Focus border")
        val hints = arrayOf("Number of cards per row", "Compact or large cards", "Gap between cards", "Sharp, rounded or round", "App logo size in cards", if (ThemeStyle.id(prefs) == ThemeStyle.CLASSIC) "Name above the app grid" else "App name on the tile", "Thin, normal or thick")
        val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_settings, R.drawable.ic_apps)
        val choices = arrayOf<Runnable>(
            Runnable { chooseSetting("App columns", arrayOf("Automatic", "2", "3", "4", "5", "6")) { which ->
                prefs.edit().putInt(if (tv) "tv_columns" else "phone_columns", if (which == 0) 0 else which + 1).apply()
                refreshApps()
            } },
            Runnable { chooseSetting("Tile size", arrayOf("Compact", "Large")) { which ->
                prefs.edit().putBoolean("large_tiles", which == 1).putInt(if (tv) "tv_columns" else "phone_columns", 0).apply()
                refreshApps()
            } },
            Runnable { chooseSetting("Tile spacing", arrayOf("Compact", "Normal", "Wide")) { which ->
                prefs.edit().putInt("tile_spacing", which).apply()
                refreshApps()
            } },
            Runnable { chooseSetting("Tile corners", arrayOf("Sharp", "Rounded", "Round")) { which ->
                prefs.edit().putInt("tile_corners", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Logo size", arrayOf("Small", "Normal", "Large")) { which ->
                prefs.edit().putInt("logo_size", which).apply()
                appWorker.execute { IconCache(this).clear() }
                cachedApps = null
                loadApps()
            } },
            Runnable { chooseSetting(appTitleSizeLabel(), arrayOf("Small", "Normal", "Large", "Extra large")) { which ->
                if (ThemeStyle.id(prefs) != ThemeStyle.CLASSIC) { prefs.edit().putInt("app_label_size", appLabelSizes()[which]).apply(); refreshApps() }
                else { val size = intArrayOf(24, 32, 40, 48)[which]; prefs.edit().putInt("focus_title_size", size).apply(); appHeading?.setTextSize(size.toFloat()) }
            } },
            Runnable { chooseSetting("Focus border", arrayOf("Thin", "Normal", "Thick")) { which ->
                prefs.edit().putInt("focus_border", which).apply()
                refreshApps()
            } }
        )
        subMenu("Tiles", labels, hints, icons, choices)
    }

    private fun clockSettings() {
        if (harmony() && tv) { harmonyClockSettings(); return }
        val labels = arrayOf("Clock size", "Clock style", "Clock font", "Time format", "Clock alignment", "Clock seconds", "Clock glow", "Clock shadow", "Hide clock", "Hide greeting", "Hijri day adjustment")
        val hints = arrayOf("Small, normal or large", "Digital or analog", "Bold, serif or mono", "12-hour or 24-hour", "Left, center or right", "Show seconds", "Halo behind the clock", "Shadow for readability", "Hide the clock", "Hide the greeting", "Adjust for local moon sighting")
        val icons = intArrayOf(R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock)
        val choices = arrayOf<Runnable>(
            Runnable { chooseSetting("Clock size", arrayOf("Small", "Normal", "Large")) { which ->
                prefs.edit().putInt("clock_size", (if (ThemeStyle.custom(prefs)) DemoGeometry.clockSizes(tv) else if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52))[which]).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Clock style", arrayOf("Digital", "Analog")) { which ->
                prefs.edit().putInt("clock_style", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Clock font", arrayOf("Bold", "Serif", "Mono")) { which ->
                prefs.edit().putInt("clock_font", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Time format", arrayOf("12-hour", "24-hour")) { which ->
                prefs.edit().putBoolean("time_24h", which == 1).apply()
                updateClock()
            } },
            Runnable { chooseSetting("Clock alignment", arrayOf("Left", "Center", "Right")) { which ->
                prefs.edit().putInt("clock_align", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Clock seconds", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("clock_seconds", which == 1).apply()
                handler.removeCallbacks(secondTick)
                if (which == 1 && resumed) secondTick.run()
                else updateClock()
            } },
            Runnable { chooseSetting("Clock glow", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("clock_glow", which == 1).apply()
                sky?.invalidate()
            } },
            Runnable { chooseSetting("Clock shadow", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("clock_shadow", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Hide clock", arrayOf("Show", "Hide")) { which ->
                prefs.edit().putBoolean("hide_clock", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Hide greeting", arrayOf("Show", "Hide")) { which ->
                prefs.edit().putBoolean("hide_greeting", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Hijri day adjustment", arrayOf("-2 days", "-1 day", "Calculated date", "+1 day", "+2 days")) { which -> prefs.edit().putInt("hijri_adjustment", which - 2).apply(); updateClock() } }
        )
        subMenu("Clock", labels, hints, icons, choices)
    }

    private fun switchVisualStyle(style: Int) {
        if (ThemeStyle.id(prefs) == style) return
        val priorLogo = prefs.getInt("logo_size", 1)
        val priorMark = ThemeStyle.id(prefs) != ThemeStyle.CLASSIC
        val wasHarmony = harmony()
        appGeneration++ // Reject artwork started for the previous style, even if its palette was similar.
        prefs.edit().putInt("visual_style", style).apply()
        ThemeStyle.demoDefaults(prefs, tv)
        val reload = wasHarmony != harmony() || priorLogo != prefs.getInt("logo_size", 1) || priorMark != (ThemeStyle.id(prefs) != ThemeStyle.CLASSIC)
        val apps = cachedApps
        if (apps != null) for (app in apps) {
            if (reload) { app.iconReady = false; app.icon = null }
        }
        if (ThemeStyle.custom(prefs) && !prefs.getBoolean("material_defaults_v1", false)) {
            prefs.edit().putBoolean("material_defaults_v1", true).putInt("tile_corners", if (style == ThemeStyle.DUNE || style == ThemeStyle.AMBER) 0 else 1)
                    .putInt("phone_columns", 2).putInt("tv_columns", 4)
                    .putString("theme_mode", if (ThemeStyle.light(prefs)) "light" else "dark")
                    .putBoolean("clock_shadow", true).apply()
        }
        GlassSettings.dismiss(this)
        buildHomeSoon()
        handler.removeCallbacks(themeMenuRefresh)
        handler.postDelayed(themeMenuRefresh, 300)
    }

    private val themeMenuRefresh: Runnable = Runnable {
        if (resumed && !isFinishing && !isDestroyed && !GlassSettings.showing(this)) themeSettings()
    }

    private fun themeSettings() {
        // "Dark mode" removed 2026-11-01 (user): the theme glass color covers it.
        val labels = arrayOf("Visual style", "Accent preset", "Custom accent color", "Launcher font", "Glass intensity", "Glass finish", "Glass edge", "Panel transparency", "Header background", "Reset current theme")
        val hints = arrayOf("Switch theme", "Cyan, Orange, Purple or Custom", "Custom theme color", "Font for the launcher", "Transparency of panels", "Soft, Clear or Frosted panels", "Reflection around glass surfaces", "Solid panels when off; app drawer stays transparent", "Status-bar glass in this theme", "Restore defaults for the active style")
        val icons = intArrayOf(R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, android.R.drawable.ic_popup_sync)
        val choices = arrayOf<Runnable>(
            Runnable { chooseSetting("Visual style", arrayOf("Sadique Glass OS", "Classic", "Dune Loom", "Pearl Current", "Cobalt Rail", "Sage Fold", "Pine Mist", "Amber Night", "Harmony Vision"), this::switchVisualStyle) },
            Runnable { chooseSetting("Accent preset", arrayOf(if (ThemeStyle.custom(prefs)) "Theme default" else if (harmony()) "Harmony Blue" else "Midnight Cyan", "Sunset Orange", "Deep Purple", "Custom")) { which ->
                prefs.edit().putInt("theme_name", which).apply()
                if (which == 3) chooseAccentColor()
                else { applyAppearance(); refreshApps() }
            } },
            Runnable { chooseAccentColor() },
            Runnable { chooseSetting("Launcher font", arrayOf("Default", "Serif", "Mono", "Light")) { which ->
                prefs.edit().putInt("app_font", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Glass intensity", arrayOf("Airy", "Normal", "Solid")) { which ->
                prefs.edit().putInt("glass_alpha", intArrayOf(30, 70, 100)[which]).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Glass finish", arrayOf("Soft", "Clear", "Frosted")) { which ->
                prefs.edit().putInt("glass_finish", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Glass edge", arrayOf("Off", "Subtle", "Bright")) { which ->
                prefs.edit().putInt("glass_edge", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Panel transparency", arrayOf("On", "Off")) { which ->
                prefs.edit().putBoolean("panel_transparency", which == 0).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Header background", if (glassOs()) arrayOf("Off", "Glass") else arrayOf("Off", "Glass", "Frosted")) { which ->
                prefs.edit().putInt("header_bg_mode", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseSetting("Reset theme", arrayOf("Keep current settings", "Reset to original defaults")) { which ->
                if (which == 1) {
                    resetCurrentTheme()
                }
            } }
        )
        subMenu("Theme", labels, hints, icons, choices)
    }

    private fun resetCurrentTheme() {
        val currentStyle = ThemeStyle.id(prefs)
        val prefix = ThemeStyle.prefix(currentStyle)
        val raw = getSharedPreferences("personal_home", 0)
        val editor = raw.edit()
        for (key in raw.all.keys) {
            if (key.startsWith(prefix)) {
                editor.remove(key)
            }
        }
        editor.apply()
        ThemeStyle.demoDefaults(prefs, tv)
        appGeneration++
        val apps = cachedApps
        if (apps != null) {
            for (app in apps) {
                app.iconReady = false
                app.icon = null
            }
        }
        applyAppearance()
        buildHomeSoon()
        Toast.makeText(this, "Theme settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun wallpaperMenu() {
        // Removed 2026-11-01 (user): "Save as favorite", "Day / night wallpaper",
        // "Smooth day/night", and the built-in Midnight mountains / Solid midnight options.
        val labels = arrayOf("Wallpaper", "Wallpaper source", "Wallpaper darkness", "Bottom shade", "Wallpaper focus glow")
        val hints = arrayOf("Your own picture", "Choose the home background", "Keep the landscape visible", "Dark gradient behind tiles", "Glow behind the focused app")
        val icons = intArrayOf(R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness)
        val choices = arrayOf<Runnable>(
            Runnable(::wallpaperSettings),
            Runnable(::chooseWallpaperSource),
            Runnable { chooseSetting("Wallpaper darkness", arrayOf("Light", "Medium", "Dark")) { which ->
                prefs.edit().putInt("background_dim", which * 20).apply()
                sky?.invalidate()
            } },
            Runnable { chooseSetting("Bottom shade", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("bottom_shade", which == 1).apply()
                sky?.invalidate()
            } },
            Runnable { chooseSetting("Wallpaper focus glow", arrayOf("On", "Off")) { which ->
                prefs.edit().putBoolean("focus_glow", which == 0).apply()
                sky?.focusAccent = 0
                sky?.invalidate()
            } }
        )
        subMenu("Wallpaper", labels, hints, icons, choices)
    }

    private fun hasFavoriteWallpaper(): Boolean {
        return profileAsset("favorite_wallpaper.jpg").isFile
    }

    private fun chooseWallpaperSource() {
        val sources = arrayListOf("Built-in", "Bing daily")
        if (hasFavoriteWallpaper()) sources.add("Favorite")
        if (glassOs()) sources.add("Gradient only")
        chooseSetting("Wallpaper source", sources.toTypedArray()) { which ->
            val gradient = "Gradient only" == sources[which]
            prefs.edit().putInt("wallpaper_source", if (gradient) 0 else which)
                    .putString("wallpaper_mode", if (gradient) "solid" else "mountains").apply()
            buildHomeSoon()
        }
    }

    private fun appMenu() {
        if (harmony()) {
            subMenu("Apps", arrayOf("Hide / unhide apps", "Rearrange apps"),
                    arrayOf("Choose which apps appear", "Choose the order of app cards"),
                    intArrayOf(R.drawable.ic_apps, R.drawable.ic_swap_vert),
                    arrayOf<Runnable>(Runnable(::hiddenApps), Runnable(::rearrangeApps)))
            return
        }
        val labels = arrayOf("Hide / unhide apps", "Rearrange apps", "Continue watching")
        val hints = arrayOf("Choose which apps appear", "Choose the order of app cards", "Resume titles shared by your TV apps")
        val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_swap_vert, R.drawable.ic_livetv)
        val choices = arrayOf<Runnable>(
            Runnable(::hiddenApps),
            Runnable(::rearrangeApps),
            Runnable { chooseSetting("Continue watching", arrayOf("Show below apps", "Hide row", "Allow TV Watch Next access")) { which ->
                if (which == 2) continueWatching?.requestAccess()
                else {
                    val settings = prefs.edit().putBoolean("continue_watching", which == 0)
                    if (which == 0) settings.putBoolean("apps_only", false)
                    settings.apply()
                    buildHomeSoon()
                }
            } }
        )
        subMenu("Apps", labels, hints, icons, choices)
    }

    private fun systemMenu() {
        val labels = arrayOf("Screensaver & weather", "Profile name & photo", "Performance", "Updates", "Export settings", "Import settings", "Reset layout & unhide apps", getString(R.string.compose_apps_menu))
        val hints = arrayOf("Photos, weather & idle timer", "Name and local profile picture", "Light, Balanced or Full", "Automatic GitHub updates", "Save to a file", "Restore from a file", "Restore the default layout", getString(R.string.compose_apps_hint))
        val icons = intArrayOf(R.drawable.ic_picture, R.drawable.ic_person, R.drawable.ic_settings, R.drawable.ic_internet, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_apps)
        val choices = arrayOf<Runnable>(
            Runnable(::screensaverSettings),
            Runnable(::editProfile),
            Runnable { chooseSetting("Performance", arrayOf("Auto", "Light", "Balanced", "Full")) { which ->
                prefs.edit().putInt("perf_mode", which).apply()
                applyPerformanceProfile()
                buildHomeSoon()
            } },
            Runnable { LauncherUpdatesUi.show(this) },
            Runnable(::exportSettings),
            Runnable(::importSettings),
            Runnable { ThemeDialogBuilder(this).setTitle("Reset layout and show all apps?").setPositiveButton("Reset") { _, _ ->
                prefs.edit().remove("app_order").remove("hidden_apps").remove("tv_columns").remove("phone_columns").remove("large_tiles")
                        .remove("focus_title_size").remove("clock_size").remove("background_dim").remove("theme_mode")
                        .remove("wallpaper_mode").remove("focus_glow").remove("continue_watching")
                        .remove("header_bg_mode").remove("header_bg").remove("header_blur").apply()
                buildHomeSoon()
            }.setNegativeButton(android.R.string.cancel, null).show() },
            Runnable { startActivity(Intent(this, ComposeAppsActivity::class.java)) }
        )
        subMenu("System", labels, hints, icons, choices)
    }

    private fun wallpaperSettings() {
        // Built-in Midnight mountains / Solid midnight removed 2026-11-01 (user).
        chooseSetting("Home wallpaper", arrayOf("Choose picture", "Saved custom picture")) { which ->
            if (which == 0) {
                try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 8) }
                catch (unavailable: ActivityNotFoundException) { Toast.makeText(this, "No image picker available on this device", Toast.LENGTH_LONG).show() }
                catch (unavailable: SecurityException) { Toast.makeText(this, "No image picker available on this device", Toast.LENGTH_LONG).show() }
            } else {
                if (!profileAsset("personal-wallpaper.jpg").exists()) { Toast.makeText(this, "Choose a picture first", Toast.LENGTH_SHORT).show(); return@chooseSetting }
                prefs.edit().putString("wallpaper_mode", "custom").putInt("wallpaper_source", 0).apply()
                loadWallpaper()
            }
        }
    }

    private fun loadWallpaper() {
        if ("custom" != prefs.getString("wallpaper_mode", "mountains")) return
        val epoch = profileEpoch
        val file = profileAsset("personal-wallpaper.jpg")
        worker.execute {
            val image = ProfileAssets.load(this, file, false)
            handler.post { if (!isDestroyed && epoch == profileEpoch) { customWallpaper = image; sky?.invalidate(); adaptTextColors() } }
        }
    }

    /** Fixed label style for all wallpaper-backed text (user 2026-11-01). */
    private fun adaptVisibleTextColors() {
        // Fixed style for every label (user 2026-11-01): white + black outline + drop shadow.
        applyLabelScheme(userName, false, false)
        applyLabelScheme(greeting, false, true)
        applyLabelScheme(homeWeather, false, true)
        applyLabelScheme(networkStatus, false, true)
        applyLabelScheme(time, false, false)
        applyLabelScheme(ampm, false, false)
        applyLabelScheme(date, false, true)
        applyLabelScheme(islamicDate, false, true)
        applyLabelScheme(appHeading, false, false)
    }

    /** Fixed label style (2026-11-01): white text with a black outline halo and drop shadow,
     *  readable on any wallpaper. Replaces the old per-label adaptive colors. */
    private fun applyLabelScheme(label: TextView?, dark: Boolean, muted: Boolean) {
        if (label == null) return
        if (glassOs() && label === networkStatus) return
        if (glassOs() && label === ampm) { label.setTextColor(Color.WHITE); return }
        label.setTextColor(if (ThemeStyle.custom(prefs)) (if (muted) ThemeStyle.muted(prefs) else ThemeStyle.foreground(prefs)) else (if (muted) 0xE6FFFFFF.toInt() else 0xFFFFFFFF.toInt()))
        val clockLabel = label === time || label === ampm || label === date || label === islamicDate
        val headerLabel = label === userName || label === greeting || label === homeWeather || label === networkStatus
        if (headerLabel) label.setTextColor(HeaderAppearance.foreground(prefs))
        if (glassOs() && label === networkStatus) return
        if (clockLabel) label.setTextColor(wallpaperClockInk)
        if (clockLabel && !prefs.getBoolean("clock_shadow", true)) label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        else {
            val darkInk = if (clockLabel) wallpaperClockInk != 0xffffffff.toInt() else headerLabel && ThemeStyle.light(prefs) && headerBgMode() != 0
            label.setShadowLayer(dp(if (clockLabel) 5 else 2).toFloat(), 0f, dp(if (clockLabel) 2 else 1).toFloat(), if (darkInk) 0x99ffffff.toInt() else 0xe6000000.toInt())
        }
    }

    /** Nine cached photo samples choose clock ink; the photo itself is never tinted for text contrast. */
    private fun updateWallpaperClockInk(): Boolean {
        val skyView = sky
        if (skyView == null) { contrastKey = null; return false }
        val photo = skyView.currentPicture()
        if (photo == null || photo.isRecycled) {
            contrastKey = null
            val changed = wallpaperClockInk != 0xffffffff.toInt()
            wallpaperClockInk = 0xffffffff.toInt()
            return changed
        }
        if (skyView.width <= 0 || skyView.height <= 0) return false
        val y = if (skyView.clockY >= 0) skyView.clockY else dp(150).toFloat()
        val x = if (skyView.clockX >= 0) skyView.clockX else skyView.width / 2f
        val frame = skyView.wallpaperFrame(photo, y, photo === skyView.backdrop)
        if (tv && (x < frame[0] || x >= frame[2] || y < frame[1] || y >= frame[3])) {
            contrastKey = null
            val changed = wallpaperClockInk != Color.WHITE
            wallpaperClockInk = Color.WHITE
            return changed
        }
        val px = Math.max(0, Math.min(photo.width - 1, Math.round((x - frame[0]) / (frame[2] - frame[0]) * photo.width)))
        val py = Math.max(0, Math.min(photo.height - 1, Math.round((y - frame[1]) / (frame[3] - frame[1]) * photo.height)))
        val shade = HomeAppearance.shade(prefs.getString("theme_mode", "light"), skyView.minute, prefs.getInt("background_dim", 0))
        val darkInk = 0xff11191c.toInt()
        val key = contrastKey
        if (key != null && key.matches(photo, px, py, shade, darkInk)) return false
        contrastKey = ClockSampleKey(photo, px, py, shade, darkInk)
        var red = 0
        var green = 0
        var blue = 0
        val dx = Math.max(1, photo.width / 30)
        val dy = Math.max(1, photo.height / 30)
        for (row in -1..1) for (col in -1..1) {
            val pixel = photo.getPixel(Math.max(0, Math.min(photo.width - 1, px + col * dx)), Math.max(0, Math.min(photo.height - 1, py + row * dy)))
            red += (pixel shr 16) and 255
            green += (pixel shr 8) and 255
            blue += pixel and 255
        }
        val background = MaterialAppearance.mix(Color.rgb(red / 9, green / 9, blue / 9), 0xff051327.toInt(), shade / 255f)
        // A white cloud or bright patch must not turn an otherwise colourful photo's clock black.
        var whiteWallpaper = true
        for (row in 0 until 3) {
            if (!whiteWallpaper) break
            for (col in 0 until 3) {
                val pixel = photo.getPixel(Math.min(photo.width - 1, Math.round(photo.width * (0.15f + 0.35f * col))),
                        Math.min(photo.height - 1, Math.round(photo.height * (0.15f + 0.35f * row))))
                if (ThemeStyle.wallpaperClockInk(pixel, darkInk) == 0xffffffff.toInt()) { whiteWallpaper = false; break }
            }
        }
        val ink = if (whiteWallpaper) ThemeStyle.wallpaperClockInk(background, darkInk) else 0xffffffff.toInt()
        val changed = ink != wallpaperClockInk
        wallpaperClockInk = ink
        return changed
    }

    private fun adaptTextColors() {
        liquidBackdrop?.refresh()
        headerBackdrop?.refresh()
        updateWallpaperClockInk()
        adaptVisibleTextColors()
    }

    /** Applies the current light/dark text scheme to every wallpaper-backed label. */
    private fun chooseSetting(title: String, options: Array<String>, selected: IntConsumer) {
        GlassSettings.choices(this, title, options, { settingCurrentIndex(title) }, { which ->
            selected.accept(which)
            GlassSettings.refresh(this)
        })
    }

    /** Index of the currently applied option for a settings chooser (-1 when none applies). (User 2026-11-01.) */
    private fun settingCurrentIndex(title: String): Int {
        return when (title) {
            "Custom accent color" -> if (prefs.getInt("theme_name", 0) != 3) -1 else indexOf(intArrayOf(0xff40d9ff.toInt(), 0xffff9a5c.toInt(), 0xffb388ff.toInt(), 0xff69f0ae.toInt(), 0xffff5252.toInt(), 0xffffea00.toInt(), 0xffff80ab.toInt(), 0xffffffff.toInt(),
                    0xff000000.toInt(), 0xff343b46.toInt(), 0xffcbd3df.toInt(), 0xff508dff.toInt(), 0xff44c6bc.toInt(), 0xffe8c66a.toInt(), 0xffd890a5.toInt(), 0xff7986ed.toInt()), prefs.getInt("custom_accent", 0))
            "Panel transparency" -> if (PanelTransparency.enabled(prefs)) 0 else 1
            "Glass finish" -> TvGlassStyle.finish(prefs)
            "Glass edge" -> Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)))
            "Visual style" -> ThemeStyle.id(prefs)
            "Wallpaper darkness" -> Math.min(2, prefs.getInt("background_dim", 0) / 20)
            "Wallpaper focus glow" -> if (prefs.getBoolean("focus_glow", true)) 0 else 1
            "Header background" -> if (glassOs()) Math.min(1, headerBgMode()) else headerBgMode()
            "App columns" -> {
                val v = prefs.getInt(if (tv) "tv_columns" else "phone_columns", 0)
                if (v == 0) 0 else v - 1
            }
            "Tile size" -> if (prefs.getBoolean("large_tiles", false)) 1 else 0
            "Tile spacing" -> prefs.getInt("tile_spacing", 1)
            "Tile corners" -> prefs.getInt("tile_corners", 1)
            "Logo size" -> Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))
            "App label size" -> indexOf(appLabelSizes(), prefs.getInt("app_label_size", if (glassOs()) 16 else if (tv) 17 else 14))
            "Focused app title size" -> indexOf(intArrayOf(24, 32, 40, 48), prefs.getInt("focus_title_size", if (tv) 32 else 26))
            "Focus border" -> prefs.getInt("focus_border", 1)
            "Clock size" -> indexOf(if (ThemeStyle.custom(prefs)) DemoGeometry.clockSizes(tv) else if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52), prefs.getInt("clock_size", if (tv) 52 else 44))
            "Clock style" -> prefs.getInt("clock_style", 0)
            "Watch face" -> prefs.getInt("harmony_clock_face", 0)
            "Metal finish" -> prefs.getInt("harmony_clock_finish", 0)
            "Clock accent" -> prefs.getInt("harmony_clock_accent", 0)
            "Second hand" -> if (prefs.getBoolean("harmony_clock_sweep", true)) 0 else 1
            "Harmony clock size" -> prefs.getInt("harmony_clock_size", 1)
            "Daily thoughts" -> if (prefs.getBoolean("harmony_daily_quote", true)) 0 else 1
            "Clock font" -> prefs.getInt("clock_font", 0)
            "Home weather widget" -> if (prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) 0 else 1
            "Widget animation" -> if (prefs.getBoolean("weather_widget_animation", true)) 0 else 1
            "Hijri day adjustment" -> Math.max(0, Math.min(4, prefs.getInt("hijri_adjustment", 0) + 2))
            "Time format" -> if (prefs.getBoolean("time_24h", false)) 1 else 0
            "Clock alignment" -> prefs.getInt("clock_align", 1)
            "Clock seconds" -> if (prefs.getBoolean("clock_seconds", false)) 1 else 0
            "Clock glow" -> if (prefs.getBoolean("clock_glow", false)) 1 else 0
            "Clock shadow" -> if (prefs.getBoolean("clock_shadow", true)) 1 else 0
            "Hide clock" -> if (prefs.getBoolean("hide_clock", false)) 1 else 0
            "Hide greeting" -> if (prefs.getBoolean("hide_greeting", false)) 1 else 0
            "Accent preset" -> prefs.getInt("theme_name", 0)
            "Launcher font" -> prefs.getInt("app_font", 0)
            "Glass intensity" -> indexOf(intArrayOf(30, 70, 100), prefs.getInt("glass_alpha", 70))
            "Wallpaper source" -> {
                val favorite = hasFavoriteWallpaper()
                if (gradientWallpaper()) if (favorite) 3 else 2
                else if ("custom" == prefs.getString("wallpaper_mode", "mountains")) -1
                else {
                    val source = prefs.getInt("wallpaper_source", 0)
                    if (source == 2 && !favorite) 0 else source
                }
            }
            "Bottom shade" -> if (prefs.getBoolean("bottom_shade", true)) 1 else 0
            "Continue watching" -> if (prefs.getBoolean("continue_watching", true)) 0 else 1
            "Avatar shape" -> prefs.getInt("avatar_shape", 0)
            "Performance" -> prefs.getInt("perf_mode", 0)
            "Home wallpaper" -> if ("custom" == prefs.getString("wallpaper_mode", "mountains")) 1 else -1
            "Temperature unit" -> if (prefs.getBoolean("weather_fahrenheit", false)) 1 else 0
            "Start screensaver after idle" -> indexOf(intArrayOf(0, 2, 5, 10, 15), prefs.getInt("idle_screensaver_minutes", 0))
            else -> -1
        }
    }

    private fun refreshApps() {
        appHeading?.text = "Your apps"
        val apps = cachedApps
        if (apps != null) { renderApps(apps); grid?.rebind() } else loadApps()
        // Style preferences can change while app identities/content remain unchanged.
        continueWatching?.reload()
    }

    private fun hiddenApps() {
        val apps = cachedApps
        if (apps == null) { loadApps(); return }
        val hidden = HashSet(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())
        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        val dialog = GlassSettings.panel(this, "Show / hide apps", rows)
        for (app in apps) {
            val pkg = app.info.activityInfo.packageName
            val row = GlassSettings.row(this, app.name, if (hidden.contains(pkg)) "Hidden · select to show" else "Visible · select to hide",
                    R.drawable.ic_apps, accentColor(), Runnable {})
            row.setOnClickListener { _ ->
                if (!hidden.add(pkg)) hidden.remove(pkg)
                prefs.edit().putStringSet("hidden_apps", HashSet(hidden)).apply()
                val copy = (row as LinearLayout).getChildAt(1) as LinearLayout
                (copy.getChildAt(1) as TextView).text = if (hidden.contains(pkg)) "Hidden · select to show" else "Visible · select to hide"
                row.contentDescription = app.name + if (hidden.contains(pkg)) ", hidden" else ", visible"
                refreshApps()
            }
            rows.addView(row)
        }
        dialog.show()
    }

    private fun weatherMenu() {
        GlassSettings.options(this, "Weather", arrayOf("Home weather widget", "Widget animation", "Weather city", "Temperature unit", "Hourly / daily forecast"),
            arrayOf("Saved separately for this theme", "Animate only while Home is visible", "Shared weather location", "Celsius or Fahrenheit", "Open weather details"),
            intArrayOf(R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness), { which ->
                if (which == 0) chooseSetting("Home weather widget", arrayOf("On", "Off")) { value ->
                    val edit = prefs.edit().putBoolean("weather_widget", value == 0)
                    if (value == 0) edit.putBoolean("apps_only", false)
                    edit.apply()
                    buildHomeSoon()
                }
                else if (which == 1) chooseSetting("Widget animation", arrayOf("On", "Off")) { value ->
                    prefs.edit().putBoolean("weather_widget_animation", value == 0).apply()
                    weatherWidget?.active(resumed)
                }
                else if (which == 2) WeatherSettings.show(this)
                else if (which == 3) chooseSetting("Temperature unit", arrayOf("Celsius", "Fahrenheit")) { value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply() }
                else homeWeatherController.showForecast()
            }, { which -> if (which == 0) !(harmony() && tv) else which != 1 || weatherWidgetEnabled() })
    }
    private fun screensaverSettings() {
        chooseSetting("Online landscape screensaver", arrayOf("Preview", "Weather city", "Temperature unit", "Start after idle", "Set automatic screensaver")) { which ->
            if (which == 0) launch(Intent(this, LandscapeActivity::class.java))
            else if (which == 1) WeatherSettings.show(this)
            else if (which == 2) chooseSetting("Temperature unit", arrayOf("Celsius", "Fahrenheit")) { value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply() }
            else if (which == 3) chooseSetting("Start screensaver after idle", arrayOf("Off", "2 minutes", "5 minutes", "10 minutes", "15 minutes")) { value ->
                prefs.edit().putInt("idle_screensaver_minutes", intArrayOf(0, 2, 5, 10, 15)[value]).apply()
                armIdleScreensaver()
            }
            else launch(Intent(this, ScreensaverSetupActivity::class.java))
        }
    }

    private fun armIdleScreensaver() {
        handler.removeCallbacks(idleScreensaver)
        val minutes = prefs.getInt("idle_screensaver_minutes", 0)
        if (resumed && minutes > 0) handler.postDelayed(idleScreensaver, minutes * 60_000L)
    }

    override fun onUserInteraction() { super.onUserInteraction(); armIdleScreensaver() }

    private var profileAvatarPreview: ImageView? = null

    /** Refreshes the avatar preview inside the profile dialog, if open. */
    private fun refreshProfileAvatarPreview() {
        val preview = profileAvatarPreview ?: return
        preview.background = avatarSurface(false, 96)
        preview.invalidateOutline()
        val bitmap = avatarBitmap
        if (bitmap != null) preview.setImageBitmap(bitmap)
        else preview.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_person))
    }

    private fun editProfile() {
        val current = profile
        if (current != null && current.isShowing) return
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(22), dp(10), dp(22), dp(8))
        // Avatar preview inside the dialog (user 2026-11-01).
        val preview = ImageView(this)
        profileAvatarPreview = preview
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
        preview.background = avatarSurface(false, 96)
        avatarOutline(preview)
        preview.contentDescription = "Profile photo"
        refreshProfileAvatarPreview()
        val previewRow = LinearLayout(this)
        previewRow.gravity = Gravity.CENTER
        previewRow.setPadding(0, 0, 0, dp(8))
        previewRow.addView(preview, LinearLayout.LayoutParams(dp(96), dp(96)))
        form.addView(previewRow)
        form.addView(button("Photo shape · Round / Rounded square", this::chooseAvatarShape))
        val name = EditText(this)
        name.isSingleLine = true
        name.hint = "Your name"
        name.setText(prefs.getString("name", "Sadique Sadiq"))
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        name.setTextColor(ThemeStyle.foreground(prefs))
        name.setHintTextColor(ThemeStyle.muted(prefs))
        applyAppFont(name)
        name.setPadding(dp(16), dp(12), dp(16), dp(12))
        name.background = toolbarSurface(false)
        form.addView(name)
        // Choose / Remove side by side (user 2026-11-01).
        val photoRow = LinearLayout(this)
        photoRow.orientation = LinearLayout.HORIZONTAL
        val choose = button("Choose photo") {
            try {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7)
            } catch (unavailable: ActivityNotFoundException) {
                Toast.makeText(this, "No photo picker available on this device", Toast.LENGTH_LONG).show()
            } catch (unavailable: SecurityException) {
                Toast.makeText(this, "No photo picker available on this device", Toast.LENGTH_LONG).show()
            }
        }
        val remove = button("Remove photo") {
            val photo = profileAsset("personal-avatar.png")
            if (photo.exists() && !photo.delete()) {
                Toast.makeText(this, "Could not remove photo", Toast.LENGTH_SHORT).show()
                return@button
            }
            avatarBitmap = null
            refreshProfile()
            refreshProfileAvatarPreview()
        }
        val photoBtn = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(choose, photoBtn)
        val photoBtn2 = LinearLayout.LayoutParams(0, -2, 1f)
        photoBtn2.setMargins(dp(4), dp(8), dp(4), dp(4))
        photoRow.addView(remove, photoBtn2)
        form.addView(photoRow)
        form.addView(text("Profile stays on this device. Google login is not used.", 13, MUTED))
        // Save on the left, Later on the right (user 2026-11-01).
        val actionRow = LinearLayout(this)
        actionRow.orientation = LinearLayout.HORIZONTAL
        val save = button("Save") {
            val value = PersonalHomeModel.name(name.text.toString())
            if (value.isEmpty()) { name.error = "Enter your name"; return@button }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply()
            refreshProfile()
            profile?.dismiss()
            handler.post { maybeOfferDefaultHome() }
        }
        val later = button("Later") { profile?.dismiss() }
        val saveParams = LinearLayout.LayoutParams(0, -2, 1f)
        saveParams.setMargins(dp(4), dp(8), dp(4), 0)
        val laterParams = LinearLayout.LayoutParams(0, -2, 1f)
        laterParams.setMargins(dp(4), dp(8), dp(4), 0)
        actionRow.addView(save, saveParams)
        actionRow.addView(later, laterParams)
        form.addView(actionRow)
        val dialog = ThemeDialogBuilder(this).setTitle(if (prefs.getBoolean("setup", false)) "Your profile" else "Welcome · set up your Home")
                .setView(form).create()
        profile = dialog
        dialog.setOnDismissListener { profileAvatarPreview = null; handler.postDelayed(notificationOffer, 1200) }
        dialog.show()
        GlassSettings.styleDialog(this, dialog)
    }

    /**
     * Offers to make Personal Launcher the default Home. The old logic set a permanent
     * "asked once" flag, so after an update/reinstall that reset the default-home setting
     * the prompt never came back. Now the offer reappears whenever we are not the default
     * (with a short snooze after "Later" so it doesn't nag every launch).
     */
    private val updateCheck = object : Runnable {
        override fun run() {
            if (!resumed) return
            val currentProfile = profile
            val currentSearch = searchDialog
            if ((currentProfile == null || !currentProfile.isShowing) && !homeOfferShowing && (currentSearch == null || !currentSearch.isShowing))
                GitHubUpdates.check(this@PersonalHomeActivity, false)
            handler.postDelayed(this, 60_000L)
        }
    }
    private val notificationOffer: Runnable = Runnable(::maybeOfferNotificationAccess)
    private var homeOfferShowing = false
    private fun maybeOfferNotificationAccess() {
        val currentProfile = profile
        val currentSearch = searchDialog
        if (!resumed || homeOfferShowing || (currentProfile != null && currentProfile.isShowing) || (currentSearch != null && currentSearch.isShowing) || GlassSettings.showing(this)) return
        NotificationAccessSetup.offer(this)
    }

    private fun maybeOfferDefaultHome() {
        if (isDestroyed || isFinishing) return
        var alreadyDefault = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME))
                alreadyDefault = rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        if (homeOfferShowing || (HomeAutomation.enabled(this) && VolumeAutoStart.paired(this))) return
        if (alreadyDefault && VolumeAutoStart.paired(this)) return
        if (!HomeSettingsActivity.available(this) && VolumeAutoStart.paired(this)) return
        // One-time migration: drop the old permanent flag so installs that lost the
        // default-home status on update get the offer again.
        if (prefs.contains("home_offer_done")) prefs.edit().remove("home_offer_done").apply()
        if (System.currentTimeMillis() < prefs.getLong("home_offer_snooze", 0)) return
        homeOfferShowing = true
        ThemeDialogBuilder(this)
            .setTitle("Use as Home screen?")
            .setMessage("Set Personal Launcher as your default launcher so the Home button opens it.")
            .setPositiveButton("Set as default") { _, _ -> LauncherSetupUi.show(this) }
            .setNegativeButton("Later") { _, _ -> prefs.edit().putLong("home_offer_snooze",
                    System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000).apply() }
            .setOnDismissListener { homeOfferShowing = false; handler.postDelayed(notificationOffer, 1200) }
            .show()
    }

    private fun refreshProfile() {
        val name = prefs.getString("name", "Sadique Sadiq")
        userName?.text = if (name.isNullOrEmpty()) "Your name" else name
        val bitmap = avatarBitmap
        val photo = avatar
        if (bitmap != null) photo?.setImageBitmap(bitmap)
        else photo?.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_person))
    }

    private fun loadAvatar() {
        // The bounded local avatar must be ready before Home binds its first frame.
        avatarBitmap = ProfileAssets.load(this, profileAsset("personal-avatar.png"), true)
    }

    /** Export settings to JSON via document picker. Excludes secrets. */
    private fun exportSettings() {
        try {
            val json = org.json.JSONObject()
            json.put("version", 1)
            json.put("app", "com.sadique.launcher")
            // Export safe prefs only (no secrets, no cache).
            val all = prefs.all
            val data = org.json.JSONObject()
            for ((k, v) in all) {
                // Skip secrets, caches, and transient state.
                if (k == ThemePreferences.MIGRATED || k == NotificationAccessSetup.ASKED || k.contains("key") || k.contains("secret") || k.contains("token")
                        || k.contains("password") || k.startsWith("weather_cached")) continue
                if (v is String || v is Int || v is Boolean || v is Long || v is Float) {
                    data.put(k, v)
                } else if (v is Set<*>) {
                    val arr = org.json.JSONArray()
                    for (item in v) arr.put(item.toString())
                    data.put(k, arr)
                }
            }
            json.put("settings", data)
            val content = json.toString(2)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "launcher-settings.json")
            // Store content for onActivityResult.
            pendingExport = content
            startActivityForResult(intent, 10)
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingExport: String? = null

    /** Import settings from JSON via document picker. Validates format version. */
    private fun importSettings() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
            startActivityForResult(intent, 11)
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == 10 && result == RESULT_OK && data != null && data.data != null) {
            // Export: write JSON to the picked document.
            val uri = data.data!!
            val content = pendingExport
            pendingExport = null
            if (content != null) {
                worker.execute {
                    try {
                        val out = contentResolver.openOutputStream(uri)
                        out!!.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                            handler.post { Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show() }
                        }
                    } catch (e: Exception) {
                        handler.post { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            return
        }
        if (request == 11 && result == RESULT_OK && data != null && data.data != null) {
            // Import: read and validate JSON, apply settings.
            val uri = data.data!!
            worker.execute {
                try {
                    val input = contentResolver.openInputStream(uri)!!
                    input.use {
                        val scanner = java.util.Scanner(it).useDelimiter("\\A")
                        val content = if (scanner.hasNext()) scanner.next() else ""
                        val json = org.json.JSONObject(content)
                        val version = json.optInt("version", 0)
                        if (version != 1) throw Exception("Unsupported version: $version")
                        if ("com.sadique.launcher" != json.optString("app")) {
                            throw Exception("Not a Personal Launcher export")
                        }
                        val settings = json.getJSONObject("settings")
                        val ed = prefs.edit()
                        val keys = settings.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k == ThemePreferences.MIGRATED || k == NotificationAccessSetup.ASKED) continue
                            val v = settings.get(k)
                            if (v is String) ed.putString(k, v)
                            else if (v is Int) ed.putInt(k, v)
                            else if (v is Boolean) ed.putBoolean(k, v)
                            else if (v is Long) ed.putLong(k, v)
                            else if (v is org.json.JSONArray) {
                                val set = HashSet<String>()
                                val arr = v
                                for (i in 0 until arr.length()) set.add(arr.getString(i))
                                ed.putStringSet(k, set)
                            }
                        }
                        ed.apply()
                        handler.post {
                            Toast.makeText(this, "Settings imported — restarting Home", Toast.LENGTH_SHORT).show()
                            buildHome()
                        }
                    }
                } catch (e: Exception) {
                    handler.post { Toast.makeText(this, "Import failed: " + e.message, Toast.LENGTH_LONG).show() }
                }
            }
            return
        }
        if (request == 9) {
            if (result == RESULT_OK && data != null) {
                val words = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (words != null && words.isNotEmpty()) {
                    val query = words[0]
                    // Google Home behavior: if an installed app matches, show it;
                    // otherwise fall back to YouTube search.
                    if (appMatches(query)) {
                        if (searchDialog == null) searchApps()
                        val input = searchInput
                        if (input != null) { input.setText(query); input.setSelection(input.length()) }
                    } else {
                        youtubeSearch(query)
                    }
                }
            }
            return
        }
        if ((request != 7 && request != 8) || result != RESULT_OK || data == null || data.data == null) return
        val wallpaper = request == 8
        val uri = data.data!!
        val importEpoch = profileEpoch
        val targetAsset = profileAsset(if (wallpaper) "personal-wallpaper.jpg" else "personal-avatar.png")
        worker.execute {
            try {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) throw java.io.IOException("Invalid image")
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > (if (wallpaper) 1920 else 512)) options.inSampleSize *= 2
                var decoded: Bitmap? = null
                contentResolver.openInputStream(uri)?.use { input ->
                    decoded = BitmapFactory.decodeStream(input, null, options)
                }
                val image = decoded ?: throw java.io.IOException("Invalid image")
                val destination = android.util.AtomicFile(targetAsset)
                val out = destination.startWrite()
                try {
                    if (!image.compress(if (wallpaper) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (wallpaper) 85 else 100, out)) throw java.io.IOException("Cannot save image")
                    destination.finishWrite(out)
                } catch (error: Exception) { destination.failWrite(out); throw error }
                handler.post {
                    if (isDestroyed || importEpoch != profileEpoch) return@post
                    if (wallpaper) { customWallpaper = image; prefs.edit().putString("wallpaper_mode", "custom").putInt("wallpaper_source", 0).apply(); sky?.invalidate() }
                    else { avatarBitmap = image; refreshProfile(); refreshProfileAvatarPreview() }
                }
            } catch (error: Exception) {
                handler.post { if (!isDestroyed) Toast.makeText(this, "Could not read this photo. Choose another image.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        val bt = bluetooth
        if (bt != null) { bt.start(); updateBluetooth() }
        telemetry?.start()
        weatherWidget?.active(true)
        analogClock?.active(true)
        harmonyClock?.active(true)
        GitHubUpdates.resume(this)
        VolumeAutoStart.request(this)
        HomeAutomation.onResume(this)
        handler.removeCallbacks(notificationOffer)
        handler.postDelayed(notificationOffer, 1200)
        handler.removeCallbacks(updateCheck)
        handler.postDelayed(updateCheck, 12000)
        armIdleScreensaver()
        handler.removeCallbacks(tick)
        tick.run()
        updateNetwork()
        val watching = continueWatching
        if (watching != null && !prefs.getBoolean("apps_only", false)) watching.start()
        homeWeatherController.refresh()
        updateOnlineWallpaper()
        handler.removeCallbacks(secondTick)
        if (prefs.getBoolean("clock_seconds", false)) secondTick.run()
        val g = grid
        if (pageWidth > 0 && cachedApps == null) loadApps()
        else if (g != null && !g.footerFocusedOrPending() && focusedPkg != null && (panel == null || panel?.isShowing != true)) g.post { g.focusKey(focusedPkg) }
        val conn = connectivity
        if (conn != null && !networkRegistered) {
            try { conn.registerDefaultNetworkCallback(networkCallback); networkRegistered = true }
            catch (unavailable: RuntimeException) { /* Snapshot above remains available. */ }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        val g = grid
        val lm = g?.layoutManager
        if (g != null && lm != null) {
            state.putBoolean("home_grid_horizontal_dock", g.isHorizontalDock())
            state.putParcelable("home_grid_state", lm.onSaveInstanceState())
        }
    }

    override fun onPause() {
        handler.removeCallbacks(themeMenuRefresh)
        VolumeKeys.stop()
        VolumeSlider.hide(this)
        resumed = false
        weatherWidget?.active(false)
        analogClock?.active(false)
        harmonyClock?.active(false)
        bluetooth?.stop()
        telemetry?.stop()
        val g = grid
        val lm = g?.layoutManager
        if (g != null && lm != null) gridState = lm.onSaveInstanceState()
        val pkg = focusedPkg
        if (pkg != null) prefs.edit().putString("home_focus_pkg", pkg).apply()
        continueWatching?.stop()
        handler.removeCallbacks(idleScreensaver)
        handler.removeCallbacks(tick)
        handler.removeCallbacks(secondTick)
        homeWeatherController.pause()
        handler.removeCallbacks(notificationOffer)
        handler.removeCallbacks(updateCheck)
        val conn = connectivity
        if (conn != null && networkRegistered) {
            conn.unregisterNetworkCallback(networkCallback)
            networkRegistered = false
        }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        handler.removeCallbacks(themeMenuRefresh)
        super.onNewIntent(intent)
        setIntent(intent)
        panel?.dismiss()
        searchDialog?.dismiss()
        focusFirstApp()
        handleHomeAction(intent)
    }

    private fun handleHomeAction(intent: Intent?) {
        if (intent == null) return
        if (MainActivity.ACTION_VIEW_INPUTS == intent.action) handler.post { showInputs() }
        else if (MainActivity.ACTION_LAUNCHER_SETTINGS == intent.action) handler.post { LauncherPin.require(this, this::launcherSettings) }
        else if (MainActivity.ACTION_SHOW_SETTINGS == intent.action) handler.post(this::showSettings)
    }

    override fun onBackPressed() {
        launch(Intent(this, LandscapeActivity::class.java))
    }

    private fun focusFirstApp() {
        grid?.focusKey(focusedPkg)
    }

    override fun onRequestPermissionsResult(request: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(request, permissions, results)
        if (request == GlassBluetooth.PERMISSION) {
            val bt = bluetooth
            if (bt != null) {
                bt.requestingPermission = false
                if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) { bt.stop(); bt.start(); bt.show() }
            }
        }
        if (request == ContinueWatchingView.PERMISSION_REQUEST) continueWatching?.reload()
    }

    override fun onDestroy() {
        liquidBackdrop?.close()
        liquidBackdrop = null
        headerBackdrop?.close()
        headerBackdrop = null
        unregisterReceiver(appChanges)
        weatherWidget?.active(false)
        analogClock?.active(false)
        harmonyClock?.active(false)
        homeWeatherController.close()
        bluetooth?.stop()
        telemetry?.stop()
        appLibrary?.close()
        panel?.dismiss()
        profile?.dismiss()
        searchDialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
        continueWatching?.stop()
        prefs.unregisterOnSharedPreferenceChangeListener(homePreferences)
        worker.shutdownNow()
        appWorker.shutdownNow()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN is the ordinary trip to another app, not memory pressure.
        // Keep the displayed online photo warm so Home does not flash the built-in
        // background and download the photo again on every return.
        if (MemoryTrimPolicy.shouldRelease(level, resumed)) {
            sky?.releaseTransient()
            trimAppIcons()
        }
    }

    private fun tileBorder(accent: Int, focused: Boolean): Drawable? {
        if (ThemeStyle.custom(prefs)) return null // The shared path surface owns the matching edge.
        val border = GradientDrawable()
        border.setColor(Color.TRANSPARENT)
        border.cornerRadius = tileRadius().toFloat()
        val thickness = if (focused) intArrayOf(2, 3, 5)[Math.max(0, Math.min(2, prefs.getInt("focus_border", 1)))] else 1
        border.setStroke(dp(thickness), if (tvGlass()) (if (focused) 0xfff4f8ff.toInt() else TvGlassStyle.edge(prefs)) else (if (focused) accent else 0x4028435e))
        val bevel = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x20ffffff, 0x00ffffff, 0x28000000))
        bevel.cornerRadius = tileRadius().toFloat()
        return LayerDrawable(arrayOf<Drawable>(bevel, border))
    }

    companion object {
        private val CYAN = 0xff40d9ff.toInt()
        private val TEXT = 0xfff0f7ff.toInt()
        private val MUTED = 0xffa4bcd6.toInt()

        private fun sameApps(before: List<App>?, after: List<App>): Boolean {
            if (before == null || before.size != after.size) return false
            for (i in before.indices) {
                val a = before[i]
                val b = after[i]
                if (a.icon !== b.icon || a.name != b.name || a.info.activityInfo.packageName != b.info.activityInfo.packageName
                        || a.info.activityInfo.name != b.info.activityInfo.name) return false
            }
            return true
        }

        private fun indexOf(values: IntArray, value: Int): Int {
            for (i in values.indices) if (values[i] == value) return i
            return -1
        }
    }
}
