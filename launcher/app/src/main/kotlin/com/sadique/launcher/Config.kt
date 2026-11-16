package com.sadique.launcher

import android.content.Context
import com.sadique.launcher.ThemeConfigJson.raceCalendar
import java.io.File
import java.util.Collections
import java.util.HashMap

/**
 * The launcher's settings: the values in res/values/config.xml, with an optional overlay read from
 * {@code config.json} in the app's external files directory
 * ({@code /sdcard/Android/data/com.sadique.launcher/files/}). Every key in the JSON is optional and an
 * unknown or badly typed key is ignored, so a theme can change the dock and the look without a rebuild:
 * {@code tools/theme.sh themes/<name>} pushes a theme folder there and restarts Home.
 *
 * Read once in {@link MainActivity#onCreate}; nothing here touches the network.
 */
internal class Config(
    dockApps: List<String>,
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock); on by default, false leaves the slots empty. */
    @JvmField val fillDock: Boolean,
    hiddenApps: List<String>,
    libraryApps: List<String>,
    libraryExcludedApps: List<String>,
    @JvmField val liveTvPackage: String,
    @JvmField val liveTvAction: String,
    @JvmField val liveTvInputId: String,
    launchActions: Map<String, String>,
    appLabels: Map<String, String>,
    appBanners: Map<String, String>,
    @JvmField val sourcePanelService: String,
    @JvmField val sourcePanelAction: String,
    /** One int extra for the source panel, "name=value" (config.xml source_panel_extra); empty for none. */
    @JvmField val sourcePanelExtra: String,
    /** List Coocaa OS sources from com.skyworth.tv in the Inputs menu (config.xml coocaa_sources). */
    @JvmField val coocaaSources: Boolean,
    @JvmField val inputAction: String,
    inputSources: List<String>,
    /** Scheme an Inputs row's passthrough URI is sent with instead of content:// (config.xml or theme input_uri_scheme). */
    @JvmField val inputUriScheme: String,
    @JvmField val columns: Int,
    @JvmField val rowsBelowDock: Int,
    @JvmField val animations: Boolean,
    @JvmField val advancedEffects: Boolean,
    @JvmField val shamsiDate: Boolean,
    /** Name after the greeting ("Good morning, Mr. Trịnh"); empty for the greeting alone. */
    @JvmField val greetingName: String,
    @JvmField val moduleCards: Boolean,
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    @JvmField val reducedMotion: Boolean,
    /** Open the Live TV app on the first Home after the TV starts (config.xml, boot_to_live_tv). */
    @JvmField val bootToLiveTv: Boolean,
    /** Show the Open on start row in Launcher settings (config.xml, open_on_start_choice). */
    @JvmField val openOnStartChoice: Boolean,
    /** The focused dock app's name in large type above the dock (config.xml, dock_title). */
    @JvmField val dockTitle: Boolean,
    /** The dock as its own row: band, no names, parked low (config.xml dock_row). false: one plain grid. */
    @JvmField val dockRow: Boolean,
    /** Inputs, Wi-Fi and Settings folded into one gear button with a dropdown, like Google TV (config.xml top_menu). */
    @JvmField val topMenu: Boolean,
    /** Direct launcher-settings header action; off by default (launcher_settings_button). */
    @JvmField val launcherSettingsButton: Boolean,
    /** Reorder button in the top bar (config.xml reorder_button); the Settings long-press menu offers it either way. */
    @JvmField val reorderButton: Boolean,
    /** Show the Wi-Fi/Ethernet pill in the top bar (config.xml, show_network_pill). */
    @JvmField val showNetworkPill: Boolean,
    /** Show the Inputs pill when the TV has inputs to list (config.xml, show_inputs_pill). */
    @JvmField val showInputsPill: Boolean,
    /** Wi-Fi and Settings pills as round icon buttons, Inputs keeps its label (config.xml icon_only_buttons). */
    @JvmField val iconOnlyButtons: Boolean,
    /** The Inputs pill as a round icon button too (config.xml icon_only_inputs). */
    @JvmField val iconOnlyInputs: Boolean,
    /** App library as a round button in the top bar (config.xml library_button); needs app_library. */
    @JvmField val libraryButton: Boolean,
    /** The App library pill after the last Home row; false hides it while libraryButton is on (show_library_pill). */
    @JvmField val showLibraryPill: Boolean,
    /** With icon_only_buttons, the Wi-Fi pill keeps the network name up to this width in dp (0: icon only). */
    @JvmField val wifiLabelWidth: Int,
    /** Names under every tile, dimmed until focused (config.xml always_show_names); the dock band grows for them. */
    @JvmField val alwaysShowNames: Boolean,
    /** Decode wallpapers in 32-bit colour instead of RGB_565 (config.xml wallpaper_true_color). */
    @JvmField val wallpaperTrueColor: Boolean,
    /** The focused tile and the glass highlight swing past their target before settling (config.xml, focus_overshoot). */
    @JvmField val focusOvershoot: Boolean,
    @JvmField val vpnApp: String,
    @JvmField val showHomeSettingsShortcut: Boolean,
    /** One line under the clock: CPU, free memory, free storage. Off unless config or a theme turns it on. */
    @JvmField val showStats: Boolean,
    /** game_day: the package of an add-on that publishes each team's next game (GameDay); empty = off. */
    @JvmField val gameDay: String,
    /** App library pill after the last Home row (config.xml app_library). */
    @JvmField val appLibrary: Boolean,
    /** OK directly toggles Home membership in the library (app_library_selection). */
    @JvmField val appLibrarySelection: Boolean,
    /** OK opens every app in the library; Add to Home stays in the hold menu (library_ok_opens). */
    @JvmField val libraryOkOpens: Boolean,
    /** Icon on a card tinted from that icon, instead of the app's banner (config.xml uniform_tiles). */
    @JvmField val uniformTiles: Boolean,
    /** Background colour behind everything, #RRGGBB; black unless a theme says otherwise. */
    @JvmField val backgroundColor: Int,
    /** Time-of-day backdrop, 0 to 100 (config.xml, sky_intensity); 0 keeps the plain background. */
    @JvmField val skyIntensity: Int,
    /** Remote keycodes that open Settings through HomeKeyService (config.xml, settings_key_keycodes). */
    @JvmField val settingsKeycodes: IntArray,
    /** The Settings button opens the quick settings panel instead of the TV's Settings (settings_key_panel). */
    @JvmField val settingsKeyPanel: Boolean,
    /** Home puts the remote's accessibility helpers back after a force stop (restore_key_services; ServiceRepair). */
    @JvmField val restoreKeyServices: Boolean,
    /** Whether themes may show a wallpaper (config.json, wallpaper). */
    @JvmField val wallpaperEnabled: Boolean,
    /** Full-screen picture behind the launcher, or null for the plain colour. */
    @JvmField val wallpaper: File?,
    wallpapers: List<File>,
    @JvmField val wallpaperRotationMinutes: Int,
    @JvmField val wallpaperDim: Int,
    /** The dock band's opacity in percent (config.json dock_opacity, 30-100): below 100 the wallpaper shows through. */
    @JvmField val dockOpacity: Int,
    /**
     * Playback order for a wallpaper collection: {@code sequential} or {@code shuffle}. Missing or
     * invalid values keep the historical shuffle so existing themes rotate exactly as before.
     */
    @JvmField val wallpaperOrder: String,
    /** Whether the overlay file was present and parsed. */
    @JvmField val overlayApplied: Boolean,
    /** Festive skin from a theme (festival, festival_until, greeting, accent_color); {@link Festival#NONE} if none. */
    @JvmField val festival: Festival,
    /** Extra keycodes that open the input switcher in InputKeyService (config.xml input_key_keycodes). */
    @JvmField val inputKeycodes: IntArray,
    appKeys: List<String>,
    /** Stock launcher whose home screen is sent back here if the firmware revives it (config.xml stock_home_redirect). */
    @JvmField val stockHomeRedirect: String,
    raceCalendar: List<RaceWeekend.Race>,
    /** Cinematic top shelf (config.xml, cinematic_home): the focused dock app's artwork behind a larger shelf. */
    @JvmField val cinematicHome: Boolean,
    /** The glow in the focused app's colour behind the dock (config.xml, ambient_glow); false keeps the background plain. */
    @JvmField val ambientGlow: Boolean,
    /** The focus highlight in the focused app's colour on every tile (config.xml, app_glow), as cinematic_home does for the dock. */
    @JvmField val appGlow: Boolean,
    /** The rows fade out under the clock instead of being cut off (config.xml, fade_rows); TopFade. */
    @JvmField val fadeRows: Boolean,
    /** Minutes Home may sit untouched before the clock screensaver (config.xml, home_idle_clock_minutes); 0 is off. */
    @JvmField val homeIdleClockMinutes: Int,
    /** Whether the key services request key filtering (config.xml or theme key_filter); false keeps key
     *  auto-repeat working on firmware whose AccessibilityInputFilter drops repeats (TCL Android 12). */
    @JvmField val keyFilter: Boolean,
    /** The dock band as light frosted glass like the pills (config.xml, dock_glass); false keeps the solid band. */
    @JvmField val dockGlass: Boolean,
    glowStyleValue: String,
    /** Show each rotating wallpaper's name from wallpapers/captions.txt (config.xml / theme wallpaper_captions). */
    @JvmField val wallpaperCaptions: Boolean,
    /** Answer VIEW_INPUTS with the Inputs menu (handle_view_inputs); see InputsRequestActivity. */
    @JvmField val handleViewInputs: Boolean,
    /** Hold OK on a tile opens a menu (Move, Remove from Home, App info) instead of App info (config.xml / theme tile_menu). */
    @JvmField val tileMenu: Boolean,
    /** The dock band's padding equals the gap between its tiles (config.xml, dock_even_padding); false keeps the fixed padding. */
    @JvmField val dockEvenPadding: Boolean,
    /** The dock band and its edge draw (dock_band); false leaves the tiles floating on the wallpaper. */
    @JvmField val dockBand: Boolean,
    /** The dock position dots under the cinematic shelf's name (config.xml, cinematic_dots); false leaves them out. */
    @JvmField val cinematicDots: Boolean,
    /** The cinematic shelf keeps the white focus highlight instead of the app's colour (config.xml, cinematic_white_focus). */
    @JvmField val cinematicWhiteFocus: Boolean,
    /** The Wi-Fi pill's icon green while the TV is online (config.xml, wifi_status_color); false keeps it white. */
    @JvmField val wifiStatusColor: Boolean,
    /** Strength of the cinematic shelf's dark scrim, 0 to 100 percent (config.xml, cinematic_scrim); 100 as before. */
    @JvmField val cinematicScrim: Int,
    /** Opacity of the app's artwork behind the cinematic shelf, 0 to 100 (config.xml, cinematic_art); 50 as before. */
    @JvmField val cinematicArt: Int,
    /** A rotating collection's groups by time of day, as file names (config.xml, wallpaper_moods); empty rotates through all of it. */
    @JvmField val wallpaperMoods: Map<String, Set<String>>,
    /** With keyFilter on, the key services stop filtering while a game controller is connected (gamepad_passthrough). */
    @JvmField val gamepadPassthrough: Boolean,
    /** With keyFilter on, the key services swallow Volume Up/Down and repeat a held one themselves (consume_volume_keys). */
    @JvmField val consumeVolumeKeys: Boolean,
    /** A Live TV window the firmware opens by itself at power-on is sent back to Home (power_on_home). */
    @JvmField val powerOnHome: Boolean,
    /** Every Home press opens this launcher, a held one too, instead of Android's long-press action (home_long_press_home). */
    @JvmField val homeLongPressHome: Boolean,
    /** With wifi_status_color, the icon orange while connected without internet (a captive portal) and red while offline (config.xml, wifi_status_warnings). */
    @JvmField val wifiStatusWarnings: Boolean,
    topBarApps: List<String>,
    /** The greeting above the clock (Good evening); false hides it (config.xml, show_greeting). */
    @JvmField val showGreeting: Boolean,
    /** The date and the line between it and the clock in the clock's full white instead of dimmed (config.xml, header_white). */
    @JvmField val headerWhite: Boolean,
    /** The clock screensaver over Home's wallpaper instead of the black glow (config.xml, clock_wallpaper). */
    @JvmField val clockWallpaper: Boolean,
    /** How dark the clock screensaver's wallpaper is, 0 to 90 % (config.xml, clock_wallpaper_dim). */
    @JvmField val clockWallpaperDim: Int,
    /** The clock screensaver's coloured glow; false leaves the clock on plain black, e.g. for an OLED (config.xml, clock_glow). */
    @JvmField val clockGlow: Boolean,
    glassStyleValue: String,
    /** Every app in the dock's row, which scrolls sideways, and no grid below it (config.xml, single_row); needs dock_row. */
    @JvmField val singleRow: Boolean,
    /** With single_row, the band runs behind every app in the row, not only the dock apps (config.xml, dock_band_all). */
    @JvmField val dockBandAll: Boolean,
    /** Lunisolar date under the date (config.json lunar_date), computed for lunarUtcOffset hours east of UTC (-1: by locale). */
    @JvmField val lunarDate: Boolean,
    @JvmField val lunarUtcOffset: Int,
    /** The date on its own line under the clock instead of beside it (config.xml, date_below_clock). */
    @JvmField val dateBelowClock: Boolean,
    /** The VPN state as its own pill after the network pill, instead of a badge inside it (config.xml, vpn_pill). */
    @JvmField val vpnPill: Boolean,
    /** Language tag the launcher shows itself in instead of the TV's, or "" for the TV's (config.xml, locale). */
    @JvmField val locale: String,
    /** Home at every power-on, whatever the firmware put in front: an input, Live TV or an app (power_on_home_always). */
    @JvmField val powerOnHomeAlways: Boolean,
    /** The Inputs menu lists only inputs reporting connected; the rest fold behind a More inputs row (config.xml / theme inputs_connected_only). */
    @JvmField val inputsConnectedOnly: Boolean,
    /** Seconds the boot's app waits for a validated network; 0 opens it at once (open_on_start_network_wait). */
    @JvmField val openOnStartNetworkWait: Int,
    /** With vpn_pill, the VPN pill as a round lock button (config.xml / theme icon_only_vpn). */
    @JvmField val iconOnlyVpn: Boolean,
    /** Verbose remote-key logging for the key services (config.xml or theme remote_keys_debug); off by default. */
    @JvmField val remoteKeysDebug: Boolean,
    /** Opt-in rescue: the setup recovery window (setupwraith RecoveryActivity) is sent back here when the
     *  configured stock launcher is disabled (config.xml recovery_home_redirect). Off by default. */
    @JvmField val recoveryHomeRedirect: Boolean
) {
    @JvmField val dockApps: List<String>

    @JvmField val hiddenApps: List<String>

    /** Packages pre-seeded into the App library's "added" set (config.xml, library_apps); empty by default. */
    @JvmField val libraryApps: List<String>

    /** Packages the App library catalogue never offers, even with app_library on (config.xml, library_excluded_apps). */
    @JvmField val libraryExcludedApps: List<String>

    /** Package to the action its tile sends instead of opening the start screen (config.xml, launch_actions). */
    @JvmField val launchActions: Map<String, String>

    /** Package to the name its tile shows instead of the app's label (config.xml, app_labels). */
    @JvmField val appLabels: Map<String, String>

    /** Package to a bundled banner_* drawable its tile shows instead of the app's art (config.xml, app_banners). */
    @JvmField val appBanners: Map<String, String>

    /** Vendor source numbers for passthrough inputs (config.xml or theme input_sources). */
    @JvmField val inputSources: List<String>

    /** Remote buttons that open an app, "keycode:package" or "KEYCODE_NAME:package" (config.xml app_keys). */
    @JvmField val appKeys: List<String>

    /** Rotating theme images, or the single wallpaper when no collection is configured. */
    @JvmField val wallpapers: List<File>

    /** F1 weekends from a theme's race_calendar (see RaceWeekend); empty when the theme has none. */
    @JvmField val raceCalendar: List<RaceWeekend.Race>

    /** Shape of the glow behind the focused app (config.xml, glow_style): {@link #GLOW_RADIAL} or {@link #GLOW_HORIZON}. */
    @JvmField val glowStyle: String

    /** The focus highlight's look (config.xml, glass_style): {@link #GLASS_DEFAULT}, {@link #GLASS_RIM} or {@link #GLASS_HALO}. */
    @JvmField val glassStyle: String

    /** Apps shown as round logo buttons in the top bar, left of the network pill, and no longer as tiles (config.xml, top_bar_apps). */
    @JvmField val topBarApps: List<String>

    init {
        this.appKeys = Collections.unmodifiableList(appKeys)
        this.dockApps = Collections.unmodifiableList(dockApps)
        this.hiddenApps = Collections.unmodifiableList(hiddenApps)
        this.libraryApps = Collections.unmodifiableList(libraryApps)
        this.libraryExcludedApps = Collections.unmodifiableList(libraryExcludedApps)
        this.launchActions = Collections.unmodifiableMap(launchActions)
        this.appLabels = Collections.unmodifiableMap(appLabels)
        this.appBanners = Collections.unmodifiableMap(appBanners)
        this.inputSources = Collections.unmodifiableList(inputSources)
        this.wallpapers = Collections.unmodifiableList(wallpapers)
        this.raceCalendar = Collections.unmodifiableList(raceCalendar)
        this.topBarApps = Collections.unmodifiableList(topBarApps)
        this.glowStyle = glowStyle(glowStyleValue)
        this.glassStyle = glassStyle(glassStyleValue)
    }

    companion object {
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
        fun load(ctx: Context): Config = ConfigLoader.load(ctx)

        /** The dock band opacity chosen in Launcher settings, or the theme's when none (or an out-of-range one) is saved. */
        @JvmStatic
        fun chosenDockOpacity(chosen: Int, theme: Int): Int {
            return if (chosen in 30..100) chosen else theme
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
    }
}
