package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Collections
import java.util.HashSet
import java.util.Locale

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
class MainActivity : Activity(), AppAdapter.TileListener {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LauncherLocale.wrap(base))   // locale
    }

    companion object {
        private const val LAUNCHER_SETTINGS_REQUEST = 42
        /** Sent by QuickSettingsActivity: open Launcher settings from here so Home rebuilds when they close. */
        const val ACTION_LAUNCHER_SETTINGS = "com.sadique.launcher.action.LAUNCHER_SETTINGS"
        const val ACTION_SHOW_SETTINGS = "com.sadique.launcher.action.SHOW_SETTINGS"
        /** The system input switcher, forwarded here by InputsRequestActivity while handle_view_inputs is on. */
        const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"
        /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
        @JvmField
        var COLUMNS = 4
        private const val MINUTE_MS = 60_000L
        /**
         * 2025-01-01. TVs without a clock battery boot at the epoch (08:00 in China) and keep it until network
         * time arrives; the clock, date and greeting stay hidden before then.
         */
        private const val TIME_VALID_AFTER_MS = 1_735_689_600_000L
        // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
        // or Developer options → "Don't keep activities"); they only reset when the process dies.
        private var lastFocused = 0
        /** App of the last focused tile, so focus follows it when the app list changes. */
        private var lastFocusedKey: String? = null
        /** The day the petal shower last played, so it greets the first Home of each day only. */
        private var lastPetalShower: LocalDate? = null

        private fun setFestiveIcon(pill: TextView, icon: Drawable) {
            // The pills tint their icons to follow focus; the festive ones keep their own colours.
            pill.compoundDrawableTintList = null
            pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        }

        /** Runs [action] when the view gains focus, keeping any focus listener it already had. */
        private fun addFocusAction(view: View, action: Runnable) {
            val previous = view.onFocusChangeListener
            view.setOnFocusChangeListener { v, focused ->
                previous?.onFocusChange(v, focused)
                if (focused) action.run()
            }
        }



        private fun liftTowardsWhite(argb: Int, amount: Float): Int {
            var r = (argb shr 16) and 0xFF
            var g = (argb shr 8) and 0xFF
            var b = argb and 0xFF
            r += ((255 - r) * amount).toInt()
            g += ((255 - g) * amount).toInt()
            b += ((255 - b) * amount).toInt()
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
    }

    private var rtlAppGrid = false
    private lateinit var inputActions: TvInputActions
    private lateinit var liveTvPkg: String
    /** Launcher settings' change count this Home was built with (see onResume). */
    private var settingsRevision = 0L
    /** Idle time on Home before ClockActivity; 0 is off (config.xml, home_idle_clock_minutes). */
    private var idleClockMs = 0L
    private val showIdleClock = Runnable { startActivity(Intent(this, ClockActivity::class.java)) }
    private lateinit var liveTvInputId: String
    /** The app library is on (config.xml, app_library): only then can the tile menu take an app off Home. */
    private var appLibraryEnabled = false
    /** The same opt-in menu is available from either hold-OK or the remote's Menu key. */
    private var tileMenuEnabled = false
    private lateinit var sourcePanelService: String
    private var coocaaSources = false

    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var root: FrameLayout
    private lateinit var wallpaperPlayer: WallpaperPlayer
    private lateinit var glass: GlassView
    private lateinit var shelf: LauncherShelf
    private var cinematic = false
    private var appGlow = false
    private var cinematicWhiteFocus = false
    private var glassAccent = 0
    private var animations = false
    private var advancedEffects = false
    private lateinit var greeting: TextView
    private lateinit var clockBlock: View
    private lateinit var btnInputs: View
    /** config show_inputs_pill: false keeps the Inputs pill hidden even when the TV has inputs. */
    private var showInputsPill = true
    private lateinit var btnWifi: TextView
    /** The network pill's icon as last set, so a refresh that changes nothing does not lay the top bar out again. */
    private var wifiIcon = 0
    /** The pills have been filled in once; later resumes refresh them after the first frame. */
    private var pillsShown = false
    private lateinit var shamsiDate: TextView
    /** lunar_date: the lunisolar date under the date line, and the time zone it is computed for. */
    private lateinit var lunarDate: TextView
    private var lunar = false
    private var lunarOffset = 0
    private var stats: TextView? = null
    private lateinit var sportsPanel: LauncherSportsPanel
    /** config.xml show_stats, overridable by a theme. Off: the line is gone and nothing is read. */
    private var showStats = false
    /** config.xml inputs_connected_only: the Inputs menu lists only connected inputs, the rest behind "More inputs". */
    private var inputsConnectedOnly = false
    private var shamsi = false
    /** greeting_name: said after the greeting, which is shown in capitals. */
    private var greetingName = ""
    /** Optional time-of-day backdrop (config.xml, sky_intensity); 0 keeps it hidden. */
    private lateinit var sky: SkyView
    private lateinit var vpnApp: String
    /** vpn_pill: the VPN shown as its own pill (btnVpn) instead of a badge in the network pill. Off with top_menu. */
    private var vpnPill = false
    private lateinit var btnVpn: TextView
    /** The VpnBadge pill state btnVpn shows, so a refresh that changes nothing does not restyle it. */
    private var vpnPillState = VpnBadge.PILL_HIDDEN
    /** icon_only_vpn: the VPN pill is a round lock button; off, it keeps the plain glass look saved here. */
    private var iconOnlyVpn = false
    private var vpnPlainBackground: Drawable.ConstantState? = null
    private var vpnPlainInk: ColorStateList? = null
    private lateinit var springX: SpringAnimation
    private lateinit var springY: SpringAnimation
    private var glassTargetX = 0f
    private var glassTargetY = 0f
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private var glassScrollShift = 0f
    /** The same for springX, when single_row scrolls the grid sideways. */
    private var glassScrollShiftX = 0f
    private var glassCardW = 0   // width of the tile the glass was last fitted to
    private var glassShown = false
    private val tileRect = Rect()
    private val revealRect = Rect()
    private var density = 0f
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private var moduleCards = false
    /** config settings_key_keycodes / settings_key_panel, so the Settings button works on Home without HomeKeyService. */
    private var settingsKeycodes: IntArray = intArrayOf()
    private var settingsKeyPanel = false
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private var reducedMotion = false
    /** config.xml dock_row: the first row is a separate dock (band, parked low, top shelf) rather than plain grid. */
    private var dockRow = false
    /** config single_row: every app in the dock's row, scrolling sideways, no grid below. */
    private var singleRow = false
    /** The highlight springs past its target (config.xml, focus_overshoot); false goes straight there. */
    private var focusOvershoot = false
    /** Festive skin from the theme (see Festival); applied while it is active today. */
    private var festival: Festival = Festival.NONE
    private var festive = false
    private var diya: DiyaDrawable? = null
    private var bell: BellDrawable? = null
    private var marigold: MarigoldDrawable? = null
    private var greetingColor = 0
    /** boot_to_live_tv: open the Live TV app on the first Home of each boot. */
    private var bootToLiveTv = false
    /** open_on_start_choice: the app picked in Launcher settings may open on the first Home of each boot. */
    private var openOnStartChoice = false
    /** open_on_start_network_wait, in ms; the boot's app while it waits for the network, and until when. */
    private var openOnStartWaitMs = 0L
    @Volatile
    private var startPending: Intent? = null
    private var startDeadline = 0L
    /** Opens the boot's app once the network is validated (networkCallback posts this) or the wait is over. */
    private val openWhenOnline = object : Runnable {
        override fun run() {
            if (startPending == null) return
            if (!OpenOnStart.opensNow(OpenOnStart.online(this@MainActivity), SystemClock.elapsedRealtime(),
                    startDeadline)) return
            handler.removeCallbacks(this)   // the deadline's run, when the network came first
            val i = startPending
            startPending = null
            open(i!!)
        }
    }
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private var hiddenApps: Set<String> = Collections.emptySet()
    /** library_excluded_apps: packages the App library catalogue never offers. */
    private var libraryExcludedApps: Set<String> = Collections.emptySet()
    private var cards: List<Cards.Card> = Collections.emptyList()
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private var tilesReady = false
    private var settingsRecreationPending = false
    /** Between onStop and onStart: Home was fully covered by an app, not just by the translucent quick panel. */
    private var stopped = false
    /** Between onStart and onStop: Home is (at least partly) on screen. */
    private var started = false
    /**
     * Between onResume and onPause: Home is in front. The idle clock only runs then; a key's ACTION_UP can still
     * reach Home after onPause, when that key opened another app, and must not start the clock over the app.
     */
    private var resumed = false
    /** Rebuild the tiles on the way back to Home only when apps changed meanwhile (TileRefresh). */
    private val tileRefresh = TileRefresh()
    /** PackageManager's change counter at the last rebuild, to catch a package broadcast that never arrived. */
    private var packageSequence = 0
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private var openPopup: PopupWindow? = null
    private lateinit var btnReorder: View
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private lateinit var dockApps: List<String>
    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private var fillDockLeavesSlots = false
    private var appLibrary: AppLibraryDialog? = null
    private var appLibrarySelection = false
    private var libraryOkOpens = false
    private var libraryFocusPending = false
    private var pendingFocusScroll: RecyclerView.OnScrollListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val reorder = LauncherReorderController(
        host = this,
        currentGrid = { grid },
        currentAdapter = { adapter },
        lastFocused = { lastFocused },
        singleRow = { singleRow },
        rtlAppGrid = { rtlAppGrid },
        fillDockLeavesSlots = { fillDockLeavesSlots },
        dockApps = { dockApps },
        setAccent = { glass.setAccent(it) },
        defaultAccent = { if (festive) festival.accentColor() else 0 },
        setButtonActive = { btnReorder.isActivated = it },
        reloadTiles = this::reloadTiles,
        focusTile = this::focusTile,
        openLibrary = this::onLibraryRequested,
    )
    private val moduleCardMenus = ModuleCardMenus(
        host = this,
        root = { root },
        density = { density },
        currentCards = { cards },
        canPrompt = { moduleCards && tilesReady && !reorder.reordering },
        reloadTiles = this::reloadTiles,
        restoreFocus = this::restoreFocus,
        glassPopup = this::glassPopup,
        dropIn = this::dropIn,
    )
    private val launcherStats = LauncherStats(this, handler, { stats }, { showStats })
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { updateWifiLabel() }
        }

        override fun onLost(network: Network) {
            handler.post { updateWifiLabel() }
        }

        /** wifi_status_warnings follows validation; signal-strength updates also land here, so skip those. */
        private var lastValidated = false

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            // open_on_start_network_wait: the boot's app opens on the first validated network.
            if (validated && startPending != null) handler.post(openWhenOnline)
            if (validated == lastValidated) return
            lastValidated = validated
            if (wifiNoInternetTint != null) handler.post { updateWifiLabel() }
        }
    }
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) {
            updateInputsButton()
        }

        override fun onInputRemoved(inputId: String) {
            updateInputsButton()
        }
    }
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { updateWifiLabel() }
        }

        override fun onLost(network: Network) {
            handler.post { updateWifiLabel() }
        }
    }
    private val packageFilter = IntentFilter()
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED == intent.action
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            // In the background this only marks the tiles stale; onResume rebuilds them once.
            if (!tileRefresh.onPackagesChanged()) return
            reloadApps(false)
            appLibrary?.refresh()
        }
    }
    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private val timeFilter = IntentFilter(Intent.ACTION_TIME_CHANGED)
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClockVisibility()
        }
    }
    private val minuteTick = object : Runnable {
        override fun run() {
            updateGreeting()
            sportsPanel.update()
            // The pill follows the network callbacks and each return to Home; only the diya's flame follows the
            // signal level, so only it needs this minute's binder calls (about 7 ms on the UI thread).
            if (festive) updateWifiLabel()
            sky.update()
            wallpaperPlayer.update()
            handler.postDelayed(this, MINUTE_MS)
        }
    }

    private var homeAppKeys: Map<Int, String> = Collections.emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        density = resources.displayMetrics.density
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        val cfg = Config.load(this)
        inputActions = TvInputActions(this, cfg, this::open)
        homeAppKeys = RemoteKeys.parseAppKeys(cfg.appKeys)
        appLibrarySelection = cfg.appLibrarySelection
        libraryOkOpens = cfg.libraryOkOpens
        // restore_key_services: after a force stop, put back the remote's accessibility helpers that Android took
        // off the list (needs WRITE_SECURE_SETTINGS, granted by build-and-install with RESTORE_KEY_SERVICES=1).
        if (cfg.restoreKeyServices) ServiceRepair.restore(this)
        settingsRevision = settingsRevision()
        applyViewInputsHandler(cfg.handleViewInputs)
        settingsKeycodes = cfg.settingsKeycodes
        settingsKeyPanel = cfg.settingsKeyPanel
        COLUMNS = cfg.columns
        animations = cfg.animations
        advancedEffects = cfg.animations && cfg.advancedEffects
        reducedMotion = cfg.reducedMotion
        dockRow = cfg.dockRow
        singleRow = cfg.singleRow
        focusOvershoot = cfg.focusOvershoot
        festival = cfg.festival
        festive = festival.active(LocalDate.now())
        liveTvPkg = cfg.liveTvPackage
        idleClockMs = cfg.homeIdleClockMinutes * MINUTE_MS
        bootToLiveTv = cfg.bootToLiveTv
        openOnStartChoice = cfg.openOnStartChoice
        openOnStartWaitMs = cfg.openOnStartNetworkWait * 1000L
        liveTvInputId = cfg.liveTvInputId
        appLibraryEnabled = cfg.appLibrary
        tileMenuEnabled = cfg.tileMenu
        sourcePanelService = cfg.sourcePanelService
        coocaaSources = cfg.coocaaSources

        root = findViewById(R.id.root)
        root.setBackgroundColor(cfg.backgroundColor)
        // root fills the window with an opaque colour (Config keeps background_color opaque), so the window's own
        // black under it is a second full-screen fill on every frame for nothing. The starting window still uses
        // the theme's background, so launch looks the same.
        window.setBackgroundDrawable(null)
        wallpaperPlayer = WallpaperPlayer(this, root, cfg)
        sky = findViewById(R.id.sky)
        sky.setIntensity(cfg.skyIntensity)
        glass = findViewById(R.id.glass)
        glass.setStyle(cfg.glassStyle)
        if (festive) glass.setAccent(festival.accentColor())
        appGlow = cfg.appGlow
        cinematic = cfg.cinematicHome
        cinematicWhiteFocus = cfg.cinematicWhiteFocus
        shelf = LauncherShelf(this, cfg, density, { adapter }, { rtlAppGrid })
        greeting = findViewById(R.id.greeting)
        clockBlock = findViewById(R.id.leftBlock)
        greetingColor = greeting.currentTextColor
        if (!cfg.showGreeting) greeting.visibility = View.GONE
        if (cfg.headerWhite) {
            // The clock's own white for the date and the line before it, instead of their dimmed whites.
            findViewById<TextView>(R.id.date).setTextColor(Color.WHITE)
            findViewById<View>(R.id.clockDivider).setBackgroundColor(Color.WHITE)
        }
        if (resources.getBoolean(R.bool.rtl_menu_bar)) {
            // Center Hebrew greetings over the numeric time, independent of a visible AM/PM label. The clock's row
            // is watched too: date_below_clock can move the whole row right under a wider date.
            val clock = findViewById<View>(R.id.clock)
            val clockRow = clock.parent as View
            val centerGreeting = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val params = greeting.layoutParams
                if (params.width != clock.width) {
                    params.width = clock.width
                    greeting.layoutParams = params
                }
                greeting.translationX = (clockRow.left + clock.left).toFloat()
            }
            clock.addOnLayoutChangeListener(centerGreeting)
            clockRow.addOnLayoutChangeListener(centerGreeting)
        }
        btnWifi = findViewById(R.id.btnWifi)
        shamsiDate = findViewById(R.id.shamsiDate)
        stats = findViewById(R.id.stats)
        sportsPanel = LauncherSportsPanel(this, handler, cfg, density) { stopped }
        showStats = cfg.showStats
        inputsConnectedOnly = cfg.inputsConnectedOnly
        shamsi = cfg.shamsiDate
        lunarDate = findViewById(R.id.lunarDate)
        lunar = cfg.lunarDate
        lunarOffset = Lunar.offsetFor(cfg.lunarUtcOffset, Locale.getDefault())
        if (lunar) lunarDate.visibility = View.VISIBLE
        greetingName = cfg.greetingName
        if (cfg.dateBelowClock) moveDateBelowClock()
        if (shamsi) {
            shamsiDate.visibility = View.VISIBLE
            val date = findViewById<TextView>(R.id.date)
            date.setTextSize(11f)
            lunarDate.setTextSize(11f)
            date.setPadding(0, (4 * density).toInt(), 0, 0)
        }
        vpnApp = cfg.vpnApp
        vpnPill = cfg.vpnPill && !cfg.topMenu
        btnVpn = findViewById(R.id.btnVpn)
        moduleCards = cfg.moduleCards
        hiddenApps = HashSet(cfg.hiddenApps)
        libraryExcludedApps = HashSet(cfg.libraryExcludedApps)

        grid = findViewById(R.id.grid)
        rtlAppGrid = resources.getBoolean(R.bool.rtl_app_grid)
        // Mirror only the app grid; the header and other locales keep their own layouts.
        // single_row: one span laid out sideways, so the same adapter, glass and dock band serve a scrolling row.
        grid.layoutManager = LauncherGridLayoutManager(
            this, COLUMNS, singleRow, rtlAppGrid, dockRow, animations, reducedMotion,
        )
        (grid.layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (!singleRow && this@MainActivity::adapter.isInitialized && adapter.isLibraryAt(position)) COLUMNS else 1
            }
        }
        grid.setHasFixedSize(true)
        grid.itemAnimator = null
        // Tiles that scroll out while one is carried come back from the view cache without a rebind; set their
        // dimming again whenever one attaches, so none stays dim after the drop.
        grid.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(v: View) {
                v.alpha = reorder.tileAlpha(grid.getChildAdapterPosition(v))
            }

            override fun onChildViewDetachedFromWindow(v: View) {
            }
        })
        if (dockRow) grid.addItemDecoration(DockDecoration(COLUMNS, density, cinematic, cfg.alwaysShowNames, cfg.dockOpacity, cfg.dockGlass, cfg.dockEvenPadding, cfg.dockBand,
                singleRow, cfg.dockBandAll))
        if (cfg.fadeRows) TopFade.attach(grid, 48 * density)
        // Reduced motion: the tiles are simply there, with no entrance (reloadApps skips it there too).
        if (animations && !reducedMotion) {
            grid.layoutAnimation = LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f)
        }
        adapter = AppAdapter(this, this, COLUMNS, cfg)
        dockApps = cfg.dockApps
        fillDockLeavesSlots = !cfg.fillDock
        val savedOrder = Order.load(Order.file(this))
        adapter.setSavedOrder(Order.ranks(savedOrder, dockApps), Order.docked(savedOrder, dockApps))
        grid.adapter = adapter

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        val rowsBelowDock = cfg.rowsBelowDock
        grid.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val h = b - t
            // Item height follows the tile size, which follows the columns and the rows that must fit (AppAdapter.tileWidthPx).
            val inner = (r - l) - grid.paddingLeft - grid.paddingRight
            val tileW = AppAdapter.tileWidthPx(inner, h, COLUMNS, rowsBelowDock, cfg.dockTitle, cfg.dockRow, density)
            val itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tileW), density)
            val dockRow = itemH + (AppAdapter.DOCK_EXTRA_DP * density).toInt()   // item height plus DockDecoration offsets
            val gridRows = itemH * rowsBelowDock
            // Keep the hero below the header when the library is enabled on a short screen,
            // even when the theme requests an extra visible row below the dock.
            val shelfClearance = if (cfg.appLibrary) shelf.measuredHeight + (40 * density).toInt() else 0
            var want = Math.max(shelfClearance, h - dockRow - gridRows - (AppAdapter.BOTTOM_ROOM_DP * density).toInt())
            // Where the rows cannot fit even at the smallest tile (3 rows with the title on), the title keeps its
            // room above the dock and the last rows run off the bottom instead of the title drawing over the clock.
            if (this@MainActivity.dockRow && cfg.dockTitle && rowsBelowDock > 0) want = Math.max(want, (AppAdapter.TITLE_ROOM_DP * density).toInt())
            // No dock to push down: the grid starts at the top, with room for the first row's zoom and glass.
            if (!this@MainActivity.dockRow) want = (AppAdapter.TOP_ROOM_DP * density).toInt()
            // A single row never shows the names under its tiles (the title above the row names the app), so the
            // 7dp gap and 22dp label item_app.xml keeps for them would only lift the row: sit it that much lower.
            if (singleRow && !cfg.alwaysShowNames) want += (29 * density).toInt()
            if (grid.paddingTop != want) {
                grid.setPadding(grid.paddingLeft, want, grid.paddingRight, grid.paddingBottom)
                // A vertical grid parks the dock on the new padding line by scrolling (onTileFocused); a sideways
                // row cannot scroll vertically, and the layout request made from inside this layout pass is
                // dropped, so lay the row out again on the next frame. The glass and the title were placed for the
                // old position, so fit them again once it has moved.
                if (singleRow) grid.post {
                    grid.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            grid.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1))
                            val vh = grid.findViewHolderForAdapterPosition(pos)
                            val card: View? = vh?.itemView?.findViewById(R.id.card)
                            if (card != null && card.isFocused) onTileFocused(pos, card)
                        }
                    })
                    grid.requestLayout()
                }
            }
            // Cold start or column count > 4: the first focus can run before the grid has its final width or
            // padding, leaving the glass at the XML design size until a key is pressed.
            grid.post(this::refitGlass)
        }

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!glassShown) return
                glassTargetY -= dy.toFloat()
                glassScrollShift -= dy.toFloat()
                glass.translationY = glass.translationY - dy.toFloat()
                // single_row scrolls sideways: the same bookkeeping on the other axis.
                if (dx != 0) {
                    glassTargetX -= dx.toFloat()
                    glassScrollShiftX -= dx.toFloat()
                    glass.translationX = glass.translationX - dx.toFloat()
                }
            }
        })

        sportsPanel.attachToGrid(grid, clockBlock)

        // Liquid feel: soft springs on the highlight's position.
        springX = SpringAnimation(glass, object : FloatPropertyCompat<View>("glassContentX") {
            override fun getValue(v: View): Float {
                return v.translationX - glassScrollShiftX
            }

            override fun setValue(v: View, value: Float) {
                v.translationX = value + glassScrollShiftX
            }
        })
        springY = SpringAnimation(glass, object : FloatPropertyCompat<View>("glassContentY") {
            override fun getValue(v: View): Float {
                return v.translationY - glassScrollShift
            }

            override fun setValue(v: View, value: Float) {
                v.translationY = value + glassScrollShift
            }
        })
        for (s in arrayOf(springX, springY)) {
            if (reducedMotion) {
                Motion.reduce(s)
            } else if (!focusOvershoot) {
                // Critically damped: glides straight onto the tile without swinging past it.
                s.setSpring(SpringForce().setStiffness(if (advancedEffects) 520f else 440f).setDampingRatio(1f))
            } else {
                s.setSpring(SpringForce().setStiffness(if (advancedEffects) 320f else 260f)
                        .setDampingRatio(if (advancedEffects) 0.76f else 0.58f))
            }
        }

        btnInputs = findViewById(R.id.btnInputs)
        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnInputs.setOnClickListener { showInputs() }
        btnWifi.setOnClickListener { openNetworkPill() }
        btnVpn.setOnClickListener { openVpnPill() }
        btnWifi.setOnLongClickListener {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS))
            true
        }
        launcherSettingsButton = cfg.launcherSettingsButton
        if (launcherSettingsButton) {
            (btnSettings as TextView).setText(R.string.launcher_settings)
            (btnSettings as TextView).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_home_settings_pill, 0, 0, 0)
        }
        btnSettings.setOnClickListener {
            if (launcherSettingsButton) startActivityForResult(
                    Intent(this, LauncherSettingsActivity::class.java), LAUNCHER_SETTINGS_REQUEST)
            else open(systemIntent(Settings.ACTION_SETTINGS))
        }
        btnReorder = findViewById(R.id.btnReorder)
        btnReorder.setOnClickListener { reorder.toggleReorder() }
        btnReorder.visibility = if (cfg.reorderButton) View.VISIBLE else View.GONE
        if (!cfg.showNetworkPill) btnWifi.visibility = View.GONE
        showInputsPill = cfg.showInputsPill
        showInputsMenuRow = cfg.showInputsPill
        btnLibraryTop = findViewById(R.id.btnLibraryTop)
        btnLibraryTop.setOnClickListener {
            onLibraryRequested()
            libraryFromTopBar = true   // closing it goes back to this button, not to the pill after the last row
        }
        btnLibraryTop.visibility = if (cfg.libraryButton && cfg.appLibrary) View.VISIBLE else View.GONE
        addTopBarApps(cfg.topBarApps, cfg.launchActions)
        iconOnlyButtons = cfg.iconOnlyButtons
        topMenu = cfg.topMenu
        wifiIconOnly = iconOnlyButtons && cfg.wifiLabelWidth <= 0
        if (cfg.wifiStatusColor) {
            wifiPlainTint = btnWifi.compoundDrawableTintList
            wifiOnlineTint = getColorStateList(R.color.pill_icon_online)
            if (cfg.wifiStatusWarnings) {
                wifiNoInternetTint = getColorStateList(R.color.pill_icon_no_internet)
                wifiOfflineTint = getColorStateList(R.color.pill_icon_offline)
            }
        }
        if (cfg.topMenu) {
            // One gear: Inputs and Wi-Fi move into its dropdown. The Wi-Fi pill stays hidden but keeps its
            // label up to date, which the menu's Wi-Fi row shows.
            showInputsPill = false
            btnInputs.visibility = View.GONE
            btnWifi.visibility = View.GONE
            (btnSettings as TextView).setText(R.string.settings)
            (btnSettings as TextView).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0)
            if (!iconOnlyButtons) makeIconOnly(btnSettings as TextView)
            btnSettings.setOnClickListener { showTopMenu() }
            // The lone gear sits at the header's middle, not on its bottom line like the row of pills.
            val topButtons = findViewById<LinearLayout>(R.id.topButtons)
            val tlp = topButtons.layoutParams as RelativeLayout.LayoutParams
            tlp.addRule(RelativeLayout.ALIGN_TOP, R.id.leftBlock)
            tlp.bottomMargin = 0
            topButtons.layoutParams = tlp
            topButtons.gravity = Gravity.CENTER_VERTICAL
        }
        if (iconOnlyButtons) {
            if (cfg.wifiLabelWidth > 0) btnWifi.maxWidth = Math.round(cfg.wifiLabelWidth * density)
            else makeIconOnly(btnWifi)
            makeIconOnly(btnSettings as TextView)
        }
        if (cfg.iconOnlyInputs) makeIconOnly(btnInputs as TextView)
        iconOnlyVpn = vpnPill && cfg.iconOnlyVpn
        if (iconOnlyVpn) {
            vpnPlainBackground = btnVpn.background.constantState
            vpnPlainInk = btnVpn.compoundDrawableTintList
            makeIconOnly(btnVpn)
        }
        btnSettings.setOnLongClickListener {
            showSettingsMenu()
            true
        }

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        val rtlMenuBar = resources.getBoolean(R.bool.rtl_menu_bar)
        val pillKeys = View.OnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                focusTile(if (singleRow) lastFocused else lastFocused % COLUMNS)
                true
            } else {
                val firstButton = if (btnInputs.visibility == View.VISIBLE) R.id.btnInputs else firstTopButton().id
                val leftButton = if (rtlMenuBar) R.id.btnSettings else firstButton
                val rightButton = if (rtlMenuBar) firstButton else R.id.btnSettings
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) v.id == leftButton
                else keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.id == rightButton
            }
        }
        btnInputs.setOnKeyListener(pillKeys)
        btnWifi.setOnKeyListener(pillKeys)
        btnVpn.setOnKeyListener(pillKeys)
        btnSettings.setOnKeyListener(pillKeys)
        btnReorder.setOnKeyListener(pillKeys)
        btnLibraryTop.setOnKeyListener(pillKeys)
        for (button in topAppButtons) button.setOnKeyListener(pillKeys)
        if (advancedEffects) {
            val pills: MutableList<View> = ArrayList(Arrays.asList(btnInputs, btnWifi, btnVpn, btnReorder, btnLibraryTop, btnSettings))
            pills.addAll(topAppButtons)
            for (button in pills) {
                button.setOnFocusChangeListener { view, focused ->
                    view.animate()
                            .scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f)
                            .setDuration(if (reducedMotion) Motion.REDUCED_MS.toLong() else 160L).start()
                }
            }
        }
        if (festive) applyFestiveSkin(btnInputs as TextView, btnSettings as TextView)

        // Hide the glass when focus leaves the tiles.
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newF ->
            val onTile = newF != null && newF.id == R.id.card
            if (!onTile) hideGlass()
        }

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packageFilter.addDataScheme("package")
        timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        // Registered for the activity's whole life, not only while it is started: an app installed or updated while
        // another app is in front must still mark the tiles stale, or Home would have to rebuild on every return.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, packageFilter)
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        tim?.registerCallback(inputCallback, handler)
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback)
            cm.registerNetworkCallback(NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback)
        }
        // Not after recreate(): the launch intent survives it and would open the settings again.
        if (savedInstanceState == null && !openLauncherSettingsIfAsked(intent)) openInputsIfAsked(intent)
    }

    /**
     * handle_view_inputs: switch InputsRequestActivity, the VIEW_INPUTS handler, on or off. It is disabled in the
     * manifest, so a TV with its own input picker never sees it; only the sets that need it turn it on. Written
     * only when the state differs, so an ordinary start does not touch the package manager.
     */
    private fun applyViewInputsHandler(on: Boolean) {
        val handler = ComponentName(this, InputsRequestActivity::class.java)
        val pm = packageManager
        val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (pm.getComponentEnabledSetting(handler) != want) {
            pm.setComponentEnabledSetting(handler, want, PackageManager.DONT_KILL_APP)
        }
    }

    /**
     * VIEW_INPUTS, forwarded by InputsRequestActivity while handle_view_inputs is on: open the Inputs menu once
     * the pill is on screen. The intent is replaced so a later recreate() does not
     * open it again. Nothing to do when the TV has no inputs to list (the pill is hidden then).
     * top_menu hides the pill, so the list hangs under the gear there, as in showInputs().
     */
    private fun openInputsIfAsked(intent: Intent?): Boolean {
        if (intent == null || ACTION_VIEW_INPUTS != intent.action) return false
        setIntent(Intent(Intent.ACTION_MAIN))
        val anchor = if (topMenu) findViewById<View>(R.id.btnSettings) else btnInputs
        if (anchor.visibility != View.VISIBLE) return true
        if (anchor.isLaidOut) {
            // Android 9 delivers a new intent to a paused activity with a brief resume and pause around it, and
            // onPause() closes popups, so open the menu once that has run (HiSmartTV A4, Android TV 9).
            anchor.post {
                if (!isFinishing && anchor.visibility == View.VISIBLE) {
                    anchor.requestFocus()
                    showInputs()
                }
            }
            return true
        }
        // Cold start: the popup anchors to the pill (or the gear), so wait for the first layout.
        anchor.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                anchor.viewTreeObserver.removeOnGlobalLayoutListener(this)
                anchor.post {
                    if (!isFinishing && anchor.visibility == View.VISIBLE) {
                        anchor.requestFocus()
                        showInputs()
                    }
                }
            }
        })
        return true
    }

    private fun openLauncherSettingsIfAsked(intent: Intent?): Boolean {
        if (intent == null || ACTION_LAUNCHER_SETTINGS != intent.action) return false
        setIntent(Intent(Intent.ACTION_MAIN))
        startActivityForResult(Intent(this, LauncherSettingsActivity::class.java), LAUNCHER_SETTINGS_REQUEST)
        return true
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (openLauncherSettingsIfAsked(intent)) return
        if (openInputsIfAsked(intent)) return
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        if (!stopped) sportsPanel.read()
        grid.scrollToPosition(0)
        lastFocused = 0
        lastFocusedKey = if (adapter.itemCount > 0) adapter.keyAt(0) else null
        if (stopped) {
            tileRefresh.onHomeAgain()
        } else {
            // Still on screen (the quick panel was over it, or Home was already in front): the tiles are current,
            // so only go back to the first one; rebuilding would replay the entrance animation for nothing.
            grid.post { focusTile(0) }
        }
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeReceiver, timeFilter)
        }
        updateClockVisibility()
        tileRefresh.onStart()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // A festival that started or ended while Home was in the background: rebuild with or without it.
        if (festive != festival.active(LocalDate.now())) {
            recreate()
            return
        }
        // Something changed in Launcher settings: rebuild with it, however the settings were left. Only Back
        // delivered their RESULT_OK; Home (the remote's button) clears them off this task and the result comes
        // back cancelled, so a new wallpaper or layout waited for the next process start.
        if (Config.settingsChanged(settingsRevision, settingsRevision())) {
            recreate()
            return
        }
        restartIdleClock()
        moduleCardMenus.resumePrompts()
        if (grid.adapter == null) grid.adapter = adapter   // dropped by onTrimMemory
        when (tileRefresh.onResume(packagesChangedSinceRebuild())) {
            TileRefresh.Action.REBUILD -> {
                tilesReady = false
                reloadApps(true)
            }
            TileRefresh.Action.REFOCUS -> {
                // The same tiles: only the focus goes back, as after a rebuild, without replaying the entrance.
                tilesReady = false
                reloadApps(true, false, false)
            }
            TileRefresh.Action.NOTHING -> {}
        }
        if (openLiveTvAfterBoot()) return
        updateGreeting()
        sportsPanel.update()
        // The network and Inputs pills keep their state while Home is in the background, and callbacks update them
        // when the network or the inputs change, so this refresh only catches anything missed. Its binder calls
        // (about 7 ms on a TCL C755, most of it WifiManager.getConnectionInfo) would hold up the first frame back
        // on Home, so after the first resume it runs once that frame is drawn.
        if (pillsShown) {
            refreshPillsAfterNextDraw()
        } else {
            updateWifiLabel()
            updateInputsButton()
            pillsShown = true
        }
        if (showStats) launcherStats.start()
        sportsPanel.start()
        sky.update()
        wallpaperPlayer.start()
        handler.postDelayed(minuteTick, MINUTE_MS)
        if (festive && diya != null) {
            diya!!.start()
            val today = LocalDate.now()
            if (!reducedMotion && today != lastPetalShower) {
                lastPetalShower = today
                handler.postDelayed({ playPetalShower() }, 450)
            }
        }
    }

    /** Launcher settings' change count (Config.SETTINGS_REVISION_KEY); onResume rebuilds Home when it moved. */
    private fun settingsRevision(): Long {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong(Config.SETTINGS_REVISION_KEY, 0)
    }

    /**
     * Only "Reorder tiles" in Launcher settings still answers with a result: Home rebuilds (the saved choices are
     * reloaded) and then enters reorder mode. Every other change is picked up in onResume through the change count.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == RESULT_OK
                && data != null && data.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) {
            intent.putExtra(LauncherSettingsActivity.EXTRA_REORDER, true)
            // The old activity must not consume the pending action, or rebuild a second time in onResume.
            settingsRecreationPending = true
            settingsRevision = settingsRevision()
            recreate()
        }
    }

    private fun startPendingSettingsReorder() {
        if (settingsRecreationPending || !tilesReady || !hasWindowFocus() || isFinishing
                || !intent.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) return
        intent.removeExtra(LauncherSettingsActivity.EXTRA_REORDER)
        reorder.toggleReorder()
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private fun reloadApps(animate: Boolean) {
        reloadApps(animate, true)
    }

    /** @param rebuild false keeps the tiles as they are and only puts focus back (TileRefresh.Action.REFOCUS). */
    private fun reloadApps(animate: Boolean, rebuild: Boolean) {
        reloadApps(animate, rebuild, true)
    }

    /**
     * @param entrance false puts focus back as an animated reload would, without replaying the tiles' entrance:
     *                 on a return to the same tiles it would only mark a change that didn't happen.
     */
    private fun reloadApps(animate: Boolean, rebuild: Boolean, entrance: Boolean) {
        cancelPendingFocusScroll()
        val footer = grid.findViewHolderForAdapterPosition(adapter.appCount())
        val restoreLibrary = adapter.isLibraryAt(adapter.appCount()) && footer != null
                && footer.itemView.findViewById<View>(R.id.btnLibrary) != null
                && footer.itemView.findViewById<View>(R.id.btnLibrary).hasFocus()
        // Capture the app before rebinding can move focus and overwrite its key.
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        val restoreFocus = appLibrary == null && (animate || grid.hasFocus())
        if (restoreFocus) {
            glassShown = false
            glass.alpha = 0f
            shelf.resetForReload()
        }
        if (rebuild) reloadTiles()
        if (animate && entrance && animations && !reducedMotion) {
            grid.scheduleLayoutAnimation()
            // A rebuild redraws the grid anyway; without one the entrance needs a draw to start.
            if (!rebuild) grid.invalidate()
        }

        if (restoreFocus) {
            // Keep a focused footer at the end even when the app count changes.
            if (restoreLibrary) grid.post { focusLibrary() }
            else restoreFocus()
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            val pos = adapter.positionOf(returnKey)
            lastFocused = Math.max(0, Math.min(if (pos >= 0) pos else returnSlot, adapter.appCount() - 1))
            lastFocusedKey = adapter.keyAt(lastFocused)
            if (pos < 0) shelf.hideHero()
            // A newly installed app may have brought a card with it.
            tilesReady = true
            startPendingSettingsReorder()
            moduleCardMenus.showPrompt()
        }
    }

    /** True when PackageManager reports apps changed since the last rebuild (one binder call). */
    private fun packagesChangedSinceRebuild(): Boolean {
        val changed = packageManager.getChangedPackages(packageSequence)
        return changed != null && changed.packageNames.isNotEmpty()
    }

    private fun currentPackageSequence(since: Int): Int {
        val changed = packageManager.getChangedPackages(since)
        return changed?.sequenceNumber ?: since
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private fun reloadTiles() {
        if (reorder.reordering) reorder.endReorder()   // an app installed or removed while reordering: keep the order so far
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        val none: List<Cards.Card> = Collections.emptyList()
        // An updated app may bring new artwork; the backdrop is cheap to make again.
        shelf.clearArtwork()
        // One LEANBACK query for both the tiles and the card checks.
        val leanback = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        cards = if (moduleCards) CardFeature.run({ Cards.discover(this, hiddenApps,
                { Cards.load(Cards.storeFile(this)) }, leanback) }, none) else none
        adapter.reload(if (cards.isEmpty()) none else CardFeature.run({ Cards.shown(cards) }, none), leanback)
        lastFocusedKey = returnKey
        lastFocused = returnSlot
        packageSequence = currentPackageSequence(packageSequence)
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private fun restoreFocus() {
        val returnToLibrary = libraryFocusPending
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        grid.post {
            val pos = adapter.positionOf(returnKey)
            if (returnToLibrary || libraryFocusPending) focusLibrary()
            else focusTile(if (pos >= 0) pos else returnSlot)
            tilesReady = true
            startPendingSettingsReorder()
            moduleCardMenus.showPrompt()
        }
    }

    /** Any key on Home restarts the countdown to the idle clock. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        restartIdleClock()
    }

    private fun restartIdleClock() {
        root.removeCallbacks(showIdleClock)
        if (resumed && idleClockMs > 0) root.postDelayed(showIdleClock, idleClockMs)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        root.removeCallbacks(showIdleClock)
        reorder.endReorder()   // Home or another app while reordering keeps the order so far
        wallpaperPlayer.stop()
        shelf.stopAmbientAnimation()
        shelf.cancelAnimations()
        handler.removeCallbacks(minuteTick)
        handler.removeCallbacks(openWhenOnline)
        startPending = null   // leaving Home before the network came back: the boot's app stays closed
        launcherStats.stop()
        sportsPanel.stop()
        diya?.stop()
        cancelPendingFocusScroll()
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        moduleCardMenus.dismissPrompt()
        appLibrary?.close()
        openPopup?.dismiss()
    }

    override fun onStop() {
        super.onStop()
        started = false
        unregisterReceiver(timeReceiver)
        handler.removeCallbacks(clockCheck)
        stopped = true
        tileRefresh.onStop()
    }

    /**
     * Another app needs the memory while Home is in the background: let go of every tile's artwork, and
     * rebuild it when Home comes back. Not at UI_HIDDEN, the ordinary trip to an app, where keeping the
     * tiles is what makes the return instant.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_LOW and CRITICAL also arrive while Home is visible when an accessibility service keeps the
        // process in the foreground; never blank the screen the owner is looking at.
        if (!MemoryTrimPolicy.shouldRelease(level, started)) return
        adapter.clear()
        // Detach the adapter too: with Home stopped no layout pass runs, so the tile views (and the bitmaps
        // they hold) would stay attached until Home comes back. setAdapter(null) recycles them now.
        grid.adapter = null
        grid.recycledViewPool.clear()
        shelf.clearArtwork()
        wallpaperPlayer.releaseForMemoryPressure()
        tileRefresh.invalidate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            startPendingSettingsReorder()
            if (libraryFocusPending && appLibrary == null) focusLibrary()
            moduleCardMenus.showPrompt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sportsPanel.shutdown()
        unregisterReceiver(packageReceiver)
        wallpaperPlayer.close()
        val cm = getSystemService(ConnectivityManager::class.java)
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        tim?.unregisterCallback(inputCallback)
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback)
            cm.unregisterNetworkCallback(vpnCallback)
        }
    }

    /**
     * A Settings button sent as a keycode (settings_key_keycodes, e.g. 83 on TCL Google TV remotes) opens the same
     * thing HomeKeyService would. While that service runs it consumes the key first; without it (not enabled,
     * or switched off by a force-stop) the stock launcher that used to handle the key is gone, and the button did
     * nothing even on Home.
     */
    private fun handleSettingsKey(event: KeyEvent): Boolean {
        for (code in settingsKeycodes) {
            if (event.keyCode != code) continue
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                open(if (settingsKeyPanel) Intent(this, QuickSettingsActivity::class.java)
                        else systemIntent(Settings.ACTION_SETTINGS))
            }
            return true
        }
        return false
    }

    // ---- reorder ---------------------------------------------------------------------------------

    /**
     * Reorder mode (top-bar button). While it is on, every key comes here first:
     * - nothing carried: arrows move focus as usual; OK on a tile picks it up instead of opening the app;
     *   Back ends the mode and saves the order.
     * - a tile carried: arrows move it; Back (or OK) puts it down and the mode stays on for the next one.
     * Other keys (volume, Home) pass through; leaving the launcher ends the mode and saves.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Vendor keys (TCL Netflix button, 4062) never reach the key services, only the focused window.
        if (event.keyCode >= 1000 && RemoteKeys.handle(this, event, homeAppKeys[event.keyCode], null)) return true
        if (handleSettingsKey(event)) return true
        if (tileMenuEnabled && !reorder.reordering && openPopup == null && !moduleCardMenus.promptShowing
                && event.keyCode == KeyEvent.KEYCODE_MENU) {
            val position = reorder.focusedTilePosition()
            if (position >= 0 && position < adapter.appCount()
                    && packageName != adapter.packageAt(position)) {
                if (RemoteKeys.menuReleased(event.action, event.flags)) {
                    onTileLongPress(position, currentFocus!!)
                }
                return true
            }
        }
        return reorder.dispatch(event) { super.dispatchKeyEvent(event) }
    }

    override fun onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    override fun onLibraryRequested() {
        appLibrary?.close()
        openPopup?.dismiss()
        libraryFocusPending = false
        libraryFromTopBar = false
        libraryFromVpnPill = false
        val library = AppLibraryDialog(this, animations, reducedMotion, appLibrarySelection, libraryOkOpens, adapter::containsPackage, { reloadApps(false) }, {
            appLibrary = null
            openPopup = null
            if (libraryFromTopBar) {
                libraryFromTopBar = false
                btnLibraryTop.requestFocus()
            } else {
                val fromVpnPill = libraryFromVpnPill
                libraryFromVpnPill = false
                // The VPN pill can hide while the library is open (its VPN dropped): then focus goes as from the grid.
                if (fromVpnPill && btnVpn.visibility == View.VISIBLE) {
                    btnVpn.requestFocus()
                } else {
                    libraryFocusPending = true
                    grid.post {
                        if (libraryFocusPending && appLibrary == null && hasWindowFocus() && !isFinishing) focusLibrary()
                    }
                }
            }
        }, libraryExcludedApps)
        appLibrary = library
        openPopup = library.popup
        library.show()
    }

    override fun onTileFocused(position: Int, card: View) {
        lastFocused = position
        lastFocusedKey = adapter.keyAt(position)
        // Focus restored before the first layout (cold start): the tile has no size yet, and the glass
        // would be sized to 2*pad. Run again once the tile is laid out.
        if (card.width == 0) {
            card.post {
                if (card.isFocused && card.width > 0) onTileFocused(position, card)
            }
            return
        }
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        val r = tileRect
        r.set(0, 0, card.width, card.height)
        root.offsetDescendantRectToMyCoords(card, r)
        val pad = 6f * density        // matches GlassView's glow inset
        val scale = adapter.focusScale()
        val w = card.width * scale + 2 * pad
        val h = card.height * scale + 2 * pad
        val x = r.exactCenterX() - w / 2f
        val y = r.exactCenterY() - h / 2f - (if (advancedEffects) 4f * density else 0f)

        val lp = glass.layoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt() || card.width != glassCardW) {
            lp.width = w.toInt()
            lp.height = h.toInt()
            glass.layoutParams = lp
            // GlassView draws its slab inset by pad, i.e. exactly over the zoomed tile: same corners as the tile.
            glass.setRadius(AppAdapter.cornerRadius(card.width, density) * scale)
        }
        glassCardW = card.width
        glassTargetX = x
        glassTargetY = y
        // Cinematic shelf: the highlight glows in the dock app's colour, plain white in the grid (and on the dock with
        // cinematic_white_focus). app_glow: every tile. In a single row every tile is the dock's kind.
        val coloured = (appGlow || cinematic && !cinematicWhiteFocus && (singleRow || position < COLUMNS)) && !festive
        val accent = if (coloured) adapter.ambientColorAt(position) else 0
        if ((cinematic || appGlow) && !festive && accent != glassAccent) {
            glassAccent = accent
            glass.setAccent(accent)
        }
        if (!glassShown) {
            springX.cancel()
            springY.cancel()
            glass.translationX = x
            glass.translationY = y
            if (animations && !reducedMotion) glass.animate().alpha(1f).setDuration(180).start()
            else glass.alpha = 1f
            glassShown = true
        } else if (!animations) {
            springX.cancel()
            springY.cancel()
            glass.translationX = x
            glass.translationY = y
        } else {
            springX.animateToFinalPosition(x - glassScrollShiftX)
            springY.animateToFinalPosition(y - glassScrollShift)
        }
        val item = card.parent as View
        if (dockRow && (singleRow || position < COLUMNS)) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app. In a single row
            // every tile is in the dock's row, which never scrolls vertically.
            val decoratedTop = item.top - (6 * density).toInt()
            val dy = decoratedTop - grid.paddingTop
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            val scroll = !singleRow && Math.abs(dy) > (8 * density).toInt()
            if (scroll) {
                if (!animations || reducedMotion) grid.scrollBy(0, dy)
                else grid.smoothScrollBy(0, dy)
            }
            shelf.showHero(position, if (scroll) (r.top - dy).toFloat() else r.top.toFloat())
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            shelf.showGlowOnly(position)
            // Scroll just enough so the whole glass (not only the tile) is visible.
            revealRect.set(0, -pad.toInt() * 2, item.width, item.height + pad.toInt())
            grid.requestChildRectangleOnScreen(item, revealRect, reducedMotion)
        }
    }

    /** Refits the glass to the focused holder once the grid layout and tile dimensions settle. */
    private fun refitGlass() {
        if (isDestroyed || adapter.appCount() == 0 || libraryFocusPending) return
        val pos = Math.max(0, Math.min(lastFocused, adapter.appCount() - 1))
        val vh = grid.findViewHolderForAdapterPosition(pos)
        val card: View? = vh?.itemView?.findViewById(R.id.card)
        if (card == null || card.width <= 0 || !card.isFocused) return
        val pad = 6f * density
        val scale = adapter.focusScale()
        val w = (card.width * scale + 2 * pad).toInt()
        val h = (card.height * scale + 2 * pad).toInt()
        if (!glassShown || card.width != glassCardW || glass.width != w || glass.height != h) {
            onTileFocused(pos, card)
        }
    }

    private fun hideGlass() {
        if (!glassShown) return
        glassShown = false
        glass.animate().cancel()
        if (animations && !reducedMotion) glass.animate().alpha(0f).setDuration(160).start()
        else glass.alpha = 0f
    }

    // ---- navigation ------------------------------------------------------------------------------

    override fun onTileVerticalKey(position: Int, keyCode: Int): Boolean {
        val count = adapter.appCount()
        if (singleRow) {
            // One row: Up leaves for the top bar, Down has nowhere to go, and Left/Right walk the whole row,
            // stopping at its two ends (the default focus search scrolls the row to the next tile).
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return false
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true
            val forward = if (rtlAppGrid) keyCode == KeyEvent.KEYCODE_DPAD_LEFT else keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            val last = adapter.focusablePosition(count - 1)
            if (forward && position >= last) {
                if (adapter.isLibraryAt(count)) focusLibrary()
                return true
            }
            return !forward && position == 0
        }
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            val forward = if (rtlAppGrid) keyCode == KeyEvent.KEYCODE_DPAD_LEFT else keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            if (forward && position == adapter.focusablePosition(count - 1) && adapter.isLibraryAt(count)) {
                focusLibrary()
                return true
            }
            // In the dock, the last app is the row's end: the empty slots after it take no focus.
            val rowCount = if (position < COLUMNS) Math.min(count, adapter.dockFilled()) else count
            return GridNavigation.atHorizontalEdge(position, rowCount, COLUMNS,
                    rtlAppGrid, keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
        }
        val target: Int
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false          // dock row: let focus move to the top bar
            target = position - COLUMNS
        } else {
            val lastRowStart = ((count - 1) / COLUMNS) * COLUMNS
            if (position >= lastRowStart) {
                if (adapter.isLibraryAt(count)) focusLibrary()
                return true
            }
            target = Math.min(position + COLUMNS, count - 1)
        }
        focusTile(target)
        // The default focus search clicks for Left/Right; Up/Down are handled here, so click for them too.
        grid.playSoundEffect(if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
                SoundEffectConstants.NAVIGATION_UP else SoundEffectConstants.NAVIGATION_DOWN)
        return true
    }

    override fun onLibraryFocused() {
        libraryFocusPending = false
        hideGlass()
        shelf.hideHero()
    }

    /**
     * date_below_clock: the date column moves to its own line under the clock, in the vertical LinearLayout that holds
     * the clock's row, and the line between them goes. In Hebrew (rtl_menu_bar) the date and the clock's row sit right.
     */
    private fun moveDateBelowClock() {
        val column = findViewById<View>(R.id.date).parent as View
        val clockRow = findViewById<View>(R.id.clock).parent as View
        val clockColumn = clockRow.parent as LinearLayout
        (column.parent as ViewGroup).removeView(column)
        findViewById<View>(R.id.clockDivider).visibility = View.GONE
        // Its own width, not the column's: in Hebrew the column is only as wide as the clock.
        val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Math.round(4 * density)
        if (resources.getBoolean(R.bool.rtl_menu_bar)) {
            // Date and clock flush right; the greeting stays left and follows the clock by translation.
            lp.gravity = Gravity.RIGHT
            (clockRow.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.RIGHT
        }
        clockColumn.addView(column, clockColumn.indexOfChild(clockRow) + 1, lp)
        // The column now wraps its texts, so they must wrap too: a match_parent TextView does not ask for a new
        // layout when its text gets longer, and the column kept the width of the day before ("TUESDAY 27 OCTOB…").
        val texts = column as ViewGroup
        for (i in 0 until texts.childCount) {
            val text = texts.getChildAt(i)
            val textLp = text.layoutParams as LinearLayout.LayoutParams
            textLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            // Keep each line where its own gravity put it (right in Hebrew) now that it no longer fills the column.
            textLp.gravity = (text as TextView).gravity and Gravity.HORIZONTAL_GRAVITY_MASK
            text.layoutParams = textLp
        }
    }

    /** The first top-bar button after Inputs that is shown, in bar order (top_bar_apps, the network pill, ...); Settings is always there. */
    private fun firstTopButton(): View {
        val bar = findViewById<ViewGroup>(R.id.topButtons)
        for (i in 0 until bar.childCount) {
            val button = bar.getChildAt(i)
            if (button !== btnInputs && button.visibility == View.VISIBLE) return button
        }
        return findViewById(R.id.btnSettings)
    }

    /** top_bar_apps buttons, in bar order. */
    private val topAppButtons: MutableList<View> = ArrayList()

    /**
     * Puts each top_bar_apps app in the top bar as a round pill with the app's own logo, left of the network pill.
     * The logo keeps its colours: the pill style's tint, which turns the other icons white, is cleared.
     */
    private fun addTopBarApps(packages: List<String>, launchActions: Map<String, String>) {
        val bar = findViewById<ViewGroup>(R.id.topButtons)
        val pm = packageManager
        val size = Math.round(22 * density)
        for (pkg in packages) {
            var launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg)
            if (launch == null) continue   // not installed, or disabled
            // Use the same configured launch_actions as tiles; retain the normal entry if unavailable.
            launch = LaunchActions.select(pkg, launchActions, launch) { targetPackage, action ->
                val custom = LaunchActions.intent(targetPackage, action)
                if (pm.resolveActivity(custom, 0) != null) custom else null
            }
            val logo: Drawable
            val label: CharSequence
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                logo = info.loadIcon(pm)
                label = info.loadLabel(pm)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
            val button = layoutInflater.inflate(R.layout.top_app_button, bar, false) as TextView
            // pillKeys finds the bar's ends by id: without one of its own, every app button would be an end.
            button.id = View.generateViewId()
            logo.setBounds(0, 0, size, size)
            button.setCompoundDrawablesRelative(logo, null, null, null)
            button.compoundDrawableTintList = null
            button.contentDescription = label
            val target = launch
            button.setOnClickListener { open(Intent(target)) }
            // After the network pill and the VPN pill; the Hebrew bar runs the other way, so before both there.
            bar.addView(button, bar.indexOfChild(if (resources.getBoolean(R.bool.rtl_menu_bar)) btnVpn else btnWifi))
            topAppButtons.add(button)
        }
    }

    override fun onLibraryUp() {
        if (adapter.appCount() == 0) { firstTopButton().requestFocus(); return }
        val lastRow = ((adapter.appCount() - 1) / COLUMNS) * COLUMNS
        focusTile(Math.min(lastRow + lastFocused % COLUMNS, adapter.appCount() - 1))
    }

    private fun focusLibrary() {
        cancelPendingFocusScroll()
        val position = adapter.appCount()
        if (!adapter.isLibraryAt(position)) return
        val holder = grid.findViewHolderForAdapterPosition(position)
        if (holder != null) {
            val button = holder.itemView.findViewById<View>(R.id.btnLibrary)
            val visible = Rect()
            if (button.getGlobalVisibleRect(visible) && visible.height() >= button.height) {
                if (button.requestFocus()) libraryFocusPending = false
                return
            }
        }
        if (!animations) {
            grid.scrollToPosition(position)
            grid.post {
                if (appLibrary == null && hasWindowFocus()) {
                    val footer = grid.findViewHolderForAdapterPosition(adapter.appCount())
                    if (footer != null && adapter.isLibraryAt(adapter.appCount())
                            && footer.itemView.findViewById<View>(R.id.btnLibrary).requestFocus()) libraryFocusPending = false
                }
            }
            return
        }
        pendingFocusScroll = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state != RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                if (pendingFocusScroll != this) return
                pendingFocusScroll = null
                val footer = rv.findViewHolderForAdapterPosition(position)
                if (footer != null && footer.itemView.findViewById<View>(R.id.btnLibrary).requestFocus())
                    libraryFocusPending = false
            }
        }
        grid.addOnScrollListener(pendingFocusScroll!!)
        grid.smoothScrollToPosition(position)
    }

    private fun cancelPendingFocusScroll() {
        if (pendingFocusScroll != null) {
            grid.removeOnScrollListener(pendingFocusScroll!!)
            pendingFocusScroll = null
            grid.stopScroll()
        }
    }

    private fun focusTile(position: Int) {
        libraryFocusPending = false
        cancelPendingFocusScroll()
        if (adapter.appCount() == 0) {
            if (adapter.isLibraryAt(0)) focusLibrary()
            else firstTopButton().requestFocus()
            return
        }
        val pos = adapter.focusablePosition(Math.max(0, Math.min(position, adapter.appCount() - 1)))
        val vh = grid.findViewHolderForAdapterPosition(pos)
        if (vh != null) {
            val card = vh.itemView.findViewById<View>(R.id.card)
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused) onTileFocused(pos, card)
            else card.requestFocus()
            return
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        if (!animations) {
            grid.scrollToPosition(pos)
            grid.post {
                val h = grid.findViewHolderForAdapterPosition(pos)
                if (h != null) h.itemView.findViewById<View>(R.id.card).requestFocus()
            }
            return
        }
        pendingFocusScroll = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                rv.removeOnScrollListener(this)
                if (pendingFocusScroll != this) return
                pendingFocusScroll = null
                val h = rv.findViewHolderForAdapterPosition(pos)
                val target = if (h != null) h.itemView.findViewById<View>(R.id.card) else rv.getChildAt(0)
                target?.requestFocus()
            }
        }
        grid.addOnScrollListener(pendingFocusScroll!!)
        grid.smoothScrollToPosition(pos)
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /** Some TVs (Coocaa OS) set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private val clockCheck = Runnable { updateClockVisibility() }

    private fun updateClockVisibility() {
        val valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS
        clockBlock.visibility = if (valid) View.VISIBLE else View.INVISIBLE
        handler.removeCallbacks(clockCheck)
        if (valid) {
            updateGreeting()
        } else {
            handler.postDelayed(clockCheck, 1_000L)
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var res: Int
        // Locale-specific day parts (app language on Android 13+).
        // Indonesian: Pagi 00-10, Siang 10-15, Sore 15-18, Malam 18-24.
        // Spain (es-ES) and Catalan: "Buenas tardes" into the evening; "Buenas noches" from 21.
        // Latin American Spanish (es-MX, es-AR, …) keeps the default hours: evening strings are also
        // «Buenas noches», and there «Buenas noches» from ~17 matches local use better than Spain's 21.
        val locale = resources.configuration.locales[0]
        val lang = locale.language
        val country = locale.country
        if ("in" == lang || "id" == lang) {
            if (hour < 10) res = R.string.good_morning
            else if (hour < 15) res = R.string.good_afternoon
            else if (hour < 18) res = R.string.good_evening
            else res = R.string.good_night
        } else if (("es" == lang && "ES" == country) || "ca" == lang) {
            if (hour < 6) res = R.string.good_night
            else if (hour < 12) res = R.string.good_morning
            else if (hour < 21) res = R.string.good_afternoon
            else res = R.string.good_night
        } else if ("da" == lang) {
            // Danish day parts: "God morgen" until 11, then "God formiddag" to noon.
            if (hour < 5) res = R.string.good_night
            else if (hour < 11) res = R.string.good_morning
            else if (hour < 12) res = R.string.good_forenoon
            else if (hour < 18) res = R.string.good_afternoon
            else if (hour < 22) res = R.string.good_evening
            else res = R.string.good_night
        } else {
            if (hour < 5) res = R.string.good_night
            else if (hour < 12) res = R.string.good_morning
            else if (hour < 17) res = R.string.good_afternoon
            else if (hour < 22) res = R.string.good_evening
            else res = R.string.good_night
        }
        val festiveGreeting = if (festive) festival.greetingText(resources) else ""
        if (festiveGreeting.isNotEmpty()) {
            greeting.text = festiveGreeting
            greeting.setTextColor(liftTowardsWhite(festival.accentColor(), 0.25f))
        } else {
            greeting.text = if (greetingName.isEmpty()) getString(res)
                    else getString(R.string.greeting_with_name, getString(res), greetingName)
            greeting.setTextColor(greetingColor)
        }
        if (shamsi) shamsiDate.text = Shamsi.today(resources)
        if (lunar) lunarDate.text = Lunar.today(resources, lunarOffset)
    }

    private fun refreshPillsAfterNextDraw() {
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                // Posted from inside the frame's traversal, so it runs after that frame is drawn. A post straight
                // from onResume would run first: the frame is only scheduled after onResume returns.
                handler.post {
                    if (!isDestroyed) {
                        updateWifiLabel()
                        updateInputsButton()
                    }
                }
                return true
            }
        })
        root.invalidate()   // a resume that changes nothing on screen still draws once, so the refresh runs
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private fun updateWifiLabel() {
        var text = getString(R.string.wifi)
        var vpn = false
        var online = false
        var validated = false
        var icon = R.drawable.ic_wifi
        // The diya's flame: 0 is out (offline), 1 to 4 follow the Wi-Fi signal, Ethernet burns steady at 4.
        var flame = 0
        var steady = false
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            var wifi = false
            var ethernet = false
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (net in cm?.allNetworks ?: emptyArray<Network>()) {
                val caps = cm?.getNetworkCapabilities(net)
                if (caps == null) continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true
                    continue
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                online = true
                // Android reached the internet through it; a captive portal stays unvalidated until login.
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) validated = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
            }
            if (!online) {
                text = getString(R.string.offline)
                vpn = false
            } else if (ethernet) {
                text = getString(R.string.ethernet)
                icon = R.drawable.ic_ethernet
                flame = 4
                steady = true
            } else if (wifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                var ssid: String? = info?.ssid
                flame = if (info != null) Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(info.rssi, 5))) else 3
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim()
                    if (ssid.isNotEmpty() && !ssid.contains("unknown") && ssid != "0x") text = ssid
                }
            }
        } catch (_: Exception) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        val vpnDown = !vpn && online && vpnApp.isNotEmpty()
        if (vpnPill) updateVpnPill(vpn)
        if ((vpn || vpnDown) && !vpnPill) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            val badge = getString(if (vpn) R.string.vpn else R.string.vpn_off)
            val span = VpnBadge(if (vpn) getDrawable(R.drawable.ic_lock) else null, density)
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The Wi-Fi icon and its
            // gap are counted by hand: until the pill has a resolved layout direction (the first call, from
            // onResume, runs before that) getTotalPaddingStart/End leave the compound drawables out, which
            // left the name about 31dp too much room and the badge cut off.
            val paint = btnWifi.paint
            val compound = btnWifi.compoundDrawablesRelative
            val firstDrawable = compound[0]
            val iconRoom = if (firstDrawable != null) firstDrawable.intrinsicWidth + btnWifi.compoundDrawablePadding else 0
            val room = (btnWifi.maxWidth - btnWifi.paddingStart - btnWifi.paddingEnd - iconRoom
                    - span.getSize(paint, badge, 0, badge.length, null)).toFloat() - paint.measureText(" ") - 2 * density
            val name = TextUtils.ellipsize(text, paint, Math.max(0f, room), TextUtils.TruncateAt.END).toString()
            // A long badge ("VPN отключён") can leave room for a stub like "MT…", which says nothing and, after
            // rounding, can push the badge itself out of the pill. Show the badge alone then. On Ethernet the
            // icon already names the link, and "Ethernet" cut to "Ethe…" or not depended on the font size (#961),
            // so Ethernet always shows the icon and the badge alone.
            // The space stays even then: a line holding only the span gets no font metrics and collapses.
            val stub = name.length < text.length && name.length < 5
            text = if (stub || icon == R.drawable.ic_ethernet) "" else name
            val label = SpannableString("$text $badge")
            label.setSpan(span, text.length + 1, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (!TextUtils.equals(btnWifi.text, label)) btnWifi.text = label
        } else if (!TextUtils.equals(btnWifi.text, text)) {
            btnWifi.text = text
        }
        if (wifiIconOnly) {
            btnWifi.contentDescription = btnWifi.text
            btnWifi.text = null
        }
        val d = diya
        if (festive && d != null) {
            if (online && flame == 0) flame = 3   // online some other way (cellular, USB tethering)
            d.setSteady(steady)
            d.setFlame(flame)
        } else {
            if (icon != wifiIcon) {
                wifiIcon = icon
                btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            }
            var tint: ColorStateList? = if (online) wifiOnlineTint else wifiPlainTint
            // wifi_status_warnings: orange while connected but not validated (a captive portal), red while offline.
            if (wifiNoInternetTint != null) tint = if (!online) wifiOfflineTint else if (validated) wifiOnlineTint else wifiNoInternetTint
            if (wifiOnlineTint != null && btnWifi.compoundDrawableTintList != tint) {
                btnWifi.compoundDrawableTintList = tint
            }
        }
    }

    /** config.xml icon_only_buttons. */
    private var iconOnlyButtons = false
    /** config.xml top_menu: Inputs, Wi-Fi and Settings folded into one gear. */
    private var topMenu = false
    /** show_inputs_pill as configured: top_menu clears showInputsPill, so its Inputs row reads this. */
    private var showInputsMenuRow = true
    private var launcherSettingsButton = false
    /** icon_only_buttons without wifi_label_width: the Wi-Fi pill shows no network name. */
    private var wifiIconOnly = false
    /** config.xml wifi_status_color: the Wi-Fi icon's green while online and its usual pill colours; null while off. */
    private var wifiOnlineTint: ColorStateList? = null
    private var wifiPlainTint: ColorStateList? = null
    /** wifi_status_warnings (with wifi_status_color): orange without internet, red offline; null while off. */
    private var wifiNoInternetTint: ColorStateList? = null
    private var wifiOfflineTint: ColorStateList? = null
    /** The app library button in the top bar (library_button). */
    private lateinit var btnLibraryTop: View
    /** The open library came from that button: closing it returns there instead of scrolling to the grid's end. */
    private var libraryFromTopBar = false
    /** The open library came from the VPN pill (vpn_pill, no VPN app): closing it returns there while the pill still shows. */
    private var libraryFromVpnPill = false

    /** Turns a labelled top pill into the round TopButtonIcon shape: no text, the icon centred by even padding. */
    private fun makeIconOnly(pill: TextView) {
        pill.contentDescription = pill.text
        pill.text = null
        pill.maxWidth = Int.MAX_VALUE
        val pad = Math.round(10 * density)
        pill.setPaddingRelative(pad, 0, pad, 0)
        pill.compoundDrawablePadding = 0
        pill.layoutParams.width = Math.round(42 * density)
    }

    // ---- festive skin ------------------------------------------------------------------------------

    /**
     * Swaps the pill icons for the festival's: a bell for Inputs that swings when focused, a diya for the
     * network whose flame follows the connection, and a marigold for Settings that turns when focused.
     */
    private fun applyFestiveSkin(inputs: TextView, settings: TextView) {
        val b = BellDrawable(density)
        val m = MarigoldDrawable(density)
        val d = DiyaDrawable(density)
        bell = b
        marigold = m
        diya = d
        b.setReducedMotion(reducedMotion)
        m.setReducedMotion(reducedMotion)
        d.setReducedMotion(reducedMotion)
        setFestiveIcon(inputs, b)
        setFestiveIcon(btnWifi, d)
        setFestiveIcon(settings, m)
        addFocusAction(inputs, b::ring)
        addFocusAction(settings, m::turn)
    }

    private fun playPetalShower() {
        if (isFinishing || isDestroyed) return
        val shower = PetalShowerView(this)
        root.addView(shower, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shower.play(60, 2200) { root.removeView(shower) }
    }

    /**
     * The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. With
     * vpn_pill the VPN app has its own pill, so this one always opens Wi-Fi settings.
     */
    private fun openNetworkPill() {
        val i = if (vpnPill) null else vpnAppIntent()
        open(i ?: systemIntent(Settings.ACTION_WIFI_SETTINGS))
    }

    /** vpn_app's launch intent, or null when none is set or it is not installed. */
    private fun vpnAppIntent(): Intent? {
        if (vpnApp.isEmpty()) return null
        val i = packageManager.getLeanbackLaunchIntentForPackage(vpnApp)
        return i ?: packageManager.getLaunchIntentForPackage(vpnApp)
    }

    /**
     * vpn_pill: the VPN app, where it is switched on and off. Without one installed, the App library to pick from, or
     * Android's VPN settings when the theme turned the library off.
     */
    private fun openVpnPill() {
        val i = vpnAppIntent()
        if (i != null || !appLibraryEnabled) {
            open(i ?: systemIntent(Settings.ACTION_VPN_SETTINGS))
            return
        }
        onLibraryRequested()
        libraryFromVpnPill = true
    }

    /** vpn_pill: the pill outlined in green with a lock while a VPN is up, outlined in red "VPN off" while it is not;
     *  solid with a white ring only while focused. */
    private fun updateVpnPill(vpn: Boolean) {
        val state = VpnBadge.pillState(vpn, vpnAppIntent() != null)
        if (state == VpnBadge.PILL_HIDDEN) {
            if (btnVpn.visibility == View.GONE) return
            val focused = btnVpn.hasFocus()
            btnVpn.visibility = View.GONE
            if (focused) (if (btnWifi.visibility == View.VISIBLE) btnWifi else firstTopButton()).requestFocus()
            return
        }
        if (state != vpnPillState) {
            vpnPillState = state
            val up = state == VpnBadge.PILL_UP
            // The state colour at rest (the pill is an outline then), the contrasting ink on the solid focused fill.
            val ink = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(VpnBadge.pillInkColor(up, true), VpnBadge.pillInkColor(up, false)))
            if (iconOnlyVpn) {
                // An open lock on the plain glass button, or the closed lock outlined in green like the pill (solid
                // while focused); the words go to talkback.
                btnVpn.contentDescription = getString(if (up) R.string.vpn else R.string.vpn_off)
                btnVpn.background = if (up) VpnBadge.pillBackground(true, density) else vpnPlainBackground!!.newDrawable()
                btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(VpnBadge.iconOnlyLock(up), 0, 0, 0)
                btnVpn.compoundDrawableTintList = if (up) ink else vpnPlainInk
                btnVpn.visibility = View.VISIBLE
                return
            }
            btnVpn.setText(if (up) R.string.vpn else R.string.vpn_off)
            btnVpn.setTextColor(ink)
            btnVpn.background = VpnBadge.pillBackground(up, density)
            btnVpn.setCompoundDrawablesRelativeWithIntrinsicBounds(if (up) R.drawable.ic_lock else 0, 0, 0, 0)
            btnVpn.compoundDrawableTintList = ink
        }
        btnVpn.visibility = View.VISIBLE
    }


    /** Every settings entry shares the standard intent and the vendor fallback (SystemSettings). */
    private fun systemIntent(standardAction: String): Intent {
        return SystemSettings.intent(this, standardAction)
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private fun updateInputsButton() {
        if (!showInputsPill) {
            btnInputs.visibility = View.GONE
            return
        }
        var any = inputActions.liveTvIntent() != null || sourcePanelService.isNotEmpty()
                || (coocaaSources && CoocaaSources.list(this).isNotEmpty())
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        if (!any && tim != null) {
            for (info in tim.tvInputList) {
                // Inputs hidden in Settings > Inputs are left out of the menu, so they don't count here either.
                if (info.isPassthroughInput && !info.isHidden(this)) {
                    any = true
                    break
                }
            }
        }
        btnInputs.visibility = if (any) View.VISIBLE else View.GONE
    }

    /** boot_to_live_tv, or the Open on start app: the first time Home comes up after the TV starts, open that app
     *  instead. Once per boot, keyed on Settings.Global.BOOT_COUNT and committed to disk before the app starts: on a
     *  low-memory TV the app can push the launcher out of memory, and an apply() still in flight was lost then, so
     *  every Home press opened the app again. */
    private fun openLiveTvAfterBoot(): Boolean {
        val app = (if (openOnStartChoice) getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "") else "") ?: ""
        if (!bootToLiveTv && app.isEmpty()) return false
        val boot = OpenOnStart.boot(this)
        val prefs = getSharedPreferences(OpenOnStart.BOOT_PREFS, MODE_PRIVATE)
        val target = if (bootToLiveTv) liveTvPkg else app
        if (!OpenOnStart.due(boot, prefs.getInt(OpenOnStart.BOOT_KEY, -1), target, packageName)) return false
        if (!prefs.edit().putInt(OpenOnStart.BOOT_KEY, boot).commit()) return false
        val i = if (bootToLiveTv) inputActions.liveTvIntent() else OpenOnStart.intent(packageManager, app)
        if (i == null) return false
        // open_on_start_network_wait: Home stays on screen until the network is back or the wait is over.
        if (openOnStartWaitMs > 0 && !OpenOnStart.online(this)) {
            startPending = i
            startDeadline = SystemClock.elapsedRealtime() + openOnStartWaitMs
            handler.postDelayed(openWhenOnline, openOnStartWaitMs)
            // Validated between the check above and startPending: networkCallback may have skipped it.
            if (OpenOnStart.online(this)) handler.post(openWhenOnline)
            return false
        }
        open(i)
        return true
    }

    private fun open(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private fun showInputs() {
        TvInputsPopup(
            host = this,
            root = root,
            density = density,
            coocaaSources = coocaaSources,
            sourcePanelService = sourcePanelService,
            topMenu = topMenu,
            inputsConnectedOnly = inputsConnectedOnly,
            liveTvInputId = liveTvInputId,
            liveTvPkg = liveTvPkg,
            liveTvIntent = inputActions::liveTvIntent,
            openSourcePanel = inputActions::openSourcePanel,
            mappedSource = inputActions::mappedSource,
            openInput = inputActions::openInput,
            open = this::open,
            glassPopup = this::glassPopup,
            dropIn = this::dropIn,
        ).show()
    }

    private fun glassPopup(panel: View, width: Int): PopupWindow {
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))   // needed for Back to dismiss
        popup.isOutsideTouchable = true
        popup.animationStyle = 0
        popup.elevation = 0f
        popup.setOnDismissListener {
            if (openPopup === popup) openPopup = null
        }
        openPopup = popup
        return popup
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private fun dropIn(popup: PopupWindow, panel: View, anchor: View, gravity: Int) {
        panel.alpha = 0f
        panel.pivotY = 0f
        panel.scaleY = 0.92f
        panel.translationY = -8 * density
        popup.showAsDropDown(anchor, 0, (10 * density).toInt(), gravity)
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(if (reducedMotion) Motion.REDUCED_MS.toLong() else 220L)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    /** top_menu: the gear's dropdown, standing in for the Inputs, Wi-Fi and Settings pills. */
    private fun showTopMenu() {
        val anchor = findViewById<View>(R.id.btnSettings)
        val panel = layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (220 * density).toInt())
        // Inputs opens its own list once this one has closed.
        if (showInputsMenuRow) addTvInputRow(this, rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1) { root.post { showInputs() } }
        addTvInputRow(this, rows, popup, R.drawable.ic_wifi, getString(R.string.quick_network), -1, this::openNetworkPill)
        val network: CharSequence? = if (wifiIconOnly) btnWifi.contentDescription else btnWifi.text
        if (!TextUtils.isEmpty(network)) {
            val status = rows.getChildAt(rows.childCount - 1).findViewById<TextView>(R.id.status)
            status.text = network
            status.visibility = View.VISIBLE
        }
        addTvInputRow(this, rows, popup, R.drawable.ic_swap_vert, getString(R.string.reorder), -1, reorder::toggleReorder)
        if (launcherSettingsButton) {
            addTvInputRow(this, rows, popup, R.drawable.ic_home_settings_pill, getString(R.string.launcher_settings), -1) {
                startActivityForResult(Intent(this, LauncherSettingsActivity::class.java), LAUNCHER_SETTINGS_REQUEST)
            }
        } else {
            addTvInputRow(this, rows, popup, R.drawable.ic_settings, getString(R.string.settings), -1) {
                open(systemIntent(Settings.ACTION_SETTINGS))
            }
        }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((220 * density).toInt(), Math.min(panel.measuredWidth, (360 * density).toInt()))
        dropIn(popup, panel, anchor, if (resources.getBoolean(R.bool.rtl_menu_bar)) Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    /** The header opens launcher settings directly; hold OK keeps the extra home actions available. */
    private fun showSettingsMenu() {
        val anchor = findViewById<View>(R.id.btnSettings)
        val panel = layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (200 * density).toInt())
        if (!launcherSettingsButton) addSettingsMenuRow(rows, popup, R.string.launcher_settings) {
            startActivityForResult(Intent(this, LauncherSettingsActivity::class.java), LAUNCHER_SETTINGS_REQUEST)
        }
        // Firmware that ships its own settings app next to the AOSP one (Xiaomi China is one): the
        // vendor app holds pages the AOSP settings has no row for, such as Account & Security with
        // the ADB switch. Offered here only while one of them is installed.
        if (SystemSettings.vendorIntent(this) != null) {
            addSettingsMenuRow(rows, popup, R.string.native_settings) {
                val vendor = SystemSettings.vendorIntent(this)
                if (vendor != null) open(vendor)
            }
        }
        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, moduleCardMenus::showList)
        if (dockRow) addSettingsMenuRow(rows, popup, R.string.dock_edit, reorder::startDockEdit)
        addSettingsMenuRow(rows, popup, R.string.reorder, reorder::toggleReorder)
        // A launcher may not restart the TV itself; the Home key helper can open the system power menu instead.
        if (HomeKeyService.canOpenPowerMenu()) addSettingsMenuRow(rows, popup, R.string.power_menu, HomeKeyService::openPowerMenu)
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((200 * density).toInt(), panel.measuredWidth)
        // Hebrew puts Settings at the left of the header: grow the menu inward from it.
        dropIn(popup, panel, anchor, if (resources.getBoolean(R.bool.rtl_menu_bar))
                Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    /** A tile configured with launch_actions "input:<id>" (config.xml): switch to that TV input directly,
     *  the same path the Inputs menu's own rows use, instead of opening the input's owning app normally. */
    override fun onOpenInput(inputId: String) {
        inputActions.openInput(inputId)
    }

    /**
     * Hold OK on a tile: a glass menu next to it. Move starts reorder mode with this tile already picked up;
     * Remove from Home takes the shortcut off (only with the app library on, where one press adds it back);
     * App info opens the TV's page for the app, which is what holding OK used to do.
     */
    override fun onTileLongPress(position: Int, card: View) {
        if (reorder.reordering) return
        val key = adapter.keyAt(position)
        val pkg = adapter.packageAt(position)
        // Empty dock slots and the Default-home tile carry the launcher's own package: no menu for them.
        if (key == null || pkg == null || pkg == packageName) return
        val panel = layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (200 * density).toInt())
        if (dockRow && position < adapter.movableCount()) {
            val inDock = position < COLUMNS
            addSettingsMenuRow(rows, popup, if (inDock) R.string.tile_replace_in_dock else R.string.tile_add_to_dock) {
                reorder.startDockEditFor(key, inDock)
            }
        }
        if (position < adapter.movableCount()) addSettingsMenuRow(rows, popup, R.string.tile_move) {
            if (beginReorder()) {
                val at = adapter.positionOf(key)   // beginReorder can rebuild the tiles
                if (at >= 0) {
                    focusTile(at)
                    pickUp(at)
                    Toast.makeText(this, R.string.reorder_hint, Toast.LENGTH_LONG).show()
                }
            }
        }
        if (appLibraryEnabled) addSettingsMenuRow(rows, popup, R.string.library_remove_shortcut) {
            if (LibraryApps.save(this, pkg, false)) reloadApps(false)
            else Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show()
        }
        // Some firmware has no App info page at all; a row that only toasts "can't open" helps nobody.
        val info = SystemSettings.appInfo(this, pkg)
        if (info != null) addSettingsMenuRow(rows, popup, R.string.library_app_info) { open(info) }
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max((200 * density).toInt(), panel.measuredWidth)
        // Dock tiles sit near the bottom of the screen: open the menu above the tile when it would not fit below.
        val at = IntArray(2)
        card.getLocationInWindow(at)
        val gap = (10 * density).toInt()
        val height = panel.measuredHeight
        if (at[1] + card.height + gap + height <= root.height) {
            dropIn(popup, panel, card, Gravity.START)
        } else {
            panel.alpha = 0f
            panel.pivotY = height.toFloat()
            panel.scaleY = 0.92f
            panel.translationY = 8 * density
            popup.showAtLocation(root, Gravity.NO_GRAVITY, at[0], Math.max(0, at[1] - gap - height))
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        rows.getChildAt(0).requestFocus()
    }

    private fun addSettingsMenuRow(rows: LinearLayout, popup: PopupWindow, title: Int, action: Runnable) {
        val row = layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<View>(R.id.icon).visibility = View.GONE
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.findViewById<TextView>(R.id.title).setText(title)
        row.setOnClickListener {
            popup.dismiss()
            action.run()
        }
        // The OK-up after opening the menu must not activate its first row.
        var pressed = false
        row.setOnKeyListener { _, code, event ->
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) {
                false
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) pressed = true
                true
            } else {
                val activate = pressed
                pressed = false
                if (activate) {
                    popup.dismiss()
                    action.run()
                }
                true
            }
        }
        rows.addView(row)
    }
}
