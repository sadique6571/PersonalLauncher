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
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
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
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File
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
    private lateinit var homeSurfaces: HomeSurfaces
    private lateinit var homeNetwork: HomeNetworkStatus
    private lateinit var homeLabelScheme: HomeLabelScheme
    private lateinit var homeTileGeometry: HomeTileGeometry
    private lateinit var homePerformance: HomePerformance
    private lateinit var homeTileFocus: HomeTileFocus
    private lateinit var homeHarmonyNav: HomeHarmonyNav
    private lateinit var homeWallpaper: HomeCustomWallpaper
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
            editProfile = homeProfile::show,
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
    private var bluetooth: GlassBluetooth? = null
    private var telemetry: GlassTelemetry? = null
    private var appLibrary: AppLibraryDialog? = null
    private fun harmony(): Boolean = ThemeStyle.harmony(prefs)
    private fun glassOs(): Boolean = ThemeStyle.id(prefs) == ThemeStyle.GLASS || harmony()
    /** Lightweight English service shortcuts with D-pad return to the app dock. */
    private fun harmonyShortcuts(): View = homeHarmonyNav.harmonyShortcuts()
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

    private fun harmonyHeroKey(key: Int, event: KeyEvent): Boolean = homeHarmonyNav.harmonyHeroKey(key, event)

    private fun focusHarmonyHero(): Boolean = homeHarmonyNav.focusHarmonyHero()

    private fun fixedHarmonyDock(): Boolean = homeHarmonyNav.fixedHarmonyDock()

    private fun focusBelowToolbar() = homeHarmonyNav.focusBelowToolbar()
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
    private var movingRendered = false
    /** Live tiles by package, for progressive icon fill-in. Rebuilt with the grid. */
    private val tileViews = HashMap<String, View>()
    /** Package of the focused tile, restored after grid rebuilds. */
    private var focusedPkg: String? = null
    /** Last day/night state; when it flips, tile surfaces are refreshed to match. */
    private var lastLight = false
    private var panel: AlertDialog? = null
    private val homeProfile by lazy {
        HomeProfile(
            host = this,
            prefs = prefs,
            tv = tv,
            avatar = { avatar },
            userName = { userName },
            surface = this::surface,
            toolbarSurface = this::toolbarSurface,
            text = this::text,
            chooseSetting = this::chooseSetting,
            profileAsset = this::profileAsset,
            offerDefaultHome = { handler.post { maybeOfferDefaultHome() } },
            onDismiss = { handler.postDelayed(notificationOffer, 1200) },
        )
    }
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
    private fun refreshTileSurfaces() = homeTileFocus.refreshTileSurfaces()

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
        HomeThemeDefaults.migrate(prefs)
        val modes = getSystemService(UiModeManager::class.java)
        tv = modes != null && modes.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        homeSurfaces = HomeSurfaces(this, prefs, this::dp, this::glassOs)
        homeNetwork = HomeNetworkStatus(this, prefs, this::dp, this::glassOs, { connectivity }, { networkStatus }, { avatar }, { homeWeatherController.refresh() })
        homeLabelScheme = HomeLabelScheme(prefs, tv, this::dp, this::glassOs, clockContrast, { sky }, { liquidBackdrop }, { headerBackdrop }, { userName }, { greeting }, { homeWeather }, { networkStatus }, { time }, { ampm }, { date }, { islamicDate }, { appHeading })
        homeTileGeometry = HomeTileGeometry(this, prefs, tv, this::dp, { pageWidth }, { appBody }, { grid?.columnCount ?: 4 })
        homePerformance = HomePerformance(this, prefs) { sky }
        homeTileFocus = HomeTileFocus(this, prefs, tv, this::dp, this::glassOs, { effect -> homePerformance.allows(effect) }, homeSurfaces, tileViews, { sky }, { appHeading }, { avatar }, { focusedSpotlight })
        homeHarmonyNav = HomeHarmonyNav(this, prefs, tv, this::dp, this::launch, this::showAppLibrary, { grid }, { focusedPkg }, { searchButton }, { weatherWidget }, { harmonyClock }, { page })
        homeWallpaper = HomeCustomWallpaper(this, prefs, worker, handler, { profileEpoch }, { isDestroyed }, this::profileAsset, { customWallpaper = it }, { sky }, this::adaptTextColors)
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
        homeProfile.loadAvatar()
        buildHome()
        loadWallpaper()
        if (!prefs.getBoolean("setup", false)) handler.post { homeProfile.show() }
        else { handleHomeAction(intent); handler.post { maybeOfferDefaultHome() } }
    }

    private fun dp(value: Float): Int = Math.round(value * resources.displayMetrics.density)
    /** Int overload for the call sites that pass sizes: same rounding as dp(float). */
    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun appearanceDaylight(): Float = homeSurfaces.appearanceDaylight()

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

    /** Width the app grid can actually use (see HomeTileGeometry). */
    private fun gridWidth(): Int = homeTileGeometry.gridWidth()

    /** Logo size inside tiles from settings: small / normal / large. */
    private fun logoScale(): Float = homeSurfaces.logoScale()

    /** Current theme accent color: Midnight Cyan, Sunset Orange, Deep Purple, or Custom. */
    private fun accentColor(): Int = homeSurfaces.accentColor()

    /** Tile corner radius from settings: sharp / rounded / round. */
    private fun tileRadius(): Int = homeSurfaces.tileRadius()

    private fun surface(focused: Boolean): GradientDrawable = homeSurfaces.surface(focused)
    private fun appSurface(focused: Boolean): GradientDrawable = homeSurfaces.appSurface(focused)
    private fun appSurface(accent: Int, focused: Boolean): GradientDrawable = homeSurfaces.appSurface(accent, focused)
    private fun surface(focused: Boolean, drawer: Boolean): GradientDrawable = homeSurfaces.surface(focused, drawer)

    private fun text(value: String, size: Int, color: Int): TextView = homeSurfaces.text(value, size, color)

    private fun toolbarSurface(focused: Boolean): GradientDrawable = homeSurfaces.toolbarSurface(focused)

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
        HomeThemeDefaults.glassDefaults(prefs)
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
            gradientAccent = { if (gradientWallpaper()) homeTileFocus.glassAccent else null },
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
        val headerViews = HomeHeader(
            host = this,
            prefs = prefs,
            profile = homeProfile,
            tv = tv,
            tvGlass = tvGlass(),
            glassOs = glassOs(),
            harmony = harmony(),
            text = this::text,
            applyAppFont = this::applyAppFont,
            surface = this::surface,
            focusBelowToolbar = this::focusBelowToolbar,
            updateNetwork = this::updateNetwork,
            openWifi = { openSetting(Settings.ACTION_WIFI_SETTINGS) },
            toolbarKey = this::toolbarKey,
            showBluetooth = { bluetooth?.show() },
        ).build(header, greetingText())
        avatar = headerViews.avatar
        userName = headerViews.name
        greeting = headerViews.greeting
        networkStatus = headerViews.networkStatus
        homeWeather = headerViews.weather
        bluetoothStatus = headerViews.bluetooth
        page.addView(header)
        val bar = headerViews.actions
        actions = bar
        if (glassOs() && !harmony()) {
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
        val clockViews = HomeClockLayout(
            this, prefs, tv, resumed, glassOs(), harmony(), weatherWidgetEnabled(),
            clockDatesInline, this::dp, this::text, this::clockAlignment, this::fitClockText,
        ).build(root, sky) {
            if (clockContrast.update(sky, prefs, tv, dp(150).toFloat())) adaptVisibleTextColors()
        }
        val clock = clockViews.container
        clockLayout = clock
        time = clockViews.time
        date = clockViews.date
        ampm = clockViews.ampm
        islamicDate = clockViews.islamicDate
        analogClock = clockViews.analogClock
        clockTextSize = clockViews.textSize
        focusedSpotlight = clockViews.spotlight
        if (focusedSpotlight != null) updateSpotlight()
        val heading = text("Your apps", prefs.getInt("focus_title_size", if (tv) 32 else 26), TEXT)
        appHeading = heading
        heading.isSingleLine = true
        heading.ellipsize = TextUtils.TruncateAt.END
        heading.setPadding(dp(4), dp(8), 0, dp(12))
        val grid = HomeAppGrid<PersonalApp>(this, { app -> app.info.activityInfo.packageName },
                { app, width ->
                    val carried = moveSession.carriedApp
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
        homeProfile.refreshProfile()
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
    private fun updateOnlineWallpaper() = homeWallpaper.updateOnlineWallpaper()

    /** Refreshes the Continue Watching row (banner rows were removed 2026-11-01). */
    private fun updateContentRows() {
        continueWatching?.reload()
    }

    private fun updateNetwork() = homeNetwork.update()

    private val artworkCatalog by lazy {
        HomeArtworkCatalog(
            host = this, prefs = prefs, tv = tv, appWorker = appWorker, handler = handler, fallbackAccent = CYAN,
            appsOnHome = { cachedApps }, tileVisible = tileViews::containsKey,
            renderApps = this::renderApps, updateTileIcon = this::updateTileIcon, accentColor = this::accentColor,
        )
    }

    private fun loadApps() {
        moveSession.clear()
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
        if (moveSession.carriedApp != null) return
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

    private val moveSession by lazy {
        HomeMoveSession(
            host = this, prefs = prefs, currentGrid = { grid }, currentApps = { cachedApps },
            orderedApps = this::orderedApps, clearCategory = { selectedCategory = "" },
            movingRendered = { movingRendered = true }, showHeading = { appHeading?.text = it },
            gridWidth = this::gridWidth, submitHomeApps = this::submitHomeApps,
            renderApps = this::renderApps, focusFirstApp = this::focusFirstApp,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (settingsRemoteKeys.contains(key) && !SettingsKeyPolicy.isNumberEntryKey(key)) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) RemoteKeys.launchHomeSettings(this)
            return true
        }
        if (VolumeKeys.direction(event.keyCode) != 0
                && !PassiveHomeService.enabled(this) && VolumeKeys.control(this, event)) return true
        if (event.action == KeyEvent.ACTION_DOWN) HomeNavSounds.play(this, event.keyCode)
        // Press effect: tile dips slightly the moment OK is pressed.
        if (event.action == KeyEvent.ACTION_DOWN && moveSession.carriedApp == null
                && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
                || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val focused = currentFocus
            if (focused != null && focused !is HarmonyAppTile && tileViews.containsValue(focused)) {
                focused.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
            }
        }
        if (moveSession.carriedApp != null && event.action == KeyEvent.ACTION_DOWN)
            return moveSession.dispatchDown(event) || super.dispatchKeyEvent(event)
        val g = grid
        if (g != null && g.navigateKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private val demoPlacement: Runnable = Runnable(::placeDemoCards)
    private fun placeDemoCards() = homeTileGeometry.placeDemoCards(grid, isDestroyed)

    private fun referenceCardHeight(cellWidth: Int): Int = homeTileGeometry.referenceCardHeight(cellWidth)
    private fun referenceCardHeight(cellWidth: Int, columns: Int): Int = homeTileGeometry.referenceCardHeight(cellWidth, columns)
    private fun referenceCaptionHeight(): Int = homeTileGeometry.referenceCaptionHeight()
    private fun referenceDockHeight(): Int = homeTileGeometry.referenceDockHeight()
    private val tileFactory by lazy {
        HomeTileFactory(
            host = this, prefs = prefs, tv = tv, dp = this::dp, text = this::text,
            tileRadius = this::tileRadius, cardHeight = this::referenceCardHeight,
            captionHeight = this::referenceCaptionHeight, surface = this::appSurface,
            border = this::tileBorder, perfAllows = this::perfAllows, accentColor = this::accentColor,
            selectApp = { focusedPkg = it.info.activityInfo.packageName },
            updateFocusWallpaper = this::updateTileFocusWallpaper, launch = this::launch,
            isCarryingApp = { moveSession.carriedApp != null }, showOptions = this::showTileOptions,
            trackAttachment = this::trackTileAttachment,
        )
    }

    private fun createAppTile(app: PersonalApp, width: Int, moving: Boolean): View {
        artworkCatalog.requestIcon(app)
        return tileFactory.create(app, width, moving, grid!!)
    }

    private fun updateTileFocusWallpaper(tile: View, app: PersonalApp, focus: Boolean) = homeTileFocus.updateTileFocusWallpaper(tile, app, focus)

    private fun showTileOptions(app: PersonalApp) {
        ThemeDialogBuilder(this).setTitle(app.name).setItems(arrayOf("App info", "Hide from launcher", "Move app", getString(R.string.h_category))) { _, which ->
            if (which == 0) launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.activityInfo.packageName)))
            else if (which == 2) moveSession.start(app)
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
    private fun updateGlassAccent(color: Int) = homeTileFocus.updateGlassAccent(color)
    private fun updateSpotlight() = homeTileFocus.updateSpotlight()
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
                HomeDashboard.IDENTITY -> homeProfile.show()
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
    private fun applyPerformanceProfile() = homePerformance.applyProfile()

    /** Returns true if the named effect is allowed in the current performance tier. */
    private fun perfAllows(effect: String): Boolean = homePerformance.allows(effect)

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
    private fun applyAppFont(view: TextView) = homeSurfaces.applyAppFont(view)

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

    private fun hasFavoriteWallpaper(): Boolean = homeWallpaper.hasFavoriteWallpaper()

    private fun loadWallpaper() = homeWallpaper.loadWallpaper()

    /** Fixed label style for all wallpaper-backed text (user 2026-11-01). */
    private fun adaptVisibleTextColors() = homeLabelScheme.adaptVisible()

    private fun adaptTextColors() = homeLabelScheme.adaptTextColors()

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

    /**
     * Polls for updates while Home is visible and the profile and search dialogs are closed.
     */
    private val updateCheck = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (!homeProfile.isShowing && !homeOfferShowing && !homeSearch.isShowing)
                GitHubUpdates.check(this@PersonalHomeActivity, false)
            handler.postDelayed(this, 60_000L)
        }
    }
    private val notificationOffer: Runnable = Runnable(::maybeOfferNotificationAccess)
    private var homeOfferShowing = false
    private fun maybeOfferNotificationAccess() {
        if (!resumed || homeOfferShowing || homeProfile.isShowing || homeSearch.isShowing || GlassSettings.showing(this)) return
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
                    else homeProfile.setAvatar(image)
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
        homeProfile.dismiss()
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

    private fun tileBorder(accent: Int, focused: Boolean): Drawable? = homeSurfaces.tileBorder(accent, focused)

    companion object {
        private val CYAN = 0xff40d9ff.toInt()
        private val TEXT = 0xfff0f7ff.toInt()
        private val MUTED = 0xffa4bcd6.toInt()


    }
}
