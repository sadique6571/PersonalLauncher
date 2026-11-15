package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.util.ArrayList
import java.util.Arrays
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
        // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
        // or Developer options → "Don't keep activities"); they only reset when the process dies.
        private var lastFocused = 0
        /** App of the last focused tile, so focus follows it when the app list changes. */
        private var lastFocusedKey: String? = null
        /** The day the petal shower last played, so it greets the first Home of each day only. */
        private var lastPetalShower: LocalDate? = null
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
    private lateinit var shelf: LauncherShelf
    private var cinematic = false
    private var appGlow = false
    private var cinematicWhiteFocus = false
    private var animations = false
    private var advancedEffects = false
    private lateinit var greeting: TextView
    private lateinit var clockBlock: View
    private lateinit var btnInputs: View
    private lateinit var btnWifi: TextView
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
    private lateinit var btnVpn: TextView
    private lateinit var networkPills: LauncherNetworkPills
    private var density = 0f
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private var moduleCards = false
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
    private var greetingColor = 0
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
    private val popups by lazy {
        LauncherPopups(this, root, density, reducedMotion, LAUNCHER_SETTINGS_REQUEST, this::open)
    }
    private lateinit var btnReorder: View
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private lateinit var dockApps: List<String>
    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private var fillDockLeavesSlots = false
    private var appLibrary: AppLibraryDialog? = null
    private var appLibrarySelection = false
    private var libraryOkOpens = false

    private val gridFocus = LauncherGridFocus(
        host = this,
        gridView = { grid },
        currentAdapter = { adapter },
        animationsEnabled = { animations },
        libraryClosed = { appLibrary == null },
        firstTopButton = this::firstTopButton,
        onTileFocused = this::onTileFocused,
    )
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
        setAccent = { glassHighlight.setAccent(it) },
        defaultAccent = { if (festive) festival.accentColor() else 0 },
        setButtonActive = { btnReorder.isActivated = it },
        reloadTiles = this::reloadTiles,
        focusTile = gridFocus::focusTile,
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
        glassPopup = { panel, width -> popups.glassPopup(panel, width) },
        dropIn = { popup, panel, anchor, gravity -> popups.dropIn(popup, panel, anchor, gravity) },
    )
    private val launcherStats = LauncherStats(this, handler, { stats }, { showStats })
    /** The extracted pieces of Home, built in onCreate once their views and config values exist. */
    private lateinit var headerClock: MainClockBlock
    private lateinit var bootOpener: MainBootOpener
    private lateinit var festiveSkin: MainFestiveSkin
    private lateinit var homeCallbacks: MainHomeCallbacks
    private lateinit var glassHighlight: MainGlassHighlight
    private lateinit var tileNavigation: MainTileNavigation
    private lateinit var topPills: MainTopPills
    private lateinit var settingsKey: MainSettingsKey

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
        settingsKey = MainSettingsKey(this, cfg.settingsKeycodes, cfg.settingsKeyPanel, this::open)
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
        bootOpener = MainBootOpener(this, handler, cfg.openOnStartNetworkWait * 1000L,
                cfg.openOnStartChoice, cfg.bootToLiveTv, cfg.liveTvPackage,
                inputActions::liveTvIntent, this::open)
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
        festiveSkin = MainFestiveSkin(this, root, density, reducedMotion) { !isFinishing && !isDestroyed }
        wallpaperPlayer = WallpaperPlayer(this, root, cfg)
        sky = findViewById(R.id.sky)
        sky.setIntensity(cfg.skyIntensity)
        val glassView = findViewById<GlassView>(R.id.glass)
        glassView.setStyle(cfg.glassStyle)
        if (festive) glassView.setAccent(festival.accentColor())
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
        headerClock = MainClockBlock(this, handler, clockBlock, greeting, shamsiDate, lunarDate,
                greetingName, greetingColor, festive, festival, shamsi, lunar, lunarOffset)
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
            grid.post { glassHighlight.refit(lastFocused, gridFocus.libraryFocusPending) }
        }

        glassHighlight = MainGlassHighlight(
            glassView, root, grid, shelf, { adapter }, density,
            animations, reducedMotion, advancedEffects, cinematic, appGlow, cinematicWhiteFocus,
            festive, singleRow, dockRow, focusOvershoot,
            { isDestroyed }, this::onTileFocused,
        )
        tileNavigation = MainTileNavigation(
            { adapter }, grid, gridFocus, singleRow, rtlAppGrid, { lastFocused }, this::firstTopButton,
        )

        sportsPanel.attachToGrid(grid, clockBlock)

        btnInputs = findViewById(R.id.btnInputs)
        topPills = MainTopPills(
            this, handler, root, density, btnInputs, { networkPills }, inputActions,
            sourcePanelService, coocaaSources, cfg.showInputsPill, appLibraryEnabled, this::open,
            { onLibraryRequested(); libraryFromVpnPill = true }, { isDestroyed },
        )
        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnInputs.setOnClickListener { showInputs() }
        btnWifi.setOnClickListener { topPills.openNetworkPill() }
        btnVpn.setOnClickListener { topPills.openVpnPill() }
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
        networkPills = LauncherNetworkPills(
            this, cfg, btnWifi, btnVpn, density, { if (festive) festiveSkin.diya else null }, this::firstTopButton,
        )
        if (cfg.topMenu) {
            // One gear: Inputs and Wi-Fi move into its dropdown. The Wi-Fi pill stays hidden but keeps its
            // label up to date, which the menu's Wi-Fi row shows.
            topPills.showInputsPill = false
            btnInputs.visibility = View.GONE
            btnWifi.visibility = View.GONE
            (btnSettings as TextView).setText(R.string.settings)
            (btnSettings as TextView).setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0)
            if (!iconOnlyButtons) topPills.makeIconOnly(btnSettings as TextView)
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
            else topPills.makeIconOnly(btnWifi)
            topPills.makeIconOnly(btnSettings as TextView)
        }
        if (cfg.iconOnlyInputs) topPills.makeIconOnly(btnInputs as TextView)
        networkPills.captureVpnStyle(topPills::makeIconOnly)
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
                gridFocus.focusTile(if (singleRow) lastFocused else lastFocused % COLUMNS)
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
        if (festive) festiveSkin.apply(btnInputs as TextView, btnWifi, btnSettings as TextView)

        // Hide the glass when focus leaves the tiles.
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newF ->
            val onTile = newF != null && newF.id == R.id.card
            if (!onTile) glassHighlight.hide()
        }

        homeCallbacks = MainHomeCallbacks(
            this, handler, MINUTE_MS,
            onNetworkChanged = { handler.post { networkPills.refresh() } },
            onNetworkValidated = { bootOpener.onNetworkValidated() },
            warnsAboutInternet = { networkPills.warnsAboutInternet },
            onInputChanged = { topPills.updateInputsButton() },
            onPackagesChanged = {
                if (tileRefresh.onPackagesChanged()) {
                    reloadApps(false)
                    appLibrary?.refresh()
                }
            },
            onTimeChanged = { headerClock.updateClockVisibility() },
            onMinuteTick = {
                headerClock.updateGreeting()
                sportsPanel.update()
                // The pill follows the network callbacks and each return to Home; only the diya's flame follows the
                // signal level, so only it needs this minute's binder calls (about 7 ms on the UI thread).
                if (festive) networkPills.refresh()
                sky.update()
                wallpaperPlayer.update()
            },
        )
        homeCallbacks.registerPersistent()
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
            grid.post { gridFocus.focusTile(0) }
        }
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        started = true
        homeCallbacks.registerTime()
        headerClock.updateClockVisibility()
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
        if (bootOpener.openAfterBoot()) return
        headerClock.updateGreeting()
        sportsPanel.update()
        // The network and Inputs pills keep their state while Home is in the background, and callbacks update them
        // when the network or the inputs change, so this refresh only catches anything missed. Its binder calls
        // (about 7 ms on a TCL C755, most of it WifiManager.getConnectionInfo) would hold up the first frame back
        // on Home, so after the first resume it runs once that frame is drawn.
        if (pillsShown) {
            topPills.refreshAfterNextDraw()
        } else {
            networkPills.refresh()
            topPills.updateInputsButton()
            pillsShown = true
        }
        if (showStats) launcherStats.start()
        sportsPanel.start()
        sky.update()
        wallpaperPlayer.start()
        homeCallbacks.startMinuteTick()
        if (festive && festiveSkin.diya != null) {
            festiveSkin.diya!!.start()
            val today = LocalDate.now()
            if (!reducedMotion && today != lastPetalShower) {
                lastPetalShower = today
                handler.postDelayed({ festiveSkin.playPetalShower() }, 450)
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
        gridFocus.cancelPendingFocusScroll()
        val footer = grid.findViewHolderForAdapterPosition(adapter.appCount())
        val restoreLibrary = adapter.isLibraryAt(adapter.appCount()) && footer != null
                && footer.itemView.findViewById<View>(R.id.btnLibrary) != null
                && footer.itemView.findViewById<View>(R.id.btnLibrary).hasFocus()
        // Capture the app before rebinding can move focus and overwrite its key.
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        val restoreFocus = appLibrary == null && (animate || grid.hasFocus())
        if (restoreFocus) {
            glassHighlight.resetForReload()
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
            if (restoreLibrary) grid.post { gridFocus.focusLibrary() }
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
        val returnToLibrary = gridFocus.libraryFocusPending
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        grid.post {
            val pos = adapter.positionOf(returnKey)
            if (returnToLibrary || gridFocus.libraryFocusPending) gridFocus.focusLibrary()
            else gridFocus.focusTile(if (pos >= 0) pos else returnSlot)
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
        homeCallbacks.stopMinuteTick()
        bootOpener.cancel()
        launcherStats.stop()
        sportsPanel.stop()
        festiveSkin.diya?.stop()
        gridFocus.cancelPendingFocusScroll()
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        moduleCardMenus.dismissPrompt()
        appLibrary?.close()
        popups.current?.dismiss()
    }

    override fun onStop() {
        super.onStop()
        started = false
        homeCallbacks.unregisterTime()
        headerClock.cancelPendingCheck()
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
            if (gridFocus.libraryFocusPending && appLibrary == null) gridFocus.focusLibrary()
            moduleCardMenus.showPrompt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sportsPanel.shutdown()
        homeCallbacks.unregisterPersistent()
        wallpaperPlayer.close()
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
        if (settingsKey.handle(event)) return true
        if (tileMenuEnabled && !reorder.reordering && popups.current == null && !moduleCardMenus.promptShowing
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
        popups.current?.dismiss()
        gridFocus.libraryFocusPending = false
        libraryFromTopBar = false
        libraryFromVpnPill = false
        val library = AppLibraryDialog(this, animations, reducedMotion, appLibrarySelection, libraryOkOpens, adapter::containsPackage, { reloadApps(false) }, {
            appLibrary = null
            popups.current = null
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
                    gridFocus.libraryFocusPending = true
                    grid.post {
                        if (gridFocus.libraryFocusPending && appLibrary == null && hasWindowFocus() && !isFinishing) gridFocus.focusLibrary()
                    }
                }
            }
        }, libraryExcludedApps)
        appLibrary = library
        popups.current = library.popup
        library.show()
    }

    override fun onTileFocused(position: Int, card: View) {
        lastFocused = position
        lastFocusedKey = adapter.keyAt(position)
        glassHighlight.onTileFocused(position, card)
    }

    // ---- navigation ------------------------------------------------------------------------------

    override fun onTileVerticalKey(position: Int, keyCode: Int): Boolean =
            tileNavigation.onTileVerticalKey(position, keyCode)

    override fun onLibraryFocused() {
        gridFocus.libraryFocusPending = false
        glassHighlight.hide()
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

    override fun onLibraryUp() = tileNavigation.onLibraryUp()

    // ---- top bar ---------------------------------------------------------------------------------

    /** config.xml icon_only_buttons. */
    private var iconOnlyButtons = false
    /** config.xml top_menu: Inputs, Wi-Fi and Settings folded into one gear. */
    private var topMenu = false
    /** show_inputs_pill as configured: top_menu clears showInputsPill, so its Inputs row reads this. */
    private var showInputsMenuRow = true
    private var launcherSettingsButton = false
    /** The app library button in the top bar (library_button). */
    private lateinit var btnLibraryTop: View
    /** The open library came from that button: closing it returns there instead of scrolling to the grid's end. */
    private var libraryFromTopBar = false
    /** The open library came from the VPN pill (vpn_pill, no VPN app): closing it returns there while the pill still shows. */
    private var libraryFromVpnPill = false

    // ---- opening ---------------------------------------------------------------------------------

    /** Every settings entry shares the standard intent and the vendor fallback (SystemSettings). */
    private fun systemIntent(standardAction: String): Intent {
        return SystemSettings.intent(this, standardAction)
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
            glassPopup = { panel, width -> popups.glassPopup(panel, width) },
            dropIn = { popup, panel, anchor, gravity -> popups.dropIn(popup, panel, anchor, gravity) },
        ).show()
    }

    private fun showTopMenu() {
        popups.showTopMenu(
            showInputsMenuRow, launcherSettingsButton, networkPills.label,
            this::showInputs, topPills::openNetworkPill, reorder::toggleReorder,
        )
    }

    private fun showSettingsMenu() {
        popups.showSettingsMenu(
            launcherSettingsButton, moduleCards, dockRow, moduleCardMenus::showList,
            reorder::startDockEdit, reorder::toggleReorder,
        )
    }

    /** A tile configured with launch_actions "input:<id>" (config.xml): switch to that TV input directly,
     *  the same path the Inputs menu's own rows use, instead of opening the input's owning app normally. */
    override fun onOpenInput(inputId: String) {
        inputActions.openInput(inputId)
    }

    override fun onTileLongPress(position: Int, card: View) {
        if (reorder.reordering) return
        popups.showTileMenu(position, card, dockRow, appLibraryEnabled, adapter, reorder) { reloadApps(false) }
    }

}
