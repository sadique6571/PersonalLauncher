package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.sadique.launcher/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
internal class Config private constructor(
    dockApps: List<String>,
    fillDock: Boolean,
    hiddenApps: List<String>,
    libraryApps: List<String>,
    libraryExcludedApps: List<String>,
    liveTvPackage: String,
    liveTvAction: String,
    liveTvInputId: String,
    launchActions: Map<String, String>,
    appLabels: Map<String, String>,
    appBanners: Map<String, String>,
    sourcePanelService: String,
    sourcePanelAction: String,
    sourcePanelExtra: String,
    coocaaSources: Boolean,
    inputAction: String,
    inputSources: List<String>,
    inputUriScheme: String,
    columns: Int,
    rowsBelowDock: Int,
    animations: Boolean,
    advancedEffects: Boolean,
    shamsiDate: Boolean,
    greetingName: String,
    moduleCards: Boolean,
    reducedMotion: Boolean,
    bootToLiveTv: Boolean,
    openOnStartChoice: Boolean,
    dockTitle: Boolean,
    dockRow: Boolean,
    topMenu: Boolean,
    launcherSettingsButton: Boolean,
    reorderButton: Boolean,
    showNetworkPill: Boolean,
    showInputsPill: Boolean,
    iconOnlyButtons: Boolean,
    iconOnlyInputs: Boolean,
    libraryButton: Boolean,
    showLibraryPill: Boolean,
    wifiLabelWidth: Int,
    alwaysShowNames: Boolean,
    wallpaperTrueColor: Boolean,
    focusOvershoot: Boolean,
    vpnApp: String,
    showHomeSettingsShortcut: Boolean,
    showStats: Boolean,
    gameDay: String,
    appLibrary: Boolean,
    appLibrarySelection: Boolean,
    libraryOkOpens: Boolean,
    uniformTiles: Boolean,
    backgroundColor: Int,
    skyIntensity: Int,
    settingsKeycodes: IntArray,
    settingsKeyPanel: Boolean,
    restoreKeyServices: Boolean,
    wallpaperEnabled: Boolean,
    wallpaper: File?,
    wallpapers: List<File>,
    wallpaperRotationMinutes: Int,
    wallpaperDim: Int,
    dockOpacity: Int,
    wallpaperOrder: String,
    overlayApplied: Boolean,
    festival: Festival,
    inputKeycodes: IntArray,
    appKeys: List<String>,
    stockHomeRedirect: String,
    raceCalendar: List<RaceWeekend.Race>,
    cinematicHome: Boolean,
    ambientGlow: Boolean,
    appGlow: Boolean,
    fadeRows: Boolean,
    homeIdleClockMinutes: Int,
    keyFilter: Boolean,
    dockGlass: Boolean,
    glowStyleValue: String,
    wallpaperCaptions: Boolean,
    handleViewInputs: Boolean,
    tileMenu: Boolean,
    dockEvenPadding: Boolean,
    dockBand: Boolean,
    cinematicDots: Boolean,
    cinematicWhiteFocus: Boolean,
    wifiStatusColor: Boolean,
    cinematicScrim: Int,
    cinematicArt: Int,
    wallpaperMoods: Map<String, Set<String>>,
    gamepadPassthrough: Boolean,
    consumeVolumeKeys: Boolean,
    powerOnHome: Boolean,
    homeLongPressHome: Boolean,
    wifiStatusWarnings: Boolean,
    topBarApps: List<String>,
    showGreeting: Boolean,
    headerWhite: Boolean,
    clockWallpaper: Boolean,
    clockWallpaperDim: Int,
    clockGlow: Boolean,
    glassStyleValue: String,
    singleRow: Boolean,
    dockBandAll: Boolean,
    lunarDate: Boolean,
    lunarUtcOffset: Int,
    dateBelowClock: Boolean,
    vpnPill: Boolean,
    locale: String,
    powerOnHomeAlways: Boolean,
    inputsConnectedOnly: Boolean,
    openOnStartNetworkWait: Int,
    iconOnlyVpn: Boolean,
    remoteKeysDebug: Boolean,
    recoveryHomeRedirect: Boolean
) {
    @JvmField val dockApps: List<String>

    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    @JvmField val fillDock: Boolean
    @JvmField val hiddenApps: List<String>

    /** Packages pre-seeded into the App library's "added" set (config.xml, library_apps); empty by default. */
    @JvmField val libraryApps: List<String>

    /** Packages the App library catalogue never offers, even with app_library on (config.xml, library_excluded_apps). */
    @JvmField val libraryExcludedApps: List<String>
    @JvmField val liveTvPackage: String
    @JvmField val liveTvAction: String
    @JvmField val liveTvInputId: String

    /** Package to the action its tile sends instead of opening the start screen (config.xml, launch_actions). */
    @JvmField val launchActions: Map<String, String>

    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    @JvmField val appLabels: Map<String, String>

    /** Package to a bundled banner_* drawable its tile shows instead of the app's art (config.xml, app_banners). */
    @JvmField val appBanners: Map<String, String>
    @JvmField val sourcePanelService: String
    @JvmField val sourcePanelAction: String

    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    @JvmField val sourcePanelExtra: String

    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    @JvmField val coocaaSources: Boolean
    @JvmField val inputAction: String

    /** Vendor source numbers for passthrough inputs (config.xml or theme input_sources). */
    @JvmField val inputSources: List<String>

    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml or theme input_uri_scheme). */
    @JvmField val inputUriScheme: String
    @JvmField val columns: Int
    @JvmField val animations: Boolean
    @JvmField val advancedEffects: Boolean
    @JvmField val rowsBelowDock: Int
    @JvmField val shamsiDate: Boolean

    /** Lunisolar date under the date (config.json lunar_date), computed for lunarUtcOffset hours east of UTC (-1: by locale). */
    @JvmField val lunarDate: Boolean
    @JvmField val lunarUtcOffset: Int

    /** The date on its own line under the clock instead of beside it (config.xml, date_below_clock). */
    @JvmField val dateBelowClock: Boolean

    /** The VPN state as its own pill after the network pill, instead of a badge inside it (config.xml, vpn_pill). */
    @JvmField val vpnPill: Boolean

    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    @JvmField val locale: String

    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    @JvmField val greetingName: String
    @JvmField val moduleCards: Boolean

    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    @JvmField val reducedMotion: Boolean

    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    @JvmField val bootToLiveTv: Boolean

    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    @JvmField val openOnStartChoice: Boolean

    /** Seconds the boot's app waits for a validated network; 0 opens it at once (open_on_start_network_wait). */
    @JvmField val openOnStartNetworkWait: Int

    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    @JvmField val dockTitle: Boolean

    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    @JvmField val dockRow: Boolean

    /** Inputs, Wi-Fi and Settings folded into one gear button with a dropdown, like Google TV (config.xml top_menu). */
    @JvmField val topMenu: Boolean

    /** Direct launcher-settings header action; off by default (launcher_settings_button). */
    @JvmField val launcherSettingsButton: Boolean

    /** Names under every tile, dimmed until focused (config.xml always_show_names); the dock band grows for them. */
    @JvmField val alwaysShowNames: Boolean

    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    @JvmField val reorderButton: Boolean

    /** Show the Wi-Fi/Ethernet pill in the top bar (config.xml, show_network_pill). */
    @JvmField val showNetworkPill: Boolean

    /** Show the Inputs pill when the TV has inputs to list (config.xml, show_inputs_pill). */
    @JvmField val showInputsPill: Boolean

    /** Wi-Fi and Settings pills as round icon buttons, Inputs keeps its label (config.xml icon_only_buttons). */
    @JvmField val iconOnlyButtons: Boolean

    /** The Inputs pill as a round icon button too (config.xml icon_only_inputs). */
    @JvmField val iconOnlyInputs: Boolean

    /** With vpn_pill, the VPN pill as a round lock button (config.xml / theme icon_only_vpn). */
    @JvmField val iconOnlyVpn: Boolean

    /** App library as a round button in the top bar (config.xml library_button); needs app_library. */
    @JvmField val libraryButton: Boolean

    /** The App library pill after the last Home row; false hides it while libraryButton is on (show_library_pill). */
    @JvmField val showLibraryPill: Boolean

    /** With icon_only_buttons, the Wi-Fi pill keeps the network name up to this width in dp (0: icon only). */
    @JvmField val wifiLabelWidth: Int

    /** Decode wallpapers in 32-bit colour instead of RGB_565 (config.xml wallpaper_true_color). */
    @JvmField val wallpaperTrueColor: Boolean

    /** The focused tile and the glass highlight swing past their target before settling (config.xml, focus_overshoot). */
    @JvmField val focusOvershoot: Boolean
    @JvmField val vpnApp: String
    @JvmField val showHomeSettingsShortcut: Boolean

    /** One line under the clock: CPU, free memory, free storage. Off unless config or a theme turns it on. */
    @JvmField val showStats: Boolean

    /** game_day: the package of an add-on that publishes each team's next game (GameDay); empty = off. */
    @JvmField val gameDay: String

    /** App library pill after the last Home row (config.xml app_library). */
    @JvmField val appLibrary: Boolean

    /** OK directly toggles Home membership in the library (app_library_selection). */
    @JvmField val appLibrarySelection: Boolean

    /** OK opens every app in the library; Add to Home stays in the hold menu (library_ok_opens). */
    @JvmField val libraryOkOpens: Boolean

    /** Icon on a card tinted from that icon, instead of the app's banner (config.xml uniform_tiles). */
    @JvmField val uniformTiles: Boolean

    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    @JvmField val backgroundColor: Int

    /** Time-of-day backdrop, 0 to 100 (config.xml, sky_intensity); 0 keeps the plain background. */
    @JvmField val skyIntensity: Int

    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    @JvmField val settingsKeycodes: IntArray

    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    @JvmField val inputKeycodes: IntArray

    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    @JvmField val appKeys: List<String>

    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    @JvmField val keyFilter: Boolean

    /** Verbose remote-key logging for the key services (config.xml or theme remote_keys_debug); off by default. */
    @JvmField val remoteKeysDebug: Boolean

    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    @JvmField val gamepadPassthrough: Boolean

    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    @JvmField val consumeVolumeKeys: Boolean

    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    @JvmField val stockHomeRedirect: String

    /** Opt-in rescue: the setup recovery window (setupwraith RecoveryActivity) is sent back here when the
     *  configured stock launcher is disabled (config.xml recovery_home_redirect). Off by default. */
    @JvmField val recoveryHomeRedirect: Boolean

    /** A Live TV window the firmware opens by itself at power-on is sent back to Home (power_on_home). */
    @JvmField val powerOnHome: Boolean

    /** Every Home press opens this launcher, a held one too, instead of Android's long-press action (home_long_press_home). */
    @JvmField val homeLongPressHome: Boolean

    /** Home at every power-on, whatever the firmware put in front: an input, Live TV or an app (power_on_home_always). */
    @JvmField val powerOnHomeAlways: Boolean

    /** The Inputs menu lists only inputs reporting connected; the rest fold behind a More inputs row (config.xml / theme inputs_connected_only). */
    @JvmField val inputsConnectedOnly: Boolean

    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    @JvmField val settingsKeyPanel: Boolean

    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    @JvmField val restoreKeyServices: Boolean

    /** Whether themes may show a wallpaper (config.json, wallpaper). */
    @JvmField val wallpaperEnabled: Boolean

    /** Full-screen picture behind the launcher, or null for the plain colour. */
    @JvmField val wallpaper: File?

    /** Rotating theme images, or the single wallpaper when no collection is configured. */
    @JvmField val wallpapers: List<File>
    @JvmField val wallpaperRotationMinutes: Int
    @JvmField val wallpaperDim: Int

    /** The dock band's opacity in percent (config.json dock_opacity, 30-100): below 100 the wallpaper shows through. */
    @JvmField val dockOpacity: Int

    /**
     * Playback order for a wallpaper collection: {@code sequential} or {@code shuffle}. Missing or
     * invalid values keep the historical shuffle so existing themes rotate exactly as before.
     */
    @JvmField val wallpaperOrder: String

    /** Whether the overlay file was present and parsed. */
    @JvmField val overlayApplied: Boolean

    /** Festive skin from a theme (festival, festival_until, greeting, accent_color); {@link Festival#NONE} if none. */
    @JvmField val festival: Festival

    /** F1 weekends from a theme's race_calendar (see RaceWeekend); empty when the theme has none. */
    @JvmField val raceCalendar: List<RaceWeekend.Race>

    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    @JvmField val cinematicHome: Boolean

    /** The glow in the focused app's colour behind the dock (config.xml, ambient_glow); false keeps the background plain. */
    @JvmField val ambientGlow: Boolean

    /** The focus highlight in the focused app's colour on every tile (config.xml, app_glow), as cinematic_home does for the dock. */
    @JvmField val appGlow: Boolean

    /** The rows fade out under the clock instead of being cut off (config.xml, fade_rows); TopFade. */
    @JvmField val fadeRows: Boolean

    /** Minutes Home may sit untouched before the clock screensaver (config.xml, home_idle_clock_minutes); 0 is off. */
    @JvmField val homeIdleClockMinutes: Int

    /** The dock band as light frosted glass like the pills (config.xml, dock_glass); false keeps the solid band. */
    @JvmField val dockGlass: Boolean

    /** The dock band's padding equals the gap between its tiles (config.xml, dock_even_padding); false keeps the fixed padding. */
    @JvmField val dockEvenPadding: Boolean

    /** The dock band and its edge draw (dock_band); false leaves the tiles floating on the wallpaper. */
    @JvmField val dockBand: Boolean

    /** The dock position dots under the cinematic shelf's name (config.xml, cinematic_dots); false leaves them out. */
    @JvmField val cinematicDots: Boolean

    /** The cinematic shelf keeps the white focus highlight instead of the app's colour (config.xml, cinematic_white_focus). */
    @JvmField val cinematicWhiteFocus: Boolean

    /** The Wi-Fi pill's icon green while the TV is online (config.xml, wifi_status_color); false keeps it white. */
    @JvmField val wifiStatusColor: Boolean

    /** Opacity of the app's artwork behind the cinematic shelf, 0 to 100 (config.xml, cinematic_art); 50 as before. */
    @JvmField val cinematicArt: Int

    /** Strength of the cinematic shelf's dark scrim, 0 to 100 percent (config.xml, cinematic_scrim); 100 as before. */
    @JvmField val cinematicScrim: Int

    /** A rotating collection's groups by time of day, as file names (config.xml, wallpaper_moods); empty rotates through all of it. */
    @JvmField val wallpaperMoods: Map<String, Set<String>>

    /** Shape of the glow behind the focused app (config.xml, glow_style): {@link #GLOW_RADIAL} or {@link #GLOW_HORIZON}. */
    @JvmField val glowStyle: String

    /** The focus highlight's look (config.xml, glass_style): {@link #GLASS_DEFAULT}, {@link #GLASS_RIM} or {@link #GLASS_HALO}. */
    @JvmField val glassStyle: String

    /** Show each rotating wallpaper's name from wallpapers/captions.txt (config.xml / theme wallpaper_captions). */
    @JvmField val wallpaperCaptions: Boolean

    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    @JvmField val handleViewInputs: Boolean

    /** Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info (config.xml / theme tile_menu). */
    @JvmField val tileMenu: Boolean

    /** With wifi_status_color, the icon orange while connected without internet (a captive portal) and red while offline (config.xml, wifi_status_warnings). */
    @JvmField val wifiStatusWarnings: Boolean

    /** Apps shown as round logo buttons in the top bar, left of the network pill, and no longer as tiles (config.xml, top_bar_apps). */
    @JvmField val topBarApps: List<String>

    /** The greeting above the clock (Good evening); false hides it (config.xml, show_greeting). */
    @JvmField val showGreeting: Boolean

    /** The date and the line between it and the clock in the clock's full white instead of dimmed (config.xml, header_white). */
    @JvmField val headerWhite: Boolean

    /** The clock screensaver over Home's wallpaper instead of the black glow (config.xml, clock_wallpaper). */
    @JvmField val clockWallpaper: Boolean

    /** How dark the clock screensaver's wallpaper is, 0 to 90 % (config.xml, clock_wallpaper_dim). */
    @JvmField val clockWallpaperDim: Int

    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (config.xml, clock_glow). */
    @JvmField val clockGlow: Boolean

    /** Every app in the dock's row, which scrolls sideways, and no grid below it (config.xml, single_row); needs dock_row. */
    @JvmField val singleRow: Boolean

    /** With single_row, the band runs behind every app in the row, not only the dock apps (config.xml, dock_band_all). */
    @JvmField val dockBandAll: Boolean

    init {
        this.handleViewInputs = handleViewInputs
        this.tileMenu = tileMenu
        this.inputKeycodes = inputKeycodes
        this.appKeys = Collections.unmodifiableList(appKeys)
        this.keyFilter = keyFilter
        this.stockHomeRedirect = stockHomeRedirect
        this.recoveryHomeRedirect = recoveryHomeRedirect
        this.dockApps = Collections.unmodifiableList(dockApps)
        this.fillDock = fillDock
        this.hiddenApps = Collections.unmodifiableList(hiddenApps)
        this.libraryApps = Collections.unmodifiableList(libraryApps)
        this.libraryExcludedApps = Collections.unmodifiableList(libraryExcludedApps)
        this.liveTvPackage = liveTvPackage
        this.liveTvAction = liveTvAction
        this.liveTvInputId = liveTvInputId
        this.launchActions = Collections.unmodifiableMap(launchActions)
        this.appLabels = Collections.unmodifiableMap(appLabels)
        this.appBanners = Collections.unmodifiableMap(appBanners)
        this.sourcePanelService = sourcePanelService
        this.sourcePanelAction = sourcePanelAction
        this.sourcePanelExtra = sourcePanelExtra
        this.coocaaSources = coocaaSources
        this.inputAction = inputAction
        this.inputSources = Collections.unmodifiableList(inputSources)
        this.inputUriScheme = inputUriScheme
        this.columns = columns
        this.rowsBelowDock = rowsBelowDock
        this.animations = animations
        this.advancedEffects = advancedEffects
        this.shamsiDate = shamsiDate
        this.lunarDate = lunarDate
        this.lunarUtcOffset = lunarUtcOffset
        this.dateBelowClock = dateBelowClock
        this.vpnPill = vpnPill
        this.locale = locale
        this.greetingName = greetingName
        this.moduleCards = moduleCards
        this.reducedMotion = reducedMotion
        this.bootToLiveTv = bootToLiveTv
        this.openOnStartChoice = openOnStartChoice
        this.dockTitle = dockTitle
        this.dockRow = dockRow
        this.topMenu = topMenu
        this.launcherSettingsButton = launcherSettingsButton
        this.reorderButton = reorderButton
        this.showNetworkPill = showNetworkPill
        this.showInputsPill = showInputsPill
        this.iconOnlyButtons = iconOnlyButtons
        this.iconOnlyInputs = iconOnlyInputs
        this.iconOnlyVpn = iconOnlyVpn
        this.remoteKeysDebug = remoteKeysDebug
        this.libraryButton = libraryButton
        this.showLibraryPill = showLibraryPill
        this.wifiLabelWidth = wifiLabelWidth
        this.alwaysShowNames = alwaysShowNames
        this.wallpaperTrueColor = wallpaperTrueColor
        this.focusOvershoot = focusOvershoot
        this.vpnApp = vpnApp
        this.showHomeSettingsShortcut = showHomeSettingsShortcut
        this.showStats = showStats
        this.gameDay = gameDay
        this.appLibrary = appLibrary
        this.appLibrarySelection = appLibrarySelection
        this.libraryOkOpens = libraryOkOpens
        this.uniformTiles = uniformTiles
        this.backgroundColor = backgroundColor
        this.skyIntensity = skyIntensity
        this.settingsKeycodes = settingsKeycodes
        this.settingsKeyPanel = settingsKeyPanel
        this.restoreKeyServices = restoreKeyServices
        this.wallpaperEnabled = wallpaperEnabled
        this.wallpaper = wallpaper
        this.wallpapers = Collections.unmodifiableList(wallpapers)
        this.wallpaperRotationMinutes = wallpaperRotationMinutes
        this.wallpaperDim = wallpaperDim
        this.dockOpacity = dockOpacity
        this.wallpaperOrder = wallpaperOrder
        this.overlayApplied = overlayApplied
        this.festival = festival
        this.raceCalendar = Collections.unmodifiableList(raceCalendar)
        this.cinematicHome = cinematicHome
        this.ambientGlow = ambientGlow
        this.appGlow = appGlow
        this.fadeRows = fadeRows
        this.homeIdleClockMinutes = homeIdleClockMinutes
        this.dockGlass = dockGlass
        this.dockEvenPadding = dockEvenPadding
        this.dockBand = dockBand
        this.cinematicDots = cinematicDots
        this.cinematicWhiteFocus = cinematicWhiteFocus
        this.wifiStatusColor = wifiStatusColor
        this.cinematicArt = cinematicArt
        this.cinematicScrim = cinematicScrim
        this.wallpaperMoods = wallpaperMoods
        this.gamepadPassthrough = gamepadPassthrough
        this.consumeVolumeKeys = consumeVolumeKeys
        this.powerOnHome = powerOnHome
        this.homeLongPressHome = homeLongPressHome
        this.powerOnHomeAlways = powerOnHomeAlways
        this.inputsConnectedOnly = inputsConnectedOnly
        this.openOnStartNetworkWait = openOnStartNetworkWait
        this.wifiStatusWarnings = wifiStatusWarnings
        this.topBarApps = Collections.unmodifiableList(topBarApps)
        this.showGreeting = showGreeting
        this.headerWhite = headerWhite
        this.clockWallpaper = clockWallpaper
        this.clockWallpaperDim = clockWallpaperDim
        this.clockGlow = clockGlow
        this.singleRow = singleRow
        this.dockBandAll = dockBandAll
        this.glowStyle = glowStyle(glowStyleValue)
        this.wallpaperCaptions = wallpaperCaptions
        this.glassStyle = glassStyle(glassStyleValue)
    }

    companion object {
        private const val TAG = "TvHome.Config"
        const val FILE_NAME = "config.json"
        const val WALLPAPER_NAME = "wallpaper.jpg"
        const val SETTINGS_NAME = "launcher_settings"
        const val ROWS_BELOW_DOCK_KEY = "rows_below_dock"
        const val WALLPAPER_CHOICE_KEY = "wallpaper_choice"
        const val DOCK_OPACITY_KEY = "dock_opacity"

        /**
         * Counts the changes made in Launcher settings. Home remembers the count it was built with and rebuilds on
         * resume when it differs, however the settings were left (see MainActivity#onResume).
         */
        const val SETTINGS_REVISION_KEY = "settings_revision"

        private const val THEME_REVISION_NAME = "theme_revision"
        private const val THEME_SIGNATURE_KEY = "theme_signature"

        const val GLOW_RADIAL = "radial"
        const val GLOW_HORIZON = "horizon"
        const val GLASS_DEFAULT = "glass"
        const val GLASS_RIM = "rim"
        const val GLASS_HALO = "halo"

        /** True when Launcher settings changed something since Home was built at revision {@code seen}. */
        @JvmStatic
        fun settingsChanged(seen: Long, current: Long): Boolean {
            return seen != current
        }

        /** A known glow_style, or radial (the default) for anything else. */
        @JvmStatic
        fun glowStyle(value: String?): String {
            return if (value != null && GLOW_HORIZON.equals(value.trim(), ignoreCase = true)) GLOW_HORIZON else GLOW_RADIAL
        }

        /** A known glass_style, or glass (the default) for anything else. */
        @JvmStatic
        fun glassStyle(value: String?): String {
            if (value == null) return GLASS_DEFAULT
            val v = value.trim()
            if (GLASS_RIM.equals(v, ignoreCase = true)) return GLASS_RIM
            return if (GLASS_HALO.equals(v, ignoreCase = true)) GLASS_HALO else GLASS_DEFAULT
        }

        /** The folder the theme tools push into; null when external storage is unavailable. */
        @JvmStatic
        fun dir(ctx: Context): File? {
            return ctx.getExternalFilesDir(null)
        }

        @JvmStatic
        fun load(ctx: Context): Config {
            val res = ctx.resources
            var dock: MutableList<String> = res.getStringArray(R.array.dock_apps).toMutableList()
            var fillDock = res.getBoolean(R.bool.fill_dock)
            var hidden: MutableList<String> = res.getStringArray(R.array.hidden_apps).toMutableList()
            var libraryApps: MutableList<String> = res.getStringArray(R.array.library_apps).toMutableList()
            var libraryExcludedApps: MutableList<String> = res.getStringArray(R.array.library_excluded_apps).toMutableList()
            var liveTv = res.getString(R.string.live_tv_package).trim()
            var liveTvAction = res.getString(R.string.live_tv_action).trim()
            var liveTvInputId = res.getString(R.string.live_tv_input_id).trim()
            var launchActions: MutableList<String> = res.getStringArray(R.array.launch_actions).toMutableList()
            var appLabels: MutableList<String> = res.getStringArray(R.array.app_labels).toMutableList()
            var appBanners: MutableList<String> = res.getStringArray(R.array.app_banners).toMutableList()
            var panelService = res.getString(R.string.source_panel_service).trim()
            var panelAction = res.getString(R.string.source_panel_action).trim()
            var panelExtra = res.getString(R.string.source_panel_extra).trim()
            var coocaaSources = res.getBoolean(R.bool.coocaa_sources)
            var inputAction = res.getString(R.string.input_action).trim()
            var inputSources: MutableList<String> = res.getStringArray(R.array.input_sources).toMutableList()
            var inputUriScheme = res.getString(R.string.input_uri_scheme).trim()
            var columns = res.getInteger(R.integer.columns)
            var rowsBelow = res.getInteger(R.integer.rows_below_dock)
            var animations = res.getBoolean(R.bool.animations)
            var advancedEffects = res.getBoolean(R.bool.advanced_effects)
            var shamsi = res.getBoolean(R.bool.shamsi_date)
            var lunar = res.getBoolean(R.bool.lunar_date)
            var lunarOffset = -1
            var greetingName = res.getString(R.string.greeting_name).trim()
            var moduleCards = res.getBoolean(R.bool.module_cards)
            var reducedMotion = res.getBoolean(R.bool.reduced_motion)
            var bootToLiveTv = res.getBoolean(R.bool.boot_to_live_tv)
            var openOnStartChoice = res.getBoolean(R.bool.open_on_start_choice)
            var openOnStartNetworkWait = res.getInteger(R.integer.open_on_start_network_wait)
            var dockTitle = res.getBoolean(R.bool.dock_title)
            var dockRow = res.getBoolean(R.bool.dock_row)
            var topMenu = res.getBoolean(R.bool.top_menu)
            var launcherSettingsButton = res.getBoolean(R.bool.launcher_settings_button)
            var reorderButton = res.getBoolean(R.bool.reorder_button)
            var showNetworkPill = res.getBoolean(R.bool.show_network_pill)
            var showInputsPill = res.getBoolean(R.bool.show_inputs_pill)
            var iconOnlyButtons = res.getBoolean(R.bool.icon_only_buttons)
            var iconOnlyInputs = res.getBoolean(R.bool.icon_only_inputs)
            var iconOnlyVpn = res.getBoolean(R.bool.icon_only_vpn)
            var remoteKeysDebug = res.getBoolean(R.bool.remote_keys_debug)
            var libraryButton = res.getBoolean(R.bool.library_button)
            var showLibraryPill = res.getBoolean(R.bool.show_library_pill)
            var wifiLabelWidth = res.getInteger(R.integer.wifi_label_width)
            var alwaysShowNames = res.getBoolean(R.bool.always_show_names)
            var wallpaperTrueColor = res.getBoolean(R.bool.wallpaper_true_color)
            var focusOvershoot = res.getBoolean(R.bool.focus_overshoot)
            var wallpaperCaptions = res.getBoolean(R.bool.wallpaper_captions)
            var tileMenu = res.getBoolean(R.bool.tile_menu)
            var vpn = res.getString(R.string.vpn_app).trim()
            var homeSettings = res.getBoolean(R.bool.show_home_settings_shortcut)
            var showStats = res.getBoolean(R.bool.show_stats)
            var gameDay = res.getString(R.string.game_day).trim()
            var library = res.getBoolean(R.bool.app_library)
            var librarySelection = res.getBoolean(R.bool.app_library_selection)
            var libraryOkOpens = res.getBoolean(R.bool.library_ok_opens)
            var uniformTiles = res.getBoolean(R.bool.uniform_tiles)
            var background = 0xFF000000.toInt()
            var skyIntensity = res.getInteger(R.integer.sky_intensity)
            var settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes)
            var settingsKeyPanel = res.getBoolean(R.bool.settings_key_panel)
            var restoreKeyServices = res.getBoolean(R.bool.restore_key_services)
            var inputKeycodes = res.getIntArray(R.array.input_key_keycodes)
            var handleViewInputs = res.getBoolean(R.bool.handle_view_inputs)
            var appKeys: MutableList<String> = res.getStringArray(R.array.app_keys).toMutableList()
            var stockHomeRedirect = res.getString(R.string.stock_home_redirect).trim()
            var recoveryHomeRedirect = res.getBoolean(R.bool.recovery_home_redirect)
            var cinematic = res.getBoolean(R.bool.cinematic_home)
            var ambientGlow = res.getBoolean(R.bool.ambient_glow)
            var appGlow = res.getBoolean(R.bool.app_glow)
            var fadeRows = res.getBoolean(R.bool.fade_rows)
            var idleClockMinutes = res.getInteger(R.integer.home_idle_clock_minutes)
            var keyFilter = res.getBoolean(R.bool.key_filter)
            var gamepadPassthrough = res.getBoolean(R.bool.gamepad_passthrough)
            var consumeVolumeKeys = res.getBoolean(R.bool.consume_volume_keys)
            var powerOnHome = res.getBoolean(R.bool.power_on_home)
            var homeLongPressHome = res.getBoolean(R.bool.home_long_press_home)
            var powerOnHomeAlways = res.getBoolean(R.bool.power_on_home_always)
            var inputsConnectedOnly = res.getBoolean(R.bool.inputs_connected_only)
            var dockGlass = res.getBoolean(R.bool.dock_glass)
            var dockEvenPadding = res.getBoolean(R.bool.dock_even_padding)
            var dockBand = res.getBoolean(R.bool.dock_band)
            var cinematicDots = res.getBoolean(R.bool.cinematic_dots)
            var cinematicWhiteFocus = res.getBoolean(R.bool.cinematic_white_focus)
            var wifiStatusColor = res.getBoolean(R.bool.wifi_status_color)
            var cinematicArt = res.getInteger(R.integer.cinematic_art)
            var cinematicScrim = res.getInteger(R.integer.cinematic_scrim)
            var wallpaperMoods = moods(res.getString(R.string.wallpaper_moods))
            var glowStyle = res.getString(R.string.glow_style)
            var wifiStatusWarnings = res.getBoolean(R.bool.wifi_status_warnings)
            var dateBelowClock = res.getBoolean(R.bool.date_below_clock)
            var vpnPill = res.getBoolean(R.bool.vpn_pill)
            var locale = res.getString(R.string.locale).trim()
            var topBarApps: MutableList<String> = res.getStringArray(R.array.top_bar_apps).toMutableList()
            var showGreeting = res.getBoolean(R.bool.show_greeting)
            var headerWhite = res.getBoolean(R.bool.header_white)
            var clockWallpaper = res.getBoolean(R.bool.clock_wallpaper)
            var clockWallpaperDim = res.getInteger(R.integer.clock_wallpaper_dim)
            var clockGlow = res.getBoolean(R.bool.clock_glow)
            var glassStyle = res.getString(R.string.glass_style)
            var singleRow = res.getBoolean(R.bool.single_row)
            var dockBandAll = res.getBoolean(R.bool.dock_band_all)
            var applied = false

            val themeDir = dir(ctx)
            val file = if (themeDir != null) File(themeDir, FILE_NAME) else null
            val mounted = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
            val json = readOverlay(ctx, file, mounted)
            if (json != null) {
                applied = true
                dock = stringList(json, "dock_apps", dock)
                fillDock = bool(json, "fill_dock", fillDock)
                hidden = stringList(json, "hidden_apps", hidden)
                libraryApps = stringList(json, "library_apps", libraryApps)
                libraryExcludedApps = stringList(json, "library_excluded_apps", libraryExcludedApps)
                liveTv = string(json, "live_tv_package", liveTv)
                liveTvAction = string(json, "live_tv_action", liveTvAction)
                liveTvInputId = string(json, "live_tv_input_id", liveTvInputId)
                launchActions = stringList(json, "launch_actions", launchActions)
                appLabels = stringList(json, "app_labels", appLabels)
                appBanners = stringList(json, "app_banners", appBanners)
                panelService = string(json, "source_panel_service", panelService)
                panelAction = string(json, "source_panel_action", panelAction)
                panelExtra = string(json, "source_panel_extra", panelExtra).trim()
                coocaaSources = bool(json, "coocaa_sources", coocaaSources)
                inputAction = string(json, "input_action", inputAction)
                inputSources = stringList(json, "input_sources", inputSources)
                inputUriScheme = string(json, "input_uri_scheme", inputUriScheme).trim()
                columns = integer(json, "columns", columns, 1, 8)
                rowsBelow = integer(json, "rows_below_dock", rowsBelow, 0, 3)
                animations = bool(json, "animations", animations)
                advancedEffects = bool(json, "advanced_effects", advancedEffects)
                shamsi = bool(json, "shamsi_date", shamsi)
                lunar = bool(json, "lunar_date", lunar)
                lunarOffset = integer(json, "lunar_utc_offset", lunarOffset, -1, 14)
                greetingName = string(json, "greeting_name", greetingName).trim()
                moduleCards = bool(json, "module_cards", moduleCards)
                reducedMotion = bool(json, "reduced_motion", reducedMotion)
                bootToLiveTv = bool(json, "boot_to_live_tv", bootToLiveTv)
                openOnStartChoice = bool(json, "open_on_start_choice", openOnStartChoice)
                openOnStartNetworkWait = integer(json, "open_on_start_network_wait", openOnStartNetworkWait, 0, 600)
                dockTitle = bool(json, "dock_title", dockTitle)
                dockRow = bool(json, "dock_row", dockRow)
                topMenu = bool(json, "top_menu", topMenu)
                launcherSettingsButton = bool(json, "launcher_settings_button", launcherSettingsButton)
                reorderButton = bool(json, "reorder_button", reorderButton)
                showNetworkPill = bool(json, "show_network_pill", showNetworkPill)
                showInputsPill = bool(json, "show_inputs_pill", showInputsPill)
                iconOnlyButtons = bool(json, "icon_only_buttons", iconOnlyButtons)
                iconOnlyInputs = bool(json, "icon_only_inputs", iconOnlyInputs)
                iconOnlyVpn = bool(json, "icon_only_vpn", iconOnlyVpn)
                remoteKeysDebug = bool(json, "remote_keys_debug", remoteKeysDebug)
                libraryButton = bool(json, "library_button", libraryButton)
                showLibraryPill = bool(json, "show_library_pill", showLibraryPill)
                wifiLabelWidth = integer(json, "wifi_label_width", wifiLabelWidth, 0, 300)
                alwaysShowNames = bool(json, "always_show_names", alwaysShowNames)
                wallpaperTrueColor = bool(json, "wallpaper_true_color", wallpaperTrueColor)
                focusOvershoot = bool(json, "focus_overshoot", focusOvershoot)
                wallpaperCaptions = bool(json, "wallpaper_captions", wallpaperCaptions)
                tileMenu = bool(json, "tile_menu", tileMenu)
                vpn = string(json, "vpn_app", vpn)
                homeSettings = bool(json, "show_home_settings_shortcut", homeSettings)
                showStats = bool(json, "show_stats", showStats)
                gameDay = string(json, "game_day", gameDay)
                library = bool(json, "app_library", library)
                librarySelection = bool(json, "app_library_selection", librarySelection)
                libraryOkOpens = bool(json, "library_ok_opens", libraryOkOpens)
                uniformTiles = bool(json, "uniform_tiles", uniformTiles)
                background = color(json, "background_color", background)
                skyIntensity = integer(json, "sky_intensity", skyIntensity, 0, 100)
                settingsKeycodes = intArray(json, "settings_key_keycodes", settingsKeycodes)
                settingsKeyPanel = bool(json, "settings_key_panel", settingsKeyPanel)
                restoreKeyServices = bool(json, "restore_key_services", restoreKeyServices)
                inputKeycodes = intArray(json, "input_key_keycodes", inputKeycodes)
                handleViewInputs = bool(json, "handle_view_inputs", handleViewInputs)
                appKeys = stringList(json, "app_keys", appKeys)
                appKeys = RemoteKeys.migrateScanSettingsKeys(appKeys).toMutableList()
                stockHomeRedirect = string(json, "stock_home_redirect", stockHomeRedirect)
                recoveryHomeRedirect = bool(json, "recovery_home_redirect", recoveryHomeRedirect)
                cinematic = bool(json, "cinematic_home", cinematic)
                ambientGlow = bool(json, "ambient_glow", ambientGlow)
                appGlow = bool(json, "app_glow", appGlow)
                fadeRows = bool(json, "fade_rows", fadeRows)
                idleClockMinutes = integer(json, "home_idle_clock_minutes", idleClockMinutes, 0, 1440)
                keyFilter = bool(json, "key_filter", keyFilter)
                gamepadPassthrough = bool(json, "gamepad_passthrough", gamepadPassthrough)
                consumeVolumeKeys = bool(json, "consume_volume_keys", consumeVolumeKeys)
                powerOnHome = bool(json, "power_on_home", powerOnHome)
                homeLongPressHome = bool(json, "home_long_press_home", homeLongPressHome)
                powerOnHomeAlways = bool(json, "power_on_home_always", powerOnHomeAlways)
                inputsConnectedOnly = bool(json, "inputs_connected_only", inputsConnectedOnly)
                dockGlass = bool(json, "dock_glass", dockGlass)
                dockEvenPadding = bool(json, "dock_even_padding", dockEvenPadding)
                dockBand = bool(json, "dock_band", dockBand)
                cinematicDots = bool(json, "cinematic_dots", cinematicDots)
                cinematicWhiteFocus = bool(json, "cinematic_white_focus", cinematicWhiteFocus)
                wifiStatusColor = bool(json, "wifi_status_color", wifiStatusColor)
                cinematicArt = integer(json, "cinematic_art", cinematicArt, 0, 100)
                cinematicScrim = integer(json, "cinematic_scrim", cinematicScrim, 0, 100)
                val moodsJson = json.optJSONObject("wallpaper_moods")
                if (moodsJson != null) wallpaperMoods = moods(moodsJson)
                wifiStatusWarnings = bool(json, "wifi_status_warnings", wifiStatusWarnings)
                dateBelowClock = bool(json, "date_below_clock", dateBelowClock)
                vpnPill = bool(json, "vpn_pill", vpnPill)
                locale = string(json, "locale", locale).trim()
                topBarApps = stringList(json, "top_bar_apps", topBarApps)
                showGreeting = bool(json, "show_greeting", showGreeting)
                headerWhite = bool(json, "header_white", headerWhite)
                clockWallpaper = bool(json, "clock_wallpaper", clockWallpaper)
                clockWallpaperDim = integer(json, "clock_wallpaper_dim", clockWallpaperDim, 0, 90)
                clockGlow = bool(json, "clock_glow", clockGlow)
                singleRow = bool(json, "single_row", singleRow)
                dockBandAll = bool(json, "dock_band_all", dockBandAll)
                glowStyle = string(json, "glow_style", glowStyle)
                glassStyle = string(json, "glass_style", glassStyle)
            }
            // A top-bar app is moved there, not copied: it leaves the grid.
            for (pkg in topBarApps) if (!hidden.contains(pkg)) hidden.add(pkg)
            // A theme push or reset invalidates choices made on the TV. On first use, remember the
            // current theme without clearing choices from an older launcher version.
            val choices = ctx.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
            // Unmounted storage looks like a theme reset; compare only once the file is readable.
            if (mounted) syncThemeChoices(choices, themeDir, file)
            val chosenColumns = choices.getInt("columns", columns)
            if (chosenColumns in 4..7) columns = chosenColumns
            val chosenRows = choices.getInt(ROWS_BELOW_DOCK_KEY, rowsBelow)
            if (chosenRows in 0..3) rowsBelow = chosenRows
            animations = choices.getBoolean("animations", animations)
            advancedEffects = choices.getBoolean("advanced_effects", advancedEffects)
            var wallpaper: File? = if (themeDir != null) File(themeDir, WALLPAPER_NAME) else null
            if (wallpaper != null && !(wallpaper.isFile && wallpaper.length() > 0)) wallpaper = null
            // A theme can also switch the wallpaper off while the file is still there.
            var wallpaperEnabled = json == null || bool(json, "wallpaper", true)
            if (!wallpaperEnabled) wallpaper = null
            val rotationMinutes = if (json == null) 0 else integer(json, "wallpaper_rotation_minutes", 0, 0, 1440)
            val wallpaperDim = if (json == null) 0 else integer(json, "wallpaper_dim", 0, 0, 90)
            var dockOpacity = if (json == null) 100 else integer(json, "dock_opacity", 100, 30, 100)
            dockOpacity = chosenDockOpacity(choices.getInt(DOCK_OPACITY_KEY, -1), dockOpacity)
            var wallpaperOrder = if (json == null) WallpaperRotation.ORDER_SHUFFLE
                else string(json, "wallpaper_order", WallpaperRotation.ORDER_SHUFFLE)
            // An unknown order must not silently change how an existing collection rotates.
            if (!WallpaperRotation.validOrder(wallpaperOrder)) wallpaperOrder = WallpaperRotation.ORDER_SHUFFLE
            val wallpapers = ArrayList<File>()
            if (rotationMinutes > 0 && themeDir != null && wallpaperEnabled) {
                wallpapers.addAll(WallpaperRotation.images(File(themeDir, "wallpapers")))
            }
            if (wallpapers.isEmpty() && wallpaper != null) wallpapers.add(wallpaper)
            // An on-TV wallpaper pick overrides the theme: a filename from the wallpapers/
            // collection, or "none". Unset means the theme decides (off by default).
            val wallpaperChoice = choices.getString(WALLPAPER_CHOICE_KEY, null)
            val chosen = WallpaperRotation.chosen(if (themeDir == null) Collections.emptyList()
                else WallpaperRotation.images(File(themeDir, "wallpapers")), wallpaper, wallpaperChoice)
            if (chosen != null) {
                wallpapers.clear()
                wallpapers.addAll(chosen)
                wallpaper = if (chosen.isEmpty()) null else chosen[0]
                wallpaperEnabled = chosen.isNotEmpty()
            }
            // A festive skin comes only from a theme; config.xml has no festival.
            val festival = if (json == null) Festival.NONE else Festival.parse(string(json, "festival", ""),
                string(json, "festival_until", ""), string(json, "greeting", ""), string(json, "accent_color", ""))

            return Config(dock, fillDock, hidden, libraryApps, libraryExcludedApps, liveTv, liveTvAction, liveTvInputId, packageMap(launchActions), packageMap(appLabels),
                packageMap(appBanners), panelService, panelAction, panelExtra, coocaaSources, inputAction, inputSources, inputUriScheme, Math.max(1, columns),
                Math.max(0, rowsBelow), animations, advancedEffects, shamsi, greetingName, moduleCards, reducedMotion, bootToLiveTv,
                openOnStartChoice, dockTitle, dockRow, topMenu, launcherSettingsButton, reorderButton, showNetworkPill, showInputsPill,
                iconOnlyButtons, iconOnlyInputs, libraryButton, showLibraryPill, wifiLabelWidth, alwaysShowNames, wallpaperTrueColor, focusOvershoot,
                vpn, homeSettings, showStats, gameDay, library, librarySelection, libraryOkOpens, uniformTiles, background,
                Math.max(0, Math.min(100, skyIntensity)), settingsKeycodes, settingsKeyPanel, restoreKeyServices, wallpaperEnabled, wallpaper,
                wallpapers, rotationMinutes, wallpaperDim, dockOpacity, wallpaperOrder, applied, festival, inputKeycodes, appKeys, stockHomeRedirect,
                if (json == null) Collections.emptyList() else raceCalendar(json), cinematic, ambientGlow, appGlow, fadeRows,
                Math.max(0, idleClockMinutes), keyFilter, dockGlass, glowStyle, wallpaperCaptions, handleViewInputs, tileMenu, dockEvenPadding,
                dockBand, cinematicDots, cinematicWhiteFocus, wifiStatusColor, cinematicScrim, cinematicArt, wallpaperMoods, gamepadPassthrough,
                consumeVolumeKeys, powerOnHome, homeLongPressHome, wifiStatusWarnings, topBarApps, showGreeting, headerWhite, clockWallpaper,
                Math.max(0, Math.min(90, clockWallpaperDim)), clockGlow, glassStyle, singleRow && dockRow, dockBandAll, lunar, lunarOffset,
                dateBelowClock, vpnPill, locale, powerOnHomeAlways, inputsConnectedOnly,
                Math.max(0, Math.min(600, openOnStartNetworkWait)), iconOnlyVpn, remoteKeysDebug, recoveryHomeRedirect)
        }

        /**
         * {@code "race_calendar": [{"round": 18, "name": "United States Grand Prix", "place": "Austin",
         * "sessions": [{"name": "FP1", "start": "2026-10-23T17:30:00Z"}, ...]}, ...]}. A race or session that does
         * not parse is left out on its own; the rest of the calendar still counts.
         */
        private fun raceCalendar(json: JSONObject): List<RaceWeekend.Race> {
            val races = ArrayList<RaceWeekend.Race>()
            val list = json.optJSONArray("race_calendar") ?: return races
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                val sessions = r.optJSONArray("sessions") ?: continue
                val parsed = ArrayList<RaceWeekend.Session>()
                for (j in 0 until sessions.length()) {
                    val s = sessions.optJSONObject(j) ?: continue
                    val name = s.optString("name", "").trim()
                    try {
                        if (name.isNotEmpty()) parsed.add(RaceWeekend.Session(name, java.time.Instant.parse(s.optString("start", ""))))
                    } catch (ignored: java.time.format.DateTimeParseException) {
                        // a session without a readable start time cannot be counted down
                    }
                }
                if (parsed.isNotEmpty()) races.add(RaceWeekend.Race(r.optInt("round", 0),
                    r.optString("name", "Grand Prix").trim(), r.optString("place", "").trim(), parsed))
            }
            return races
        }

        private fun syncThemeChoices(choices: SharedPreferences, dir: File?, config: File?) {
            val revision = if (dir == null) null else File(dir, THEME_REVISION_NAME)
            val token = revisionToken(revision)
            val signature = fileStamp(config) + ":" + token
            val seen = choices.getString(THEME_SIGNATURE_KEY, null)
            if (seen == null) {
                // Existing preferences survive an app upgrade, unless the first launch follows an
                // explicit theme push or reset by the updated tools.
                val editor = choices.edit()
                if ("absent" != token) editor.remove("columns").remove("animations")
                    .remove("advanced_effects").remove(ROWS_BELOW_DOCK_KEY).remove(WALLPAPER_CHOICE_KEY)
                    .remove(DOCK_OPACITY_KEY)
                editor.putString(THEME_SIGNATURE_KEY, signature).commit()
            } else if (seen != signature) {
                choices.edit().remove("columns").remove("animations").remove("advanced_effects")
                    .remove(ROWS_BELOW_DOCK_KEY).remove(WALLPAPER_CHOICE_KEY).remove(DOCK_OPACITY_KEY)
                    .putString(THEME_SIGNATURE_KEY, signature).commit()
            }
        }

        /** The dock band opacity chosen in Launcher settings, or the theme's when none (or an out-of-range one) is saved. */
        @JvmStatic
        fun chosenDockOpacity(chosen: Int, theme: Int): Int {
            return if (chosen in 30..100) chosen else theme
        }

        private fun fileStamp(file: File?): String {
            return if (file != null && file.isFile) "${file.length()}@${file.lastModified()}" else "absent"
        }

        private fun revisionToken(file: File?): String {
            if (file == null || !file.isFile || file.length() > 64) return "absent"
            try {
                FileInputStream(file).use { input ->
                    val bytes = ByteArray(file.length().toInt())
                    val n = input.read(bytes)
                    return if (n > 0) String(bytes, 0, n, StandardCharsets.US_ASCII) else "absent"
                }
            } catch (ignored: Exception) {
                return "absent"
            }
        }

        /** Reads and parses the overlay; any problem means "no overlay", never a crash on the home screen. */
        /**
         * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
         * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
         * up with the built-in dock order. A missing file on mounted storage still means no overlay.
         */
        private fun readOverlay(ctx: Context, file: File?, mounted: Boolean): JSONObject? {
            val copy = File(ctx.filesDir, FILE_NAME)
            if (!mounted) return read(copy)
            val json = read(file)
            if (json == null) {
                copy.delete()
            } else {
                try {
                    FileOutputStream(copy).use { out ->
                        out.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "could not keep a copy of $file: ${e.message}")
                }
            }
            return json
        }

        private fun read(file: File?): JSONObject? {
            if (file == null || !file.isFile) return null
            if (file.length() > 64 * 1024) {
                Log.w(TAG, "ignoring $file: larger than 64 KB")
                return null
            }
            try {
                FileInputStream(file).use { input ->
                    val buf = ByteArray(file.length().toInt())
                    var n = 0
                    while (n < buf.size) {
                        val r = input.read(buf, n, buf.size - n)
                        if (r <= 0) break
                        n += r
                    }
                    var text = String(buf, 0, n, StandardCharsets.UTF_8)
                    if (text.startsWith("\uFEFF")) text = text.substring(1)   // Windows editors add a BOM
                    return JSONObject(text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ignoring $file: ${e.message}")
                return null
            }
        }

        /** wallpaper_moods as JSON text (config.xml); empty or unreadable is no groups. */
        private fun moods(text: String): Map<String, Set<String>> {
            if (text.trim().isEmpty()) return Collections.emptyMap()
            try {
                return moods(JSONObject(text))
            } catch (e: JSONException) {
                return Collections.emptyMap()
            }
        }

        /** {"night": ["03-moon-lake.jpg", ...], ...}: each mood's picture names; other keys and names are left out. */
        private fun moods(o: JSONObject): Map<String, Set<String>> {
            val groups = HashMap<String, Set<String>>()
            for (mood in WallpaperMood.ALL) {
                val names = o.optJSONArray(mood) ?: continue
                val group = HashSet<String>()
                for (i in 0 until names.length()) {
                    val name = names.optString(i, "").trim()
                    if (ThemeFiles.collectionImage(name)) group.add(name)
                }
                if (group.isNotEmpty()) groups[mood] = Collections.unmodifiableSet(group)
            }
            return Collections.unmodifiableMap(groups)
        }

        private fun string(o: JSONObject, key: String, fallback: String): String {
            val v = o.opt(key)
            return if (v is String) v.trim() else fallback
        }

        private fun bool(o: JSONObject, key: String, fallback: Boolean): Boolean {
            val v = o.opt(key)
            return if (v is Boolean) v else fallback
        }

        private fun integer(o: JSONObject, key: String, fallback: Int, min: Int, max: Int): Int {
            val v = o.opt(key)
            if (v !is Number) return fallback
            val i = v.toInt()
            return if (i < min || i > max) fallback else i
        }

        private fun color(o: JSONObject, key: String, fallback: Int): Int {
            val v = o.opt(key)
            if (v !is String) return fallback
            try {
                val c = Color.parseColor(v.trim())
                return c or 0xFF000000.toInt()   // always opaque: the window behind is black anyway
            } catch (e: IllegalArgumentException) {
                return fallback
            }
        }

        /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
        private fun intArray(o: JSONObject, key: String, fallback: IntArray): IntArray {
            val v = o.opt(key)
            if (v !is JSONArray) return fallback
            val out = IntArray(v.length())
            for (i in 0 until v.length()) {
                val item = v.opt(i)
                if (item !is Number) return fallback
                out[i] = item.toInt()
                if (out[i] < 1) return fallback
            }
            return out
        }

        /** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
        @JvmStatic
        fun packageMap(entries: List<String>): Map<String, String> {
            val out = HashMap<String, String>()
            for (entry in entries) {
                val eq = entry.indexOf('=')
                if (eq <= 0) continue
                val pkg = entry.substring(0, eq).trim()
                val value = entry.substring(eq + 1).trim()
                if (pkg.isNotEmpty() && value.isNotEmpty()) out[pkg] = value
            }
            return out
        }

        private fun stringList(o: JSONObject, key: String, fallback: MutableList<String>): MutableList<String> {
            val v = o.opt(key)
            if (v !is JSONArray) return fallback
            val out = ArrayList<String>()
            for (i in 0 until v.length()) {
                val item = v.opt(i)
                if (item is String && item.trim().isNotEmpty()) out.add(item.trim())
            }
            return out
        }
    }
}
