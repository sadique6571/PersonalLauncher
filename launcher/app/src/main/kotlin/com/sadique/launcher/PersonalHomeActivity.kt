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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
import android.graphics.Typeface
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
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
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
import java.util.Calendar
import java.util.Collections
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
    private fun weatherWidgetEnabled(): Boolean = ((harmony() && tv) || prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) && !prefs.getBoolean("apps_only", false)
    private fun clockAlignment(): Int = WeatherWidgetLayout.clockAlignment(weatherWidgetEnabled(), ThemeStyle.clockAlignment(prefs))
    private lateinit var homeWeatherController: HomeWeatherController
    private val homeBingWallpaper = HomeBingWallpaper(this)
    private val homePreferences = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ("ambient_strength" == key) updateAmbient()
        homeWeatherController.preferencesChanged(key)
    }
    private lateinit var prefs: SharedPreferences
    private lateinit var homeSearch: HomeSearch
    private val settingsMenus by lazy {
        HomeSettingsMenus(
            host = this,
            prefs = prefs,
            tv = tv,
            settingsTransfer = settingsTransfer,
            chooseSetting = this::chooseSetting,
            refreshApps = this::refreshApps,
            buildHomeSoon = this::buildHomeSoon,
            updateClock = this::updateClock,
            resetClockTick = { enabled ->
                handler.removeCallbacks(secondTick)
                if (enabled && resumed) secondTick.run() else updateClock()
            },
            configureHarmonyClock = { harmonyClock?.configure(prefs) },
            reloadIcons = {
                appWorker.execute { IconCache(this).clear() }
                cachedApps = null
                loadApps()
            },
            setAppHeadingSize = { appHeading?.setTextSize(it.toFloat()) },
            wallpaper = { sky },
            switchVisualStyle = this::switchVisualStyle,
            resetCurrentTheme = this::resetCurrentTheme,
            applyAppearance = this::applyAppearance,
            profileAsset = this::profileAsset,
            loadWallpaper = this::loadWallpaper,
            rearrangeApps = this::rearrangeApps,
            requestWatchNextAccess = { continueWatching?.requestAccess() },
            editProfile = this::editProfile,
            applyPerformanceProfile = this::applyPerformanceProfile,
            cachedApps = { cachedApps },
            loadApps = this::loadApps,
            accentColor = this::accentColor,
            activateWeatherWidget = { weatherWidget?.active(resumed) },
            showForecast = { homeWeatherController.showForecast() },
            launch = this::launch,
            armIdleScreensaver = this::armIdleScreensaver,
        )
    }
    private lateinit var settingsTransfer: HomeSettingsTransfer
    private var page: LinearLayout? = null
    private var header: LinearLayout? = null
    private var actions: LinearLayout? = null
    private var clockLayout: LinearLayout? = null
    private var appBody: LinearLayout? = null
    private var grid: HomeAppGrid<PersonalApp>? = null
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
    private fun harmonyHero(): View {
        val hero = HarmonyHomeHero(
            this, prefs, resumed, this::dp, this::text, this::greetingText, this::harmonyHeroKey,
            settingsMenus::harmonyClockSettings, homeWeatherController::showForecast,
            homeWeatherController::restoreWidget,
        ).build()
        harmonyClock = hero.clock
        harmonyGreetingValue = hero.greeting
        harmonyDateValue = hero.date
        harmonyQuoteValue = hero.quote
        if (hero.weather != null) weatherWidget = hero.weather
        return hero.container
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
    private var avatarBitmap: Bitmap? = null
    private var customWallpaper: Bitmap? = null
    private val clockContrast = HomeClockContrast()
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
    private var cachedApps: List<PersonalApp>? = null
    /** Move mode: the app being carried, and the visible package order while it moves. Null when off. */
    private var carriedApp: PersonalApp? = null
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
        settingsTransfer = HomeSettingsTransfer(this, prefs, worker, handler, this::buildHome)
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
        homeSearch = HomeSearch(
            this, prefs, tv, MUTED, { cachedApps }, this::dp, this::text, this::applyAppFont,
            this::toolbarSurface, this::tryLaunch, this::launch,
        )
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
        searchButton = iconAction(getString(R.string.h_search), R.drawable.ic_search, homeSearch::show)
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
            if (clockContrast.update(sky, prefs, tv, dp(150).toFloat())) adaptVisibleTextColors()
        }
        val heading = text("Your apps", prefs.getInt("focus_title_size", if (tv) 32 else 26), TEXT)
        appHeading = heading
        heading.isSingleLine = true
        heading.ellipsize = TextUtils.TruncateAt.END
        heading.setPadding(dp(4), dp(8), 0, dp(12))
        val grid = HomeAppGrid<PersonalApp>(this, { app -> app.info.activityInfo.packageName },
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
    private val clockDisplay by lazy { HomeClockDisplay(this, prefs, this::dp) }

    private fun greetingText(): String = clockDisplay.greetingText()

    private fun updateClock() {
        clockDisplay.update(
            this::fitClockText, ampm, date, harmonyDateValue, harmonyGreetingValue, harmonyQuoteValue,
            islamicDate, sky, greeting,
        )
    }

    private fun fitClockText(text: CharSequence) {
        val view = time ?: return
        clockDisplay.fit(view, text, pageWidth, clockTextSize, glassOs() || weatherWidgetEnabled(), clockDatesInline)
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

    private val artworkCatalog by lazy {
        HomeArtworkCatalog(
            host = this, prefs = prefs, tv = tv, appWorker = appWorker, handler = handler, fallbackAccent = CYAN,
            appsOnHome = { cachedApps }, tileVisible = tileViews::containsKey,
            renderApps = this::renderApps, updateTileIcon = this::updateTileIcon, accentColor = this::accentColor,
        )
    }

    private fun loadApps() {
        if (carriedApp != null) { carriedApp = null; moveOrder = null }
        artworkCatalog.load()
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

    private fun renderApps(apps: List<PersonalApp>) {
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
        if (restoreFocus && (panel == null || panel?.isShowing != true) && (!homeSearch.isShowing))
            g.focusKey(focusedPkg)
        homeSearch.refresh()
    }

    private fun submitHomeApps(apps: List<PersonalApp>, columns: Int, availableWidth: Int) {
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
    private fun startMoveMode(app: PersonalApp) {
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
        val byPackage = LinkedHashMap<String, PersonalApp>()
        for (app in cachedApps!!) byPackage[app.info.activityInfo.packageName] = app
        val carried = carriedApp!!
        appHeading?.text = "Move " + carried.name
        val avail = gridWidth()
        val g = grid!!
        val columns = g.columnCount
        val movingPkg = carried.info.activityInfo.packageName
        val moved = ArrayList<PersonalApp>()
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
    private val tileFactory by lazy {
        HomeTileFactory(
            host = this, prefs = prefs, tv = tv, dp = this::dp, text = this::text,
            tileRadius = this::tileRadius, cardHeight = this::referenceCardHeight,
            captionHeight = this::referenceCaptionHeight, surface = this::appSurface,
            border = this::tileBorder, perfAllows = this::perfAllows, accentColor = this::accentColor,
            selectApp = { focusedPkg = it.info.activityInfo.packageName },
            updateFocusWallpaper = this::updateTileFocusWallpaper, launch = this::launch,
            isCarryingApp = { carriedApp != null }, showOptions = this::showTileOptions,
            trackAttachment = this::trackTileAttachment,
        )
    }

    private fun createAppTile(app: PersonalApp, width: Int, moving: Boolean): View {
        artworkCatalog.requestIcon(app)
        return tileFactory.create(app, width, moving, grid!!)
    }

    private fun updateTileFocusWallpaper(tile: View, app: PersonalApp, focus: Boolean) {
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

    private fun showTileOptions(app: PersonalApp) {
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
    }

    private fun trackTileAttachment(tile: View, app: PersonalApp, g: HomeAppGrid<PersonalApp>) {
        tile.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                tileViews[app.info.activityInfo.packageName] = view
                if (ThemeStyle.custom(prefs) && tv) { g.removeCallbacks(demoPlacement); g.post(demoPlacement) }
            }
            override fun onViewDetachedFromWindow(view: View) {
                if (tileViews[app.info.activityInfo.packageName] === view) tileViews.remove(app.info.activityInfo.packageName)
            }
        })
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

    private fun orderedApps(apps: List<PersonalApp>): MutableList<PersonalApp> {
        val byPackage = LinkedHashMap<String, PersonalApp>()
        for (app in apps) byPackage[app.info.activityInfo.packageName] = app
        val saved = ArrayList<String>()
        try {
            val array = org.json.JSONArray(prefs.getString("app_order", "[]"))
            for (i in 0 until array.length()) saved.add(array.getString(i))
        } catch (malformed: org.json.JSONException) { /* Fall back to alphabetical order. */ }
        val result = ArrayList<PersonalApp>()
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

    private fun moveApp(moving: PersonalApp) {
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
    private fun appCategory(app: PersonalApp): String {
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
                HomeDashboard.SETTINGS -> settingsMenus.launcherSettings()
                HomeDashboard.SEARCH -> homeSearch.show()
                HomeDashboard.STORE -> homeSearch.openStore("")
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
    private fun clockVisible(): Boolean {
        return !prefs.getBoolean("hide_clock", false) && !prefs.getBoolean("apps_only", false)
    }

    private fun gradientWallpaper(): Boolean {
        return glassOs() && "solid" == prefs.getString("wallpaper_mode", "solid")
    }

    private fun appLabelSizes(): IntArray {
        return if (glassOs()) intArrayOf(14, 16, 20, 22) else intArrayOf(14, 17, 20, 24)
    }

    private fun switchVisualStyle(style: Int) {
        if (ThemeStyle.id(prefs) == style) return
        val priorLogo = prefs.getInt("logo_size", 1)
        val priorMark = ThemeStyle.id(prefs) != ThemeStyle.CLASSIC
        val wasHarmony = harmony()
        artworkCatalog.invalidate() // Reject artwork started for the previous style, even if its palette was similar.
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
        if (resumed && !isFinishing && !isDestroyed && !GlassSettings.showing(this)) settingsMenus.themeSettings()
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
        artworkCatalog.invalidate()
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

    private fun hasFavoriteWallpaper(): Boolean {
        return profileAsset("favorite_wallpaper.jpg").isFile
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
        if (clockLabel) label.setTextColor(clockContrast.ink)
        if (clockLabel && !prefs.getBoolean("clock_shadow", true)) label.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        else {
            val darkInk = if (clockLabel) clockContrast.ink != 0xffffffff.toInt() else headerLabel && ThemeStyle.light(prefs) && headerBgMode() != 0
            label.setShadowLayer(dp(if (clockLabel) 5 else 2).toFloat(), 0f, dp(if (clockLabel) 2 else 1).toFloat(), if (darkInk) 0x99ffffff.toInt() else 0xe6000000.toInt())
        }
    }

    private fun adaptTextColors() {
        liquidBackdrop?.refresh()
        headerBackdrop?.refresh()
        clockContrast.update(sky, prefs, tv, dp(150).toFloat())
        adaptVisibleTextColors()
    }

    /** Applies the current light/dark text scheme to every wallpaper-backed label. */
    private fun chooseSetting(title: String, options: Array<String>, selected: IntConsumer) {
        GlassSettings.choices(this, title, options, { HomeSettingsChoices.currentIndex(title, prefs, tv, glassOs(), this::headerBgMode,
                this::appLabelSizes, this::hasFavoriteWallpaper, this::gradientWallpaper) }, { which ->
            selected.accept(which)
            GlassSettings.refresh(this)
        })
    }

    private fun refreshApps() {
        appHeading?.text = "Your apps"
        val apps = cachedApps
        if (apps != null) { renderApps(apps); grid?.rebind() } else loadApps()
        // Style preferences can change while app identities/content remain unchanged.
        continueWatching?.reload()
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
            if ((currentProfile == null || !currentProfile.isShowing) && !homeOfferShowing && !homeSearch.isShowing)
                GitHubUpdates.check(this@PersonalHomeActivity, false)
            handler.postDelayed(this, 60_000L)
        }
    }
    private val notificationOffer: Runnable = Runnable(::maybeOfferNotificationAccess)
    private var homeOfferShowing = false
    private fun maybeOfferNotificationAccess() {
        val currentProfile = profile
        if (!resumed || homeOfferShowing || (currentProfile != null && currentProfile.isShowing) || homeSearch.isShowing || GlassSettings.showing(this)) return
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
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (settingsTransfer.onActivityResult(request, result, data)) return
        if (request == 9) { homeSearch.onSpeechResult(result, data); return }
        if ((request != 7 && request != 8) || result != RESULT_OK || data == null || data.data == null) return
        val wallpaper = request == 8
        val uri = data.data!!
        val importEpoch = profileEpoch
        val targetAsset = profileAsset(if (wallpaper) "personal-wallpaper.jpg" else "personal-avatar.png")
        worker.execute {
            try {
                val image = HomePhotoImport.readAndSave(contentResolver, uri, targetAsset, wallpaper)
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
        homeSearch.dismiss()
        focusFirstApp()
        handleHomeAction(intent)
    }

    private fun handleHomeAction(intent: Intent?) {
        if (intent == null) return
        if (MainActivity.ACTION_VIEW_INPUTS == intent.action) handler.post { showInputs() }
        else if (MainActivity.ACTION_LAUNCHER_SETTINGS == intent.action) handler.post { LauncherPin.require(this, settingsMenus::launcherSettings) }
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
        homeSearch.dismiss()
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
            artworkCatalog.trimIcons()
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


    }
}
