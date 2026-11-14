package com.sadique.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.widget.FrameLayout
import android.widget.ImageView
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
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Collections
import java.util.HashMap
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
        private const val STATS_MS = 10_000L
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
        /** Blue the glass turns while reorder mode is on. */
        private val REORDER_ACCENT = 0xFF6EC1FF.toInt()
        // -1: need another sample. -2: this source cannot be read (Android 9 hides /proc/stat from apps).
        private const val CPU_UNREADABLE = -2
        private val RACE_RED = 0xFFFF3B2F.toInt()

        /** One quiet line, e.g. "CPU 8%  ·  Memory 430 MB free  ·  Storage 3.1 GB free". Runs off the main thread. */
        private fun appendStat(line: StringBuilder, part: String) {
            if (line.isNotEmpty()) line.append("  ·  ")
            line.append(part)
        }

        private fun readFirstLine(path: String): String? {
            try {
                BufferedReader(FileReader(path)).use { r -> return r.readLine() }
            } catch (_: IOException) {
                return null
            }
        }

        private fun formatGb(res: Resources, bytes: Long): String {
            if (bytes <= 0) return ""
            val gb = bytes / (1024.0 * 1024 * 1024)
            return if (gb >= 10) res.getString(R.string.storage_gb_whole, gb)
            else res.getString(R.string.storage_gb_decimal, gb)
        }

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

        private fun fold(view: View, folded: MutableList<View>) {
            view.visibility = View.GONE
            folded.add(view)
        }
    }

    private var rtlAppGrid = false
    private lateinit var liveTvPkg: String
    /** Launcher settings' change count this Home was built with (see onResume). */
    private var settingsRevision = 0L
    /** Idle time on Home before ClockActivity; 0 is off (config.xml, home_idle_clock_minutes). */
    private var idleClockMs = 0L
    private val showIdleClock = Runnable { startActivity(Intent(this, ClockActivity::class.java)) }
    private lateinit var liveTvAction: String
    private lateinit var liveTvInputId: String
    /** The app library is on (config.xml, app_library): only then can the tile menu take an app off Home. */
    private var appLibraryEnabled = false
    /** The same opt-in menu is available from either hold-OK or the remote's Menu key. */
    private var tileMenuEnabled = false
    private lateinit var inputAction: String
    private lateinit var inputSources: List<String>
    private lateinit var inputUriScheme: String
    private lateinit var sourcePanelService: String
    private lateinit var sourcePanelAction: String
    private lateinit var sourcePanelExtra: String
    private var coocaaSources = false

    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var root: FrameLayout
    private lateinit var wallpaperPlayer: WallpaperPlayer
    private lateinit var glass: GlassView
    private lateinit var ambient: View
    private lateinit var heroName: TextView
    /** The top shelf: heroName, plus the cinematic shelf's "Last opened" line and dock position dots. */
    private lateinit var hero: View
    private lateinit var heroMeta: TextView
    private lateinit var heroDots: LinearLayout
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    private var cinematic = false
    /** config.xml ambient_glow, overridable by a theme. Off: the glow view is gone and no fade or colour animator
     *  is started for it, so a focus move costs nothing extra on a launcher that runs all day. */
    private var ambientGlow = false
    /** config.xml app_glow: the highlight takes the focused app's colour on every tile. */
    private var appGlow = false
    /** config.xml glow_style: the shape setAmbient paints the glow mask in. */
    private lateinit var glowStyle: String
    /** config.xml cinematic_white_focus: the cinematic shelf keeps the white highlight. */
    private var cinematicWhiteFocus = false
    private lateinit var heroArt: ImageView
    /** config.xml cinematic_dots: false leaves the dock position dots out of the cinematic shelf. */
    private var heroDotsShown = true
    /** The artwork's opacity behind the cinematic shelf (config.xml cinematic_art); 0 leaves it out. */
    private var heroArtAlpha = 0.5f
    /** Softened artwork per app (HeroArt, about 5 KB each), made the first time its dock tile is focused. */
    private val heroArtCache: MutableMap<String, Bitmap> = HashMap()
    /** The app whose artwork heroArt shows, so focusing it again only fades it back in. */
    private var heroArtKey: String? = null
    /** The focus glow's colour while the cinematic shelf tints it, 0 for plain white glass. */
    private var glassAccent = 0
    private var ambientColor = -1
    private var ambientAnimator: ValueAnimator? = null
    private var heroShown = false
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
    private lateinit var raceCalendar: List<RaceWeekend.Race>
    private lateinit var racePanel: View
    private lateinit var raceKicker: TextView
    private lateinit var raceHeadline: TextView
    private lateinit var raceSessions: TextView
    /** game_day: each team's next game from an add-on, rotating in the race panel; null when the key is off. */
    private var gameDay: GameDay? = null
    private lateinit var raceBar: View
    private lateinit var racePager: LinearLayout
    private var rotationStart = 0L
    private var lastEndedRead = 0L
    private val turnTick = Runnable { updatePanel() }
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
    private var cardPrompt: Dialog? = null
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private var promptsPaused = false
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
    /** Reorder mode is on (top-bar button). */
    private var reordering = false
    /** Key of the tile being carried in reorder mode, or null. */
    private var movingKey: String? = null
    /** Tile order when reorder mode started, to save only when something moved. */
    private var orderAtStart: List<String>? = null
    /**
     * fill_dock false: tiles in the dock when reorder mode started, plus those carried or swapped into it since
     * (#499). A grid app that only slid into a free slot, which reorder mode does not show, is not one of them.
     */
    private var dockInSession: MutableSet<String>? = null
    /** fill_dock false: how many dock slots held an app when reorder mode started. */
    private var dockedAtStart = 0
    private lateinit var btnReorder: View
    /** dock_apps as loaded (config.xml or a theme): a saved Order only applies while these are unchanged. */
    private lateinit var dockApps: List<String>
    /** fill_dock false: the dock can have empty slots, which reorder mode leaves out and restores afterwards. */
    private var fillDockLeavesSlots = false
    /** Edit-dock mode (Settings menu): a reorder session that swaps one chosen app into one dock place. */
    private var dockEditing = false
    /** Dock place being changed in edit-dock mode, or -1 while the place is still being chosen. */
    private var dockEditSlot = -1
    /** Tile menu's "Add to dock": the app waiting for a dock place in edit-dock mode, or null. */
    private var dockEditAppKey: String? = null
    private var appLibrary: AppLibraryDialog? = null
    private var appLibrarySelection = false
    private var libraryOkOpens = false
    private var libraryFocusPending = false
    private var pendingFocusScroll: RecyclerView.OnScrollListener? = null

    private val handler = Handler(Looper.getMainLooper())
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
            updatePanel()
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
        liveTvAction = cfg.liveTvAction
        liveTvInputId = cfg.liveTvInputId
        appLibraryEnabled = cfg.appLibrary
        tileMenuEnabled = cfg.tileMenu
        inputAction = cfg.inputAction
        inputSources = cfg.inputSources
        inputUriScheme = cfg.inputUriScheme
        sourcePanelService = cfg.sourcePanelService
        sourcePanelAction = cfg.sourcePanelAction
        sourcePanelExtra = cfg.sourcePanelExtra
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
        ambient = findViewById(R.id.ambient)
        // The glow is one bitmap, so its alpha can be applied while drawing it. Otherwise every frame of
        // a fade renders it into a full-screen offscreen buffer first.
        ambient.forceHasOverlappingRendering(false)
        ambientGlow = cfg.ambientGlow
        appGlow = cfg.appGlow
        if (!ambientGlow) ambient.visibility = View.GONE
        heroName = findViewById(R.id.heroName)
        hero = findViewById(R.id.hero)
        // The hero is now a group (name, and the cinematic lines): without this every fade of the default
        // shelf would render it through an offscreen buffer first, as the glow above notes.
        hero.forceHasOverlappingRendering(false)
        heroMeta = findViewById(R.id.heroMeta)
        heroDots = findViewById(R.id.heroDots)
        heroArt = findViewById(R.id.heroArt)
        cinematic = cfg.cinematicHome
        glowStyle = cfg.glowStyle
        heroDotsShown = cfg.cinematicDots
        cinematicWhiteFocus = cfg.cinematicWhiteFocus
        heroArtAlpha = cfg.cinematicArt / 100f
        if (cinematic) setUpCinematicShelf(cfg.cinematicScrim)
        // dock_title false: the name above the dock (and the cinematic lines under it) never shows; the glow
        // still follows the dock.
        if (!cfg.dockTitle) hero.visibility = View.GONE
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
        raceCalendar = cfg.raceCalendar
        racePanel = findViewById(R.id.racePanel)
        raceKicker = findViewById(R.id.raceKicker)
        raceHeadline = findViewById(R.id.raceHeadline)
        raceSessions = findViewById(R.id.raceSessions)
        raceBar = findViewById(R.id.raceBar)
        racePager = findViewById(R.id.racePager)
        gameDay = GameDay.create(this, cfg.gameDay, handler, Runnable { updatePanel() })
        // The rotation runs on the clock that never jumps: setting the TV's time must not stall or stretch a turn.
        rotationStart = SystemClock.elapsedRealtime()
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
        grid.layoutManager = object : GridLayoutManager(this, if (singleRow) 1 else COLUMNS,
                if (singleRow) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false) {
            override fun isLayoutRTL(): Boolean {
                return rtlAppGrid
            }

            /**
             * Every "bring this tile on screen" scroll lands here, both RecyclerView's own on focus and
             * onTileFocused's. The stock version treats the grid's top padding as the visible top, but that
             * padding only pushes the dock down the screen: grid rows scroll up into it. With the stock rule,
             * Up into a row above the padding dragged that row down to the padding line, cut it off at the
             * bottom of the screen and could scroll the focused tile out entirely, dropping focus into the
             * top bar. Grid tiles now scroll just enough to show the rect between the grid's top edge and its
             * bottom; dock tiles are left to onTileFocused, which parks the dock row on the padding line.
             */
            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View,
                                                       rect: Rect, immediate: Boolean,
                                                       focusedChildVisible: Boolean): Boolean {
                if (singleRow) {
                    // Sideways: scroll just enough to bring the tile's cell inside the padding. Cells are a
                    // column wide, so the row moves a whole tile at a time and the tiles stay on the columns.
                    // The whole cell, not the card rect passed in: the card is narrower and centred in it.
                    val left = child.left
                    val right = child.right
                    val dx: Int
                    if (left < paddingLeft) dx = left - paddingLeft
                    else if (right > width - paddingRight) dx = right - (width - paddingRight)
                    else dx = 0
                    if (dx == 0) return false
                    if (immediate || reducedMotion || !animations) parent.scrollBy(dx, 0)
                    else parent.smoothScrollBy(dx, 0)
                    return true
                }
                if (dockRow && getPosition(child) < COLUMNS) return false
                val top = child.top + rect.top
                val bottom = top + rect.height()
                // A row already fully on screen stays put, even inside the bottom padding (the last row
                // rows_below_dock leaves visible ends there; nudging it up was the bug). A row that has to scroll
                // in still stops at the padding line, clear of the screen edge, as before.
                val limitBottom = height - paddingBottom
                val dy: Int
                if (top < 0) dy = top
                else if (bottom <= height) dy = 0
                else dy = Math.min(top, bottom - limitBottom)
                if (dy == 0) return false
                if (immediate || reducedMotion || !animations) parent.scrollBy(0, dy)
                else parent.smoothScrollBy(0, dy)
                return true
            }
        }
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
                v.alpha = tileAlpha(grid.getChildAdapterPosition(v))
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
            val shelfClearance = if (cfg.appLibrary) hero.measuredHeight + (40 * density).toInt() else 0
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

        // game_day: the panel above the dock rides up with the cards instead of being covered by them, and fades
        // out by the time it reaches the clock. The grid's own offset is read, so a jump (Home, a restored focus)
        // lands right too.
        if (gameDay != null) {
            val clockBlock = findViewById<View>(R.id.leftBlock)
            grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val shift = if (rv.canScrollVertically(-1)) rv.computeVerticalScrollOffset() else 0
                    racePanel.translationY = -shift.toFloat()
                    val room = Math.max(1, racePanel.top - clockBlock.bottom)
                    racePanel.alpha = Math.max(0f, 1f - shift / room.toFloat())
                }
            })
        }

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
        btnReorder.setOnClickListener { toggleReorder() }
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
        if (gameDay != null && !stopped) gameDay!!.read()
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
        promptsPaused = false
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
        updatePanel()
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
        if (showStats) startStats()
        gameDay?.start()
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
        toggleReorder()
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
            heroShown = false
            hero.alpha = 0f
            ambient.alpha = 0f
            heroArt.animate().cancel()
            heroArt.alpha = 0f
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
            if (pos < 0) hideHero()
            // A newly installed app may have brought a card with it.
            tilesReady = true
            startPendingSettingsReorder()
            showCardPrompt()
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
        if (reordering) endReorder()   // an app installed or removed while reordering: keep the order so far
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        val returnKey = lastFocusedKey
        val returnSlot = lastFocused
        val none: List<Cards.Card> = Collections.emptyList()
        // An updated app may bring new artwork; the backdrop is cheap to make again.
        heroArtCache.clear()
        heroArtKey = null
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
            showCardPrompt()
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
        endReorder()   // Home or another app while reordering keeps the order so far
        wallpaperPlayer.stop()
        stopAmbientAnimation()
        hero.animate().cancel()
        ambient.animate().cancel()
        heroArt.animate().cancel()
        handler.removeCallbacks(minuteTick)
        handler.removeCallbacks(openWhenOnline)
        startPending = null   // leaving Home before the network came back: the boot's app stays closed
        stopStats()
        gameDay?.stop()
        handler.removeCallbacks(turnTick)
        diya?.stop()
        cancelPendingFocusScroll()
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        cardPrompt?.dismiss()
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
        heroArtCache.clear()
        heroArtKey = null
        wallpaperPlayer.releaseForMemoryPressure()
        tileRefresh.invalidate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            startPendingSettingsReorder()
            if (libraryFocusPending && appLibrary == null) focusLibrary()
            showCardPrompt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameDay?.shutdown()
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
        if (tileMenuEnabled && !reordering && openPopup == null && cardPrompt == null
                && event.keyCode == KeyEvent.KEYCODE_MENU) {
            val position = focusedTilePosition()
            if (position >= 0 && position < adapter.appCount()
                    && packageName != adapter.packageAt(position)) {
                if (RemoteKeys.menuReleased(event.action, event.flags)) {
                    onTileLongPress(position, currentFocus!!)
                }
                return true
            }
        }
        if (!reordering) return super.dispatchKeyEvent(event)
        if (dockEditing) return dispatchDockEdit(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
        if (movingKey != null) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (down) moveBy(code)
                    return true
                }
                else -> {
                    if (ok || back) {
                        if (!down) drop()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
        }
        if (back) {
            if (!down) endReorder()
            return true
        }
        val pos = focusedTilePosition()
        val focus = currentFocus
        if (ok && focus != null && focus.id == R.id.btnLibrary) {
            // The App library pill sits in the grid but is no tile: OK ends the mode (saving the order) and opens it.
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                endReorder()
                onLibraryRequested()
            }
            return true
        }
        if (ok && grid.hasFocus()) {
            // RecyclerView can briefly report NO_POSITION during a move or rebind. Still consume
            // OK on the grid: falling through would let the focused card open its app in reorder mode.
            if (!down && pos >= 0) pickUp(pos)
            return true
        }
        return super.dispatchKeyEvent(event)   // arrows navigate; OK on a top-bar pill works as usual
    }

    /** Adapter position of the focused tile, or -1 when focus is not on a tile. */
    private fun focusedTilePosition(): Int {
        val f = currentFocus
        val parent = f?.parent
        if (f == null || f.id != R.id.card || parent !is View) return -1
        return grid.getChildAdapterPosition(parent)
    }

    /** The reorder button: turns the mode on (focus goes back to the last tile), or off again. */
    private fun toggleReorder() {
        if (reordering) {
            endReorder()
            return
        }
        if (!beginReorder()) return
        focusTile(Math.max(0, Math.min(lastFocused, adapter.movableCount() - 1)))
        Toast.makeText(this, R.string.reorder_hint, Toast.LENGTH_LONG).show()
    }

    /** The start shared by reorder and edit-dock mode. False when there is nothing to move. */
    private fun beginReorder(): Boolean {
        if (adapter.movableCount() < 2) return false
        // Before the reload below: once it has dropped the empty slots, the dock row also shows grid apps.
        val dock = HashSet<String>()
        dockInSession = dock
        dockedAtStart = adapter.dockFilled()
        for (i in 0 until Math.min(dockedAtStart, adapter.movableCount())) dock.add(adapter.keyAt(i))
        adapter.setReordering(true)
        if (adapter.hasEmptySlots()) reloadTiles()   // every position must hold a tile that can move
        reordering = true
        orderAtStart = adapter.movableKeys()
        glass.setAccent(REORDER_ACCENT)
        btnReorder.isActivated = true
        return true
    }

    /** Edit dock (Settings menu): focus goes to the first dock place, which OK then chooses. */
    private fun startDockEdit() {
        if (reordering) endReorder()
        if (!beginReorder()) return
        dockEditing = true
        dockEditSlot = -1
        focusTile(0)
        Toast.makeText(this, R.string.dock_edit_hint, Toast.LENGTH_LONG).show()
    }

    /**
     * Keys in edit-dock mode. First OK on a dock tile chooses that place (the other dock tiles dim) and focus moves
     * to the grid; then OK on any tile swaps that app into the place, saves the order and ends the mode. Back goes
     * one step back: from choosing the app to choosing the place, and from there out of the mode.
     */
    private fun dispatchDockEdit(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
        if (back) {
            if (!down) {
                if (dockEditAppKey != null) {
                    endReorder()   // "Add to dock" cancelled before a place was chosen
                } else if (dockEditSlot >= 0) {
                    val slot = dockEditSlot
                    dockEditSlot = -1
                    dimOthers()
                    focusTile(slot)
                } else {
                    endReorder()
                }
            }
            return true
        }
        val pos = focusedTilePosition()
        if (ok && pos >= 0) {
            if (!down) {
                val n = adapter.movableCount()
                if (dockEditAppKey != null) {
                    if (pos < Math.min(COLUMNS, n)) {
                        val from = adapter.positionOf(dockEditAppKey)
                        if (from >= 0) swapIntoDock(pos, from)
                        else endReorder()
                    }
                } else if (dockEditSlot < 0) {
                    if (pos < Math.min(COLUMNS, n)) {
                        dockEditSlot = pos
                        dimOthers()
                        focusTile(Math.min(COLUMNS, n - 1))
                        Toast.makeText(this, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show()
                    }
                } else if (pos < n) {
                    swapIntoDock(dockEditSlot, pos)
                }
            }
            return true   // never opens the app while editing
        }
        return super.dispatchKeyEvent(event)   // arrows navigate as usual
    }

    /**
     * Edit-dock mode started from the tile menu. A dock app: its place is already chosen, so focus goes to the grid
     * for the app to put there. A grid app: it is already chosen, so focus goes to the dock for the place.
     */
    private fun startDockEditFor(key: String, inDock: Boolean) {
        if (reordering) endReorder()
        if (!beginReorder()) return
        dockEditing = true
        val pos = adapter.positionOf(key)   // beginReorder can rebuild the tiles
        val n = adapter.movableCount()
        if (pos < 0) {
            endReorder()
        } else if (inDock) {
            dockEditSlot = pos
            dimOthers()
            focusTile(Math.min(COLUMNS, n - 1))
            Toast.makeText(this, R.string.dock_edit_pick_app, Toast.LENGTH_LONG).show()
        } else {
            dockEditAppKey = key
            focusTile(0)
            Toast.makeText(this, R.string.dock_edit_pick_place, Toast.LENGTH_LONG).show()
        }
    }

    /** Puts the app at pos into dock place slot, and the app that was there where the chosen one was; then saves. */
    private fun swapIntoDock(slot: Int, pos: Int) {
        val slotKey = adapter.keyAt(slot)
        if (pos != slot) {
            keepScroll {
                adapter.move(pos, slot)
                adapter.move(adapter.positionOf(slotKey), pos)
            }
            dockInSession!!.add(adapter.keyAt(slot))
            if (pos >= COLUMNS) dockInSession!!.remove(slotKey)
        }
        endReorder()
        refocusAfterLayout(slot)
    }

    private fun pickUp(pos: Int) {
        if (pos >= adapter.movableCount()) return   // the "Default home" tile stays put
        movingKey = adapter.keyAt(pos)
        dimOthers()
    }

    /** Puts the carried tile down where it is; the mode stays on. */
    private fun drop() {
        movingKey = null
        dimOthers()
    }

    /** While a tile is carried, every other tile is dimmed so the carried one stands out; otherwise all are full. */
    private fun dimOthers() {
        for (i in 0 until grid.childCount) {
            val item = grid.getChildAt(i)
            item.alpha = tileAlpha(grid.getChildAdapterPosition(item))
        }
    }

    /** A tile's alpha: dim unless it is the carried one, or, in edit-dock mode, a dock tile other than the chosen place. */
    private fun tileAlpha(pos: Int): Float {
        val key = movingKey
        if (key != null) {
            return if (pos != RecyclerView.NO_POSITION && key == adapter.keyAt(pos)) 1f else 0.45f
        }
        if (dockEditSlot >= 0 && pos >= 0 && pos < COLUMNS && pos != dockEditSlot) return 0.45f
        return 1f
    }

    /** Ends the mode and saves the order if it changed since the mode started. */
    private fun endReorder() {
        if (!reordering) return
        dockEditing = false
        dockEditSlot = -1
        dockEditAppKey = null
        drop()
        reordering = false
        adapter.setReordering(false)
        glass.setAccent(if (festive) festival.accentColor() else 0)
        btnReorder.isActivated = false
        var keys = adapter.movableKeys()
        val before = orderAtStart
        orderAtStart = null
        // fill_dock false: the dock is what the owner put there, saved with its size, so the empty slots that
        // come back after the mode go after it (#499). With fill_dock true every dock slot holds an app anyway.
        var docked = -1
        if (fillDockLeavesSlots) {
            docked = DockSlots.dockCount(keys, COLUMNS, dockInSession!!)
            keys = DockSlots.dockFirst(keys, COLUMNS, dockInSession!!)
        }
        dockInSession = null
        if (keys != before || (fillDockLeavesSlots && docked != dockedAtStart)) {
            val order = Order(Order.dockKey(dockApps), keys, docked)
            if (Order.save(Order.file(this), order)) {
                adapter.setSavedOrder(Order.ranks(order, dockApps), Order.docked(order, dockApps))
            } else {
                Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show()
            }
        }
        if (fillDockLeavesSlots) reloadTiles()   // the empty dock slots left out while reordering come back
    }

    /**
     * Moves the carried tile one step. Left and Right wrap across rows, like a list; Up and Down move a whole row,
     * and Down into a short last row lands on its last tile. Nothing moves past either end or up into the top bar.
     */
    private fun moveBy(keyCode: Int) {
        val key = movingKey ?: run { drop(); return }
        val from = adapter.positionOf(key)
        val n = adapter.movableCount()
        if (from < 0 || from >= n) {
            drop()
            return
        }
        // A single row has no rows to move between: Up and Down leave the carried tile where it is.
        if (singleRow && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) return
        val to = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // rtl_app_grid (Hebrew) mirrors the grid: position 0 is on the right, so Left moves towards the end.
            if (rtlAppGrid) from + 1 else from - 1
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (rtlAppGrid) from - 1 else from + 1
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            from - COLUMNS
        } else if (from + COLUMNS < n) {
            from + COLUMNS
        } else if (from / COLUMNS < (n - 1) / COLUMNS) {
            n - 1
        } else {
            from
        }
        if (to < 0 || to >= n || to == from) return
        keepScroll { adapter.move(from, to) }
        // The owner carries this tile: where it lands decides whether it is in the dock, not dock_apps (#499).
        if (to < COLUMNS) dockInSession!!.add(key)
        else dockInSession!!.remove(key)
        refocusAfterLayout(to)
    }

    /**
     * Applies an adapter change without the grid jumping. After a move the layout manager anchors on the focused
     * tile, keeping it at its old place on screen and shifting every other row; pinning the first visible row at its
     * current offset as the next layout's anchor keeps the scroll where it was. onTileFocused still scrolls
     * afterwards if the moved tile itself ends up off screen.
     */
    private fun keepScroll(change: Runnable) {
        val lm = grid.layoutManager as GridLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val firstView = if (first != RecyclerView.NO_POSITION) lm.findViewByPosition(first) else null
        // single_row scrolls sideways, so its first visible tile is pinned by its left edge instead; mirrored by
        // rtl_app_grid, the row is laid out from the right, and the offset counts from the right edge.
        val offset = if (firstView == null) 0
                else if (singleRow && rtlAppGrid) grid.width - grid.paddingRight - lm.getDecoratedRight(firstView)
                else if (singleRow) lm.getDecoratedLeft(firstView) - grid.paddingLeft
                else lm.getDecoratedTop(firstView) - grid.paddingTop
        change.run()
        if (firstView != null) lm.scrollToPositionWithOffset(first, offset)
    }

    /**
     * Once the grid has applied the change, put the glass (and focus, if the move dropped it) on the tile at pos.
     * The grid has a fixed size, so it applies adapter changes on the next animation frame rather than in a layout
     * pass; until then it cannot find the tile by position. Wait frame by frame (at most 10) for that. No focus
     * change fires for a moved tile, so focusTile's already-focused path calls onTileFocused itself.
     */
    private fun refocusAfterLayout(pos: Int) {
        grid.postOnAnimation(object : Runnable {
            var frames = 0

            override fun run() {
                if ((grid.hasPendingAdapterUpdates() || grid.isLayoutRequested) && ++frames < 10) {
                    grid.postOnAnimation(this)
                    return
                }
                focusTile(pos)
                dimOthers()   // views that came into view or were rebound during the move
            }
        })
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
            showHero(position, if (scroll) (r.top - dy).toFloat() else r.top.toFloat())
        } else {
            // Below the dock (or in a plain grid) the glow follows the tile without the big name; letting it
            // fade out left the grid on plain black.
            showGlowOnly(position)
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

    // ---- top shelf -------------------------------------------------------------------------------

    private fun showHero(position: Int, dockTopInRoot: Float) {
        val label = adapter.labelAt(position)
        val color = adapter.ambientColorAt(position)
        val y = dockTopInRoot - 14 * density - 26 * density - hero.height.toFloat()
        val changed = !label.contentEquals(heroName.text)
        if (cinematic && heroArtAlpha > 0f) showHeroArt(position)

        if (!animations || reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            hero.animate().cancel()
            ambient.animate().cancel()
            fillHero(position, label)
            hero.y = y
            hero.translationX = 0f
            hero.alpha = 1f
            if (ambientGlow) {
                setAmbient(color)
                ambient.alpha = 1f
            }
            heroShown = true
            return
        }

        if (!heroShown) {
            stopAmbientAnimation()
            hero.animate().cancel()
            ambient.animate().cancel()
            fillHero(position, label)
            hero.y = y
            hero.translationX = 0f
            hero.animate().alpha(1f).setDuration(260).start()
            if (ambientGlow) {
                setAmbient(color)
                ambient.animate().alpha(1f).setDuration(600).start()
            }
            heroShown = true
        } else if (changed) {
            if (advancedEffects) hero.animate().cancel()
            hero.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction {
                fillHero(position, label)
                hero.y = y
                hero.translationX = 12 * density
                hero.animate().alpha(1f).translationX(0f).setDuration(200).start()
            }.start()
            if (ambientGlow && advancedEffects) {
                transitionAmbient(color)
            } else if (ambientGlow && color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction {
                    setAmbient(color)
                    ambient.animate().alpha(1f).setDuration(500).start()
                }.start()
            }
        } else {
            hero.y = y
            // onPause cancels the fade-ins, and a second Home during one lands on the same tile, which starts none:
            // finish them, or the shelf and the glow stay dim until focus moves to another app.
            if (hero.alpha < 1f) hero.animate().alpha(1f).setDuration(260).start()
            if (ambientGlow && color == ambientColor && ambient.alpha < 1f) {
                ambient.animate().alpha(1f).setDuration(600).start()
            }
        }
    }

    /** The shelf's text for this dock app: its name and, on the cinematic shelf, "Last opened" and the dots. */
    private fun fillHero(position: Int, label: String) {
        heroName.text = label
        if (!cinematic) return
        updateHeroMeta(position)
        updateHeroDots(position)
    }

    // ---- cinematic shelf -------------------------------------------------------------------------

    /**
     * The parts that never change: a larger name, the meta line and the dots, the artwork and the scrim, at
     * scrimPercent of its strength (cinematic_scrim). cinematic_art 0 leaves the artwork out, 0 percent the scrim.
     */
    private fun setUpCinematicShelf(scrimPercent: Int) {
        heroName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
        heroName.letterSpacing = -0.025f
        heroMeta.visibility = View.INVISIBLE   // keeps its height, so the name does not jump when it fills in
        heroDots.visibility = if (heroDotsShown) View.VISIBLE else View.GONE
        heroArt.visibility = if (heroArtAlpha > 0f) View.VISIBLE else View.GONE
        // One stretched bitmap: apply its alpha while drawing instead of through an offscreen buffer.
        heroArt.forceHasOverlappingRendering(false)
        if (scrimPercent <= 0) return   // cinematic_scrim 0: no scrim at all
        val scrim = findViewById<View>(R.id.heroScrim)
        val shade = scrimDrawable()
        // On the bitmap's paint, not the view: a view's alpha would draw the full screen through an offscreen buffer.
        shade.alpha = scrimPercent * 255 / 100
        scrim.background = shade
        scrim.visibility = View.VISIBLE
    }

    /**
     * Darkens the side the shelf's text is on and the lower part of the screen, so the name, the dock and
     * the grid stay readable over the artwork. One small bitmap stretched over the screen, like the glow.
     */
    private fun scrimDrawable(): Drawable {
        val w = 480
        val h = 270
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rtl = resources.getBoolean(R.bool.rtl_menu_bar)
        val paint = Paint()
        paint.shader = LinearGradient(if (rtl) w.toFloat() else 0f, 0f, if (rtl) 0f else w.toFloat(), 0f,
                intArrayOf(0xD1060406.toInt(), 0x59060406.toInt(), 0x00060406), floatArrayOf(0f, 0.42f, 0.7f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x00060406, 0x00060406, 0xDB060406.toInt(), 0xFF060406.toInt()), floatArrayOf(0f, 0.28f, 0.58f, 0.76f),
                Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val scrim = BitmapDrawable(resources, bmp)
        scrim.setGravity(Gravity.FILL)
        return scrim
    }

    /** Swaps the backdrop to this app's artwork: fade out, swap, fade in, the way the shelf's name changes. */
    private fun showHeroArt(position: Int) {
        val key = adapter.keyAt(position) ?: return
        heroArt.animate().cancel()
        if (key == heroArtKey) {
            fadeHeroArt(420)
            return
        }
        var art = heroArtCache[key]
        if (art == null) {
            art = HeroArt.from(adapter.artworkAt(position))
            if (art != null) heroArtCache[key] = art
        }
        heroArtKey = key
        val next = art
        if (!animations || reducedMotion || heroArt.alpha == 0f) {
            heroArt.setImageBitmap(next)
            fadeHeroArt(600)
            return
        }
        heroArt.animate().alpha(0f).setDuration(140).withEndAction {
            heroArt.setImageBitmap(next)
            heroArt.animate().alpha(heroArtAlpha).setDuration(420).start()
        }.start()
    }

    private fun fadeHeroArt(ms: Int) {
        if (!animations) heroArt.alpha = heroArtAlpha
        else heroArt.animate().alpha(heroArtAlpha).setDuration(if (reducedMotion) Math.min(ms, Motion.REDUCED_MS).toLong() else ms.toLong()).start()
    }

    /** "Last opened yesterday, 9:40 PM", the time in white; nothing (but the same height) when it was never seen. */
    private fun updateHeroMeta(position: Int) {
        val whenMs = LastOpened.get(this, adapter.packageAt(position))
        if (whenMs <= 0) {
            heroMeta.visibility = View.INVISIBLE
            return
        }
        val rel = LastOpened.describe(this, whenMs)
        val text = getString(R.string.last_opened, rel)
        val line = SpannableString(text)
        val at = text.indexOf(rel)
        if (at >= 0) line.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), at, at + rel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        heroMeta.text = line
        heroMeta.visibility = View.VISIBLE
    }

    /** One dot per dock app, the focused one drawn long and white. Hebrew docks start on the right, so do the dots. */
    private fun updateHeroDots(position: Int) {
        if (!heroDotsShown) return
        val count = Math.min(adapter.dockFilled(), COLUMNS)
        heroDots.visibility = if (count > 1) View.VISIBLE else View.INVISIBLE
        if (heroDots.childCount != count) {
            heroDots.removeAllViews()
            for (i in 0 until count) {
                val dot = View(this)
                val bg = GradientDrawable()
                bg.cornerRadius = 3 * density
                dot.background = bg
                val lp = LinearLayout.LayoutParams((6 * density).toInt(), (6 * density).toInt())
                if (i > 0) lp.leftMargin = (6 * density).toInt()
                heroDots.addView(dot, lp)
            }
        }
        for (i in 0 until count) {
            val dot = heroDots.getChildAt(if (rtlAppGrid) count - 1 - i else i)
            val on = i == position
            (dot.background as GradientDrawable).setColor(if (on) 0xFFFFFFFF.toInt() else 0x4DFFFFFF.toInt())
            val lp = dot.layoutParams
            val w = ((if (on) 20 else 6) * density).toInt()
            if (lp.width != w) {
                lp.width = w
                dot.layoutParams = lp
            }
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture. It is painted once in white and tinted with the app's colour,
     * so changing apps neither repaints it on the CPU (about 11 ms) nor uploads it again.
     */
    private fun setAmbient(color: Int) {
        if (color == ambientColor && ambient.background != null) return
        ambientColor = color
        var glow = ambient.background as? BitmapDrawable
        if (glow == null) {
            val w = 480
            val h = 270
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (cinematic) {
                // Higher and stronger, from the corner away from the shelf's text: the app's colour floods
                // the top of the screen over its artwork, and the scrim keeps the text side dark.
                val rtl = resources.getBoolean(R.bool.rtl_menu_bar)
                paint.shader = RadialGradient(
                        w * (if (rtl) 0.20f else 0.80f), h * 0.06f, w * 0.78f,
                        intArrayOf(0xA6FFFFFF.toInt(), 0x47FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP)
            } else if (Config.GLOW_HORIZON == glowStyle) {
                // The colour washes down from the top edge and fades out three quarters of the way down,
                // with a brighter band along the top like light over a horizon.
                paint.shader = LinearGradient(0f, 0f, 0f, h * 0.75f,
                        intArrayOf(0x66FFFFFF.toInt(), 0x24FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.4f, 1f),
                        Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                        w * 0.5f, -h * 0.25f, w * 0.55f,
                        intArrayOf(0x40FFFFFF.toInt(), 0x00FFFFFF),
                        null,
                        Shader.TileMode.CLAMP)
            } else {
                paint.shader = RadialGradient(
                        w * 0.32f, h * 0.42f, w * 0.62f,
                        intArrayOf(0x5CFFFFFF.toInt(), 0x26FFFFFF.toInt(), 0x00FFFFFF),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            glow = BitmapDrawable(resources, bmp)
            glow.setGravity(Gravity.FILL)
            ambient.background = glow
        }
        // The default tint mode, SRC_IN, keeps the mask's alpha and takes the colour from the tint.
        glow.setTint(0xFF000000.toInt() or color)
    }

    private fun transitionAmbient(color: Int) {
        // A rapid focus change continues from the colour actually displayed on screen.
        stopAmbientAnimation()
        ambient.animate().cancel()
        if (color == ambientColor) {
            ambient.animate().alpha(1f).setDuration(180).start()
            return
        }
        ambient.animate().alpha(1f).setDuration(180).start()
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), ambientColor, color)
        ambientAnimator = animator
        animator.duration = 360
        animator.addUpdateListener { animation -> setAmbient(animation.animatedValue as Int) }
        animator.start()
    }

    private fun stopAmbientAnimation() {
        ambientAnimator?.cancel()
        ambientAnimator = null
    }

    /**
     * Grid tiles (every tile with dock_row false): the glow behind the grid takes the focused tile's colour; the big
     * name above the dock does not show (every grid tile has its name underneath).
     */
    private fun showGlowOnly(position: Int) {
        val color = adapter.ambientColorAt(position)
        stopAmbientAnimation()
        hero.animate().cancel()
        ambient.animate().cancel()
        hero.alpha = 0f
        heroArt.animate().cancel()
        heroArt.alpha = 0f
        heroShown = false
        if (!ambientGlow) return
        if (!animations || reducedMotion) {
            setAmbient(color)
            ambient.alpha = 1f
            return
        }
        if (color == ambientColor && ambient.background != null) {
            ambient.animate().alpha(1f).setDuration(300).start()
            return
        }
        // The same dip-and-swap as the dock's top shelf.
        ambient.animate().alpha(0.35f).setDuration(if (ambient.alpha > 0.35f) 200L else 0L).withEndAction {
            setAmbient(color)
            ambient.animate().alpha(1f).setDuration(500).start()
        }.start()
    }

    private fun hideHero() {
        if (!heroShown) return
        stopAmbientAnimation()
        hero.animate().cancel()
        ambient.animate().cancel()
        heroArt.animate().cancel()
        heroShown = false
        if (animations && !reducedMotion) {
            hero.animate().alpha(0f).setDuration(160).start()
            if (ambientGlow) ambient.animate().alpha(0f).setDuration(300).start()
            heroArt.animate().alpha(0f).setDuration(300).start()
        } else {
            hero.alpha = 0f
            ambient.alpha = 0f
            heroArt.alpha = 0f
        }
    }

    private fun hideGlass() {
        if (!glassShown) return
        glassShown = false
        glass.animate().cancel()
        if (animations && !reducedMotion) glass.animate().alpha(0f).setDuration(160).start()
        else glass.alpha = 0f
    }

    // ---- optional system stats (config.xml show_stats) --------------------------------------------

    /** Background reader. Started only while Home is resumed and show_stats is on. */
    private var statsThread: HandlerThread? = null
    /** The current run's handler; a run whose handler is no longer this one stops (volatile: read off-thread). */
    @Volatile
    private var statsBg: Handler? = null

    private fun startStats() {
        if (statsThread != null) return
        stats?.visibility = View.VISIBLE
        cpuTotalPrev = -1
        idlePrev = -1
        procStatDead = false
        val thread = HandlerThread("stats")
        statsThread = thread
        thread.start()
        // Each start gets its own handler, held by its task: stopStats on the main thread can clear the field
        // at any moment without the reader tripping over it.
        val bg = Handler(thread.looper)
        statsBg = bg
        bg.post(object : Runnable {
            override fun run() {
                if (statsBg != bg) return
                val line = readStatsLine()
                handler.post {
                    val s = stats
                    if (showStats && s != null && statsBg == bg) s.text = line
                }
                if (statsBg == bg) bg.postDelayed(this, STATS_MS)
            }
        })
    }

    private fun stopStats() {
        if (statsThread == null) return
        statsBg!!.removeCallbacksAndMessages(null)
        statsThread!!.quit()
        statsThread = null
        statsBg = null
        // Paused (the quick panel or an app on top): keep the last line on screen. Taking it away moved the grid up
        // under a glass highlight that stayed put.
        if (!showStats) stats?.visibility = View.GONE
    }

    private fun readStatsLine(): String {
        var cpu = cpuPercent()
        if (cpu < 0) {
            try {
                Thread.sleep(200)             // second sample, so the first line already has a CPU figure
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return ""
            }
            cpu = cpuPercent()
        }
        // Android 8 and later hide /proc/stat from apps on many TVs: then the line leaves CPU out rather than
        // showing a dash that never fills in.
        val line = StringBuilder()
        if (cpu >= 0) line.append(getString(R.string.stats_cpu, cpu))
        val mem = memUsedOfTotalMb()
        if (mem != null) appendStat(line, getString(R.string.stats_memory, mem[0], mem[1]))
        val free = formatGb(resources, Environment.getDataDirectory().freeSpace)
        if (free.isNotEmpty()) appendStat(line, getString(R.string.stats_storage, free))
        return StatsText.forLocale(line.toString(), resources.configuration.locales[0])
    }

    private var cpuTotalPrev = -1L
    private var cpuIdlePrev = -1L
    private var procStatDead = false
    private var idlePrev = -1L
    private var wallPrev = -1L

    private fun cpuPercent(): Int {
        if (!procStatDead) {
            val fromStat = cpuFromProcStat()
            if (fromStat != CPU_UNREADABLE) return fromStat
            procStatDead = true
            idlePrev = -1
        }
        return cpuFromIdle()
    }

    private fun cpuFromProcStat(): Int {
        val s = readFirstLine("/proc/stat")
        if (s == null || !s.startsWith("cpu")) return CPU_UNREADABLE
        val t = s.trim().split("\\s+".toRegex())
        if (t.size < 5) return CPU_UNREADABLE
        try {
            val user = java.lang.Long.parseLong(t[1])
            val nice = java.lang.Long.parseLong(t[2])
            val sys = java.lang.Long.parseLong(t[3])
            val idle = java.lang.Long.parseLong(t[4])
            val iowait = if (t.size > 5) java.lang.Long.parseLong(t[5]) else 0
            val irq = if (t.size > 6) java.lang.Long.parseLong(t[6]) else 0
            val soft = if (t.size > 7) java.lang.Long.parseLong(t[7]) else 0
            val total = user + nice + sys + idle + iowait + irq + soft
            val idleAll = idle + iowait
            if (cpuTotalPrev < 0) {
                cpuTotalPrev = total
                cpuIdlePrev = idleAll
                return -1
            }
            val dt = total - cpuTotalPrev
            val di = idleAll - cpuIdlePrev
            cpuTotalPrev = total
            cpuIdlePrev = idleAll
            if (dt <= 0) return -1
            val busy = Math.round((dt - di) * 100.0 / dt).toInt()
            return Math.max(0, Math.min(100, busy))
        } catch (_: NumberFormatException) {
            return CPU_UNREADABLE
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. -1 until the second sample. */
    private fun cpuFromIdle(): Int {
        var idle = 0L
        var cores = 0
        for (i in 0 until 8) {
            var any = false
            for (state in 0 until 8) {
                val t = readFirstLine("/sys/devices/system/cpu/cpu" + i
                        + "/cpuidle/state" + state + "/time")
                if (t == null) break
                try {
                    idle += java.lang.Long.parseLong(t.trim())
                    any = true
                } catch (_: NumberFormatException) {
                    break
                }
            }
            if (!any) break
            cores++
        }
        if (cores == 0) return -1
        val wall = SystemClock.elapsedRealtime() * 1000L
        if (idlePrev < 0) {
            idlePrev = idle
            wallPrev = wall
            return -1
        }
        val di = idle - idlePrev
        val dw = (wall - wallPrev) * cores
        idlePrev = idle
        wallPrev = wall
        if (dw <= 0) return -1
        val busy = Math.round((dw - di) * 100.0 / dw).toInt()
        return Math.max(0, Math.min(100, busy))
    }

    /** RAM usage as {used, total} in MB, used = MemTotal - MemAvailable, e.g. "945 / 1536 MB". Used over
     *  free so the line reads as a load against a known whole; null when /proc/meminfo cannot be read. */
    private fun memUsedOfTotalMb(): LongArray? {
        var available = -1L
        var total = -1L
        try {
            BufferedReader(FileReader("/proc/meminfo")).use { r ->
                var line = r.readLine()
                while (line != null) {
                    if (line.startsWith("MemAvailable:")) {
                        val t = line.split("\\s+".toRegex())
                        if (t.size >= 2) available = java.lang.Long.parseLong(t[1]) / 1024
                    } else if (line.startsWith("MemTotal:")) {
                        val t = line.split("\\s+".toRegex())
                        if (t.size >= 2) total = java.lang.Long.parseLong(t[1]) / 1024
                    }
                    line = r.readLine()
                }
            }
        } catch (_: IOException) {
        } catch (_: NumberFormatException) {
        }
        if (available < 0 || total <= 0) return null
        return longArrayOf(Math.max(0, total - available), total)
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
        hideHero()
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

    // ---- race-weekend panel (a theme's race_calendar) -----------------------------------------------

    /**
     * Shows or hides the race-weekend panel for this minute. Text only, set once a minute with the clock:
     * nothing animates, so the panel costs the GPU nothing between ticks.
     */
    /**
     * The race panel's place under the clock. With game_day off it is exactly the F1 panel (updateRace). With it on,
     * each team's next game takes a turn there, and the theme's F1 weekend is one more turn drawn as today.
     */
    private fun updatePanel() {
        val day = gameDay
        if (day == null) {
            updateRace()
            return
        }
        handler.removeCallbacks(turnTick)
        val now = System.currentTimeMillis()
        val games = day.turns()
        for (g in games) {
            if (g.endMs <= now && now - lastEndedRead > MINUTE_MS) {
                // A game is over: the add-on already has that team's next one.
                lastEndedRead = now
                day.read()
                break
            }
        }
        val f1 = if (raceCalendar.isEmpty()) null else RaceWeekend.at(raceCalendar, Instant.ofEpochMilli(now))
        val turns = GameDay.merge(games, f1, now)
        if (turns.isEmpty()) {
            racePanel.visibility = View.GONE
            return
        }
        val seconds = day.turnSeconds()
        val since = SystemClock.elapsedRealtime() - rotationStart
        val on = GameDay.current(turns.size, since, seconds)
        val t = turns[on]
        if (t.f1) {
            raceBar.setBackgroundResource(R.drawable.race_bar)
            raceSessions.visibility = View.VISIBLE
            updateRace()
        } else {
            val bar = GradientDrawable()
            bar.cornerRadius = 1.5f * density
            bar.setColor(t.colour)
            raceBar.background = bar
            raceKicker.text = t.topLine
            if (GameDay.live(t, now)) {
                val live = SpannableString("\u25CF  " + getString(R.string.game_live))
                live.setSpan(ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                raceHeadline.text = live
            } else {
                val countdown = RaceWeekend.countdown(resources, GameDay.until(t, now))
                raceHeadline.text = if (t.word.isEmpty()) countdown else getString(R.string.race_next, t.word, countdown)
            }
            // No session times for a game: the line takes no room, so the dots sit under the countdown.
            raceSessions.visibility = View.GONE
            racePanel.visibility = View.VISIBLE
        }
        showPager(turns.size, on)
        if (turns.size > 1 && !stopped) handler.postDelayed(turnTick, GameDay.delayToNextTurn(since, seconds))
    }

    /** One dot per turn, the one on screen bright; none for a single turn. */
    private fun showPager(count: Int, on: Int) {
        if (count < 2) {
            racePager.visibility = View.GONE
            return
        }
        if (racePager.childCount != count) {
            racePager.removeAllViews()
            val size = Math.round(5 * density)
            for (k in 0 until count) {
                val dot = View(this)
                val d = GradientDrawable()
                d.shape = GradientDrawable.OVAL
                d.setColor(0xFFFFFFFF.toInt())
                dot.background = d
                val lp = LinearLayout.LayoutParams(size, size)
                lp.marginEnd = size
                racePager.addView(dot, lp)
            }
        }
        for (k in 0 until count) racePager.getChildAt(k).alpha = if (k == on) 0.9f else 0.3f
        racePager.visibility = View.VISIBLE
    }

    private fun updateRace() {        // an empty calendar (a theme without race_calendar, or a reset) hides a panel the last theme showed
        val s = if (raceCalendar.isEmpty()) null else RaceWeekend.at(raceCalendar, Instant.now())
        if (s == null) {
            racePanel.visibility = View.GONE
            return
        }
        val race = s.race
        raceKicker.text = if (race.place.isEmpty())
                getString(R.string.race_kicker_no_place, race.round, race.shortName())
                else getString(R.string.race_kicker, race.round, race.shortName(), race.place)

        if (s.live != null) {
            val live = SpannableString("\u25CF  " + getString(R.string.race_live, s.live!!.title(resources)))
            live.setSpan(ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            raceHeadline.text = live
        } else if (s.next != null) {
            // untilNext is set whenever next is (RaceWeekend.at builds it from next).
            val countdown = RaceWeekend.countdown(resources, s.untilNext!!)
            raceHeadline.text = if (s.next!!.isRace()) getString(R.string.race_lights_out, countdown)
                    else getString(R.string.race_next, s.next!!.title(resources), countdown)
        } else {
            raceHeadline.setText(R.string.race_finished)
        }

        // FP1 FRI 11:00 PM   FP2 SAT 2:30 AM ...: done sessions fade, the live one is red, the next one bright.
        val formatter = DateTimeFormatter.ofPattern(
                if (android.text.format.DateFormat.is24HourFormat(this)) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
        val row = SpannableStringBuilder()
        for (i in race.sessions.indices) {
            val session = race.sessions[i]
            if (i > 0) row.append("     ")
            val start = row.length
            row.append(session.shortName(resources)).append("  ").append(formatter.format(session.start).uppercase(Locale.getDefault()))
            val color = when (s.statuses[i]) {
                RaceWeekend.Status.DONE -> 0x47FFFFFF.toInt()
                RaceWeekend.Status.LIVE -> RACE_RED
                RaceWeekend.Status.NEXT -> 0xFFFFFFFF.toInt()
                else -> 0x99FFFFFF.toInt()
            }
            row.setSpan(ForegroundColorSpan(color), start, row.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        raceSessions.text = row
        racePanel.visibility = View.VISIBLE
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
        var any = liveTvIntent() != null || sourcePanelService.isNotEmpty()
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
        val app = if (openOnStartChoice) getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "") else ""
        if (!bootToLiveTv && app.isEmpty()) return false
        val boot = OpenOnStart.boot(this)
        val prefs = getSharedPreferences(OpenOnStart.BOOT_PREFS, MODE_PRIVATE)
        val target = if (bootToLiveTv) liveTvPkg else app
        if (!OpenOnStart.due(boot, prefs.getInt(OpenOnStart.BOOT_KEY, -1), target, packageName)) return false
        if (!prefs.edit().putInt(OpenOnStart.BOOT_KEY, boot).commit()) return false
        val i = if (bootToLiveTv) liveTvIntent() else OpenOnStart.intent(packageManager, app)
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

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private fun liveTvIntent(): Intent? {
        if (liveTvPkg.isEmpty()) return null
        val i = packageManager.getLeanbackLaunchIntentForPackage(liveTvPkg)
        if (i != null || liveTvAction.isEmpty()) return i
        val byAction = Intent(liveTvAction).setPackage(liveTvPkg)
        return if (packageManager.resolveActivity(byAction, 0) != null) byAction else null
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9): a
     *  service, or an activity on sets whose panel is a dialog (BYINTEK U4's QuickSourceActivity). */
    private fun openSourcePanel() {
        val i = Intent(sourcePanelAction)
        val c = ComponentName.unflattenFromString(sourcePanelService)
        if (c != null) i.component = c
        val eq = sourcePanelExtra.indexOf('=')
        if (eq > 0) {
            try {
                i.putExtra(sourcePanelExtra.substring(0, eq).trim(),
                        sourcePanelExtra.substring(eq + 1).trim().toInt())
            } catch (_: NumberFormatException) {
            }
        }
        try {
            if (packageManager.resolveActivity(i, 0) != null) {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            startService(i)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
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

    /** `input_sources` in config.xml or a theme: "HW5=23" maps the framework input whose id ends in /HW5 to a vendor
     *  source number; "HW8=-" leaves that input out of the Inputs menu (a port the board reports but the set
     *  does not have). Returns the number, -1 for a left-out input, or null when the input is not listed. */
    private fun mappedSource(inputId: String): Int? {
        return InputSources.mapped(inputSources, inputId)
    }

    /** Switches to a passthrough input (HDMI, AV). An input mapped in `input_sources` is opened by sending
     *  `input_action` with its source number as the int extra "input" (Xiaomi China firmware, whose tvplayer
     *  answers com.xiaomi.mitv.tvplayer.EXTSRC_PLAY and nothing else). Otherwise the standard way is
     *  ACTION_VIEW on its channel URI, which the Live TV app handles; where nothing takes that, the
     *  configured `input_action` is sent with the same URI instead (XGIMI's HDMI player only answers
     *  com.xgimi.action.hdmiPlayer). With `input_uri_scheme` set (YaOS: live-tv) the same URI goes out
     *  with that scheme and no MIME type. A player guarded by a permission this build does not declare
     *  throws SecurityException, which used to take Home down with it (YaOS before the manifest asked
     *  for ACCESS_TV_SOURCE). */
    private fun openInput(inputId: String) {
        // A CEC device is a child input of its HDMI port; the vendor source number belongs to the port
        // (MiTV-MFFU0). Only that lookup uses the parent: the generic path below still opens the child.
        var sourceId = inputId
        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        if (tim != null) {
            try {
                val info = tim.getTvInputInfo(inputId)
                if (info != null && info.parentId != null) sourceId = info.parentId
            } catch (_: RuntimeException) {
            }
        }
        val source = mappedSource(sourceId)
        if (source != null && source >= 0 && inputAction.isNotEmpty()) {
            try {
                startActivity(Intent(inputAction).putExtra("input", source)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // fall through to the standard way
            } catch (_: SecurityException) {
                // fall through to the standard way
            }
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        val view = (if (inputUriScheme.isEmpty())
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                else Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(inputUriScheme).build()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Aim it at the TV app first: apps installed later (a streaming app, a file manager) can claim the same
        // channel URI too, and with two or more handlers Android shows a chooser instead of the input (WhaleOS).
        if (liveTvPkg.isNotEmpty()) {
            try {
                startActivity(Intent(view).setPackage(liveTvPkg))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        try {
            startActivity(view)
            return
        } catch (_: ActivityNotFoundException) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (_: SecurityException) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
                return
            }
        }
        open(Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"))
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private fun showInputs() {
        // A configured source panel and no Live TV row to offer next to it: the panel is the whole menu.
        // The BYINTEK U4 needs this, as its TvInputManager lists three HDMI ports and an AV input it doesn't have.
        val coocaa = if (coocaaSources) CoocaaSources.list(this) else Collections.emptyList<CoocaaSources.Source>()
        if (coocaa.isEmpty() && sourcePanelService.isNotEmpty() && liveTvIntent() == null) {
            openSourcePanel()
            return
        }
        // top_menu: the pill is gone, so the list hangs under the gear, growing inward from the right.
        // Read the switch, not the pill: show_inputs_pill=false hides the pill too, and there the list
        // keeps its usual place.
        val underGear = topMenu
        val anchor = findViewById<View>(if (underGear) R.id.btnSettings else R.id.btnInputs)
        val panel = layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        val width = Math.max(anchor.width, (150 * density).toInt())
        val popup = glassPopup(panel, width)

        val tim = getSystemService(TV_INPUT_SERVICE) as? TvInputManager
        // inputs_connected_only: ports without a connected device and the AV jacks fold behind a "More inputs" row.
        val connectedOnly = inputsConnectedOnly
        val folded: MutableList<View> = ArrayList()
        var sawNonHdmi = false
        // An explicit root URI asks the Live TV app to leave any HDMI source selected by its own UI.
        val rootChannel = TvContract.Channels.CONTENT_URI.toString() == liveTvInputId
        var tunerId: String? = null
        var tunerIsHw = false
        var tunerIsAnalog = false
        if (tim != null) {
            val inputs = ArrayList(tim.tvInputList)
            // HDMI ports first in port order, then AV and the rest.
            inputs.sortWith(Comparator { a, b ->
                val ha = a.type == TvInputInfo.TYPE_HDMI
                val hb = b.type == TvInputInfo.TYPE_HDMI
                if (ha != hb) {
                    if (ha) -1 else 1
                } else {
                    String.valueOf(a.loadLabel(this)).compareTo(String.valueOf(b.loadLabel(this)), ignoreCase = true)
                }
            })
            // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
            // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
            // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
            // only when nothing on it speaks CEC.
            val inputIds = HashSet<String>()
            for (info in inputs) inputIds.add(info.id)
            val cecDevices: MutableMap<String, MutableList<TvInputInfo>> = HashMap()
            for (info in inputs) {
                val parent = info.parentId
                if (parent == null || !inputIds.contains(parent)) continue
                if (!info.isPassthroughInput || info.isHidden(this)) continue
                var onPort = cecDevices[parent]
                if (onPort == null) {
                    onPort = ArrayList()
                    cecDevices[parent] = onPort
                }
                onPort.add(info)
            }
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            var hdmiPorts = 0
            var hdmiConnected = 0
            for (info in inputs) {
                if (!info.isPassthroughInput || info.type != TvInputInfo.TYPE_HDMI) continue
                if (info.parentId != null && inputIds.contains(info.parentId)) continue
                hdmiPorts++
                if (tim.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++
            }
            val hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts
            for (info in inputs) {
                if (info.type == TvInputInfo.TYPE_TUNER
                        && (info.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        && (liveTvInputId.isEmpty() || rootChannel || liveTvInputId == info.id)) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    // Among tuners of the same kind, skip an analog input when another is present.
                    // MediaTek names them AnalogInputService and TunerInputService; alphabetical order
                    // would pick the analog one, and Live TV then opens a dead ATV source.
                    val hw = info.id.matches(".*/HW\\d+$".toRegex())
                    val analog = info.id.lowercase(Locale.ROOT).contains("analog")
                    val currentTuner = tunerId
                    val better = currentTuner == null
                            || (hw && !tunerIsHw)
                            || (hw == tunerIsHw && tunerIsAnalog && !analog)
                            || (hw == tunerIsHw && analog == tunerIsAnalog
                                    && info.id.compareTo(currentTuner) < 0)
                    if (better) {
                        tunerId = info.id
                        tunerIsHw = hw
                        tunerIsAnalog = analog
                    }
                }
                // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
                if (!info.isPassthroughInput || info.isHidden(this)) continue
                // input_sources can leave out ports the board reports but the set does not have ("HW8=-").
                if (mappedSource(info.id) == -1) continue
                // A CEC device's row is added with its port, below.
                if (info.parentId != null && inputIds.contains(info.parentId)) continue
                val l = info.loadLabel(this)
                var label = l?.toString() ?: info.id
                val hdmi = info.type == TvInputInfo.TYPE_HDMI
                if (hdmi && !label.uppercase(Locale.ROOT).contains("HDMI")) label = "HDMI $label"
                val custom = InputNames.tidy(info.loadCustomLabel(this))
                val named = custom != null && custom.isNotEmpty() && custom != label
                if (!hdmi && rows.childCount > 0 && !sawNonHdmi) {
                    val line = addSeparator(rows)
                    if (connectedOnly) fold(line, folded)
                }
                if (!hdmi) sawNonHdmi = true
                val devices = cecDevices[info.id]
                if (devices != null) {
                    // "Apple TV · HDMI 1": the device's own name (or the one given to it or to its port in
                    // Settings > Inputs), then the port. Opening the device's input also makes it the CEC
                    // active source, which wakes it.
                    for (device in devices) {
                        var name: CharSequence? = InputNames.tidy(device.loadCustomLabel(this))
                        if (name == null || name.isEmpty()) name = if (named) custom else device.loadLabel(this)
                        val deviceLabel = if (name == null || name.isEmpty()) label else "$name \u00B7 $label"
                        val deviceId = device.id
                        val state = if (hdmiStateBogus) -1 else tim.getInputState(deviceId)
                        val row = addInputRow(rows, popup, R.drawable.ic_input, deviceLabel, state) { openInput(deviceId) }
                        if (connectedOnly && InputFold.folds(true, state)) fold(row, folded)
                    }
                    continue
                }
                // The name given in Settings > Inputs (e.g. "MacBook Pro") goes first, the port after it.
                if (named) label = custom!! + " \u00B7 " + label
                val id = info.id
                // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
                // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
                val state = if (!hdmi || hdmiStateBogus) -1 else tim.getInputState(id)
                val row = addInputRow(rows, popup, if (hdmi) R.drawable.ic_input else R.drawable.ic_av, label, state) { openInput(id) }
                if (connectedOnly && InputFold.folds(hdmi, state)) fold(row, folded)
            }
        }
        if (folded.isNotEmpty()) {
            val more = layoutInflater.inflate(R.layout.item_input, rows, false)
            more.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_input)
            more.findViewById<TextView>(R.id.title).setText(R.string.more_inputs)
            more.findViewById<View>(R.id.status).visibility = View.GONE
            more.findViewById<View>(R.id.dot).visibility = View.GONE
            more.setOnClickListener {
                for (f in folded) f.visibility = View.VISIBLE
                rows.removeView(more)
                for (f in folded) {
                    if (f.isFocusable) { f.requestFocus(); break }
                }
            }
            rows.addView(more)
        }
        // Coocaa OS registers nothing with TvInputManager; com.skyworth.tv lists its sources instead.
        if (rows.childCount == 0) {
            for (s in coocaa) {
                val icon = if ("HDMI" == s.name) R.drawable.ic_input
                        else if ("VOD" == s.name) R.drawable.ic_livetv else R.drawable.ic_av
                addInputRow(rows, popup, icon, s.label, -1) {
                    if (!CoocaaSources.open(this, s)) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.childCount == 0 && sourcePanelService.isNotEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel)
        }
        var liveTv = liveTvIntent()
        if (liveTv != null && tunerId != null) {
            // Tune that input only when the Live TV package itself handles the URI.
            // The unrestricted input player also resolves it (that is how HDMI opens), but on the
            // AIWA AI PONT SA that player stays black, and the Live TV package does not handle the
            // URI at all, which used to toast "Not available on this TV". Keep the leanback activity
            // then: it is the same path as the Live TV tile. Do not drop the package restriction.
            // An explicit tuner uses its channel collection (e.g. DVB-S rather than DVB-T/C).
            // Keep the legacy passthrough route for automatic selection: some OEM players need it.
            // If the configured input is absent, tunerId stays null and the app opens normally.
            val configured = liveTvInputId.isNotEmpty() && !rootChannel
            val tuned = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(if (configured) TvContract.buildChannelsUriForInput(tunerId)
                                    else TvContract.buildChannelUriForPassthroughInput(tunerId),
                            if (configured) TvContract.Channels.CONTENT_TYPE else TvContract.Channels.CONTENT_ITEM_TYPE)
                    .setPackage(liveTvPkg)
            val handler = tuned.resolveActivity(packageManager)
            if (handler != null) {
                // On some MediaTek sets the tuner passthrough URI leaves the app's HDMI source selected.
                // The stock launcher addresses the same activity with the root channel URI instead (#1941).
                liveTv = if (rootChannel)
                        Intent(Intent.ACTION_VIEW).setData(TvContract.Channels.CONTENT_URI).setComponent(handler)
                        else tuned
            }
        }
        if (liveTv != null) {
            if (rows.childCount > 0) addSeparator(rows)
            val liveTvIntent = liveTv
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1) { open(liveTvIntent) }
        }
        if (rows.childCount == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.width = Math.max(width, Math.min(panel.measuredWidth, (360 * density).toInt()))

        val alignment = if (resources.getBoolean(R.bool.rtl_menu_bar) != underGear) Gravity.END else Gravity.START
        dropIn(popup, panel, anchor, alignment)
        for (i in 0 until rows.childCount) {
            val child = rows.getChildAt(i)
            if (child.visibility == View.VISIBLE && child.isFocusable) { child.requestFocus(); break }
        }
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

    private fun addSeparator(rows: LinearLayout): View {
        val line = View(this)
        line.setBackgroundColor(0x1FFFFFFF)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, density.toInt())
        val m = (6 * density).toInt()
        val h = (12 * density).toInt()
        lp.setMargins(h, m, h, m)
        rows.addView(line, lp)
        return line
    }

    private fun addInputRow(rows: LinearLayout, popup: PopupWindow, iconRes: Int, title: String, state: Int, action: Runnable): View {
        val row = layoutInflater.inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).text = title
        val status = row.findViewById<TextView>(R.id.status)
        val dot = row.findViewById<View>(R.id.dot)
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected)
            dot.visibility = View.VISIBLE
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected)
            dot.visibility = View.GONE
        } else {
            status.visibility = View.GONE
            dot.visibility = View.GONE
        }
        row.setOnClickListener {
            popup.dismiss()
            action.run()
        }
        rows.addView(row)
        return row
    }

    // ---- launcher settings and module cards -------------------------------------------------------

    /** top_menu: the gear's dropdown, standing in for the Inputs, Wi-Fi and Settings pills. */
    private fun showTopMenu() {
        val anchor = findViewById<View>(R.id.btnSettings)
        val panel = layoutInflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (220 * density).toInt())
        // Inputs opens its own list once this one has closed.
        if (showInputsMenuRow) addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1) { root.post { showInputs() } }
        addInputRow(rows, popup, R.drawable.ic_wifi, getString(R.string.quick_network), -1, this::openNetworkPill)
        val network: CharSequence? = if (wifiIconOnly) btnWifi.contentDescription else btnWifi.text
        if (!TextUtils.isEmpty(network)) {
            val status = rows.getChildAt(rows.childCount - 1).findViewById<TextView>(R.id.status)
            status.text = network
            status.visibility = View.VISIBLE
        }
        addInputRow(rows, popup, R.drawable.ic_swap_vert, getString(R.string.reorder), -1, this::toggleReorder)
        if (launcherSettingsButton) {
            addInputRow(rows, popup, R.drawable.ic_home_settings_pill, getString(R.string.launcher_settings), -1) {
                startActivityForResult(Intent(this, LauncherSettingsActivity::class.java), LAUNCHER_SETTINGS_REQUEST)
            }
        } else {
            addInputRow(rows, popup, R.drawable.ic_settings, getString(R.string.settings), -1) {
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
        if (moduleCards) addSettingsMenuRow(rows, popup, R.string.module_cards, this::showModuleCards)
        if (dockRow) addSettingsMenuRow(rows, popup, R.string.dock_edit, this::startDockEdit)
        addSettingsMenuRow(rows, popup, R.string.reorder, this::toggleReorder)
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
        openInput(inputId)
    }

    /**
     * Hold OK on a tile: a glass menu next to it. Move starts reorder mode with this tile already picked up;
     * Remove from Home takes the shortcut off (only with the app library on, where one press adds it back);
     * App info opens the TV's page for the app, which is what holding OK used to do.
     */
    override fun onTileLongPress(position: Int, card: View) {
        if (reordering) return
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
                startDockEditFor(key, inDock)
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

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private fun showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing
                || reordering) return
        if (cards.isEmpty() || !CardFeature.working()) return
        CardFeature.run<Unit?>({
            showCardPromptNow()
            null
        }, null)
    }

    private fun showCardPromptNow() {
        val card = Cards.firstPending(cards) ?: return

        val panel = layoutInflater.inflate(R.layout.dialog_card_prompt, root, false)
        val rtl = resources.getBoolean(R.bool.rtl_menu_bar)
        if (rtl) panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        panel.findViewById<TextView>(R.id.title).text = getString(R.string.card_prompt_title, card.appLabel)
        panel.findViewById<TextView>(R.id.body).text = if (card.state == Cards.State.RESIGNED)
                getString(R.string.card_prompt_resigned, card.appLabel)
                else if (card.state == Cards.State.RELABELLED)
                getString(R.string.card_prompt_relabelled, card.appLabel)
                else getString(R.string.card_prompt_body, card.appLabel)
        val names = panel.findViewById<TextView>(R.id.names)
        if (Cards.fold(card.label) == Cards.fold(card.appLabel)) names.visibility = View.GONE
        else names.text = getString(R.string.card_prompt_names, card.label, card.appLabel)
        panel.findViewById<TextView>(R.id.identity).text =
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest))
        val keep = panel.findViewById<TextView>(R.id.btnKeep)
        val show = panel.findViewById<TextView>(R.id.btnShow)
        if (rtl) {
            val buttons = keep.parent as LinearLayout
            buttons.removeAllViews()
            buttons.addView(show)
            buttons.addView(keep)
            keep.nextFocusLeftId = R.id.btnShow
            keep.nextFocusRightId = R.id.btnKeep
            show.nextFocusLeftId = R.id.btnShow
            show.nextFocusRightId = R.id.btnKeep
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(false)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((440 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.6f)
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        var decided = false
        val choose = View.OnClickListener { v ->
            if (!decided) {
                decided = true
                cardPrompt = null
                dialog.dismiss()
                decideCard(card, v.id == R.id.btnShow)   // guards its own card calls; the tile reload stays outside
                restoreFocus()   // the tiles changed under the prompt; this also chains the next prompt
            }
        }
        keep.setOnClickListener(choose)
        show.setOnClickListener(choose)
        dialog.setOnCancelListener { choose.onClick(keep) }
        dialog.setOnDismissListener {
            if (cardPrompt === dialog) cardPrompt = null
        }
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        val keys = View.OnKeyListener { v, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                true
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                v.id == (if (rtl) R.id.btnShow else R.id.btnKeep)
            } else {
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        && v.id == (if (rtl) R.id.btnKeep else R.id.btnShow)
            }
        }
        keep.setOnKeyListener(keys)
        show.setOnKeyListener(keys)
        cardPrompt = dialog
        dialog.show()
        keep.requestFocus()
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private fun decideCard(card: Cards.Card, show: Boolean) {
        val saved = CardFeature.run({ Cards.decide(Cards.storeFile(this), card, show) }, false)
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show()
            promptsPaused = true
        }
        reloadTiles()
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private fun showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show()
            return
        }
        CardFeature.run<Unit?>({
            showModuleCardsNow()
            null
        }, null)
    }

    private fun showModuleCardsNow() {
        val anchor = findViewById<View>(R.id.btnSettings)
        val panel = layoutInflater.inflate(R.layout.popup_cards, root, false)
        if (resources.getBoolean(R.bool.rtl_menu_bar))
            panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val popup = glassPopup(panel, (400 * density).toInt())
        val listed = ArrayList(cards)
        Collections.sort(listed, Cards::byLabel)
        for (card in listed) {
            val row = layoutInflater.inflate(R.layout.item_card_row, rows, false)
            row.tag = card.pkg
            row.findViewById<TextView>(R.id.title).text = card.label
            if (resources.getBoolean(R.bool.rtl_menu_bar)) {
                val heading = row.findViewById<View>(R.id.title).parent as LinearLayout
                val status = row.findViewById<View>(R.id.state)
                heading.removeView(status)
                heading.addView(status, 0)
            }
            val app = row.findViewById<TextView>(R.id.app)
            if (Cards.fold(card.label) == Cards.fold(card.appLabel)) app.visibility = View.GONE
            else app.text = getString(R.string.card_app_name, card.appLabel)
            row.findViewById<TextView>(R.id.pkg).text = card.pkg
            row.findViewById<TextView>(R.id.digest).text = Cards.longDigest(card.digest)
            val pkg = card.pkg
            row.setOnClickListener {
                CardFeature.run<Unit?>({
                    // The list stays open; the decision is for the card as it is now, not as it was listed.
                    val now = cardFor(pkg)
                    if (now != null) {
                        decideCard(now, now.state != Cards.State.SHOWN)
                        // Every row, not only this one: a toggle can move another card across the limit of 8.
                        updateCardStates(rows)
                    }
                    null
                }, null)
            }
            rows.addView(row)
        }
        updateCardStates(rows)
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val at = IntArray(2)
        anchor.getLocationInWindow(at)
        val room = root.height - (at[1] + anchor.height) - (34 * density).toInt()
        popup.height = Math.min(panel.measuredHeight, Math.max(room, (120 * density).toInt()))
        // Hebrew places Settings on the left: align the outer panel inward, like its parent menu.
        dropIn(popup, panel, anchor, if (resources.getBoolean(R.bool.rtl_menu_bar))
                Gravity.START else Gravity.END)
        rows.getChildAt(0).requestFocus()
    }

    private fun cardFor(pkg: String): Cards.Card? {
        for (c in cards) if (c.pkg == pkg) return c
        return null
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private fun updateCardStates(rows: LinearLayout) {
        val drawn = Cards.shown(cards)
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            val tag = row.tag
            val card = if (tag is String) cardFor(tag) else null
            row.findViewById<TextView>(R.id.state).text = if (card != null) stateText(card, drawn.contains(card)) else ""
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private fun stateText(card: Cards.Card, drawn: Boolean): String {
        return when (card.state) {
            Cards.State.SHOWN -> getString(if (drawn) R.string.card_state_shown else R.string.card_state_over_limit)
            Cards.State.HIDDEN -> getString(R.string.card_state_hidden)
            Cards.State.RESIGNED -> getString(R.string.card_state_new_key)
            Cards.State.RELABELLED -> getString(R.string.card_state_new_label)
            else -> getString(R.string.card_state_new)
        }
    }
}
