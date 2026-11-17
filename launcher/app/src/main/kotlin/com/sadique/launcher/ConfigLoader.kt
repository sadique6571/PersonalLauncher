package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.sadique.launcher.ThemeConfigJson.bool
import com.sadique.launcher.ThemeConfigJson.color
import com.sadique.launcher.ThemeConfigJson.intArray
import com.sadique.launcher.ThemeConfigJson.integer
import com.sadique.launcher.ThemeConfigJson.moods
import com.sadique.launcher.ThemeConfigJson.raceCalendar
import com.sadique.launcher.ThemeConfigJson.string
import com.sadique.launcher.ThemeConfigJson.stringList
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import com.sadique.launcher.Config.Companion.dir
import com.sadique.launcher.Config.Companion.FILE_NAME
import com.sadique.launcher.Config.Companion.SETTINGS_NAME
import com.sadique.launcher.Config.Companion.ROWS_BELOW_DOCK_KEY
import com.sadique.launcher.Config.Companion.WALLPAPER_NAME
import com.sadique.launcher.Config.Companion.chosenDockOpacity
import com.sadique.launcher.Config.Companion.DOCK_OPACITY_KEY
import com.sadique.launcher.Config.Companion.WALLPAPER_CHOICE_KEY
import com.sadique.launcher.Config.Companion.packageMap

internal object ConfigLoader {
    private const val THEME_REVISION_NAME = "theme_revision"
    private const val THEME_SIGNATURE_KEY = "theme_signature"

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
        val json = ThemeConfigOverlay.readOverlay(ctx, FILE_NAME, file, mounted)
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
            dateBelowClock, vpnPill, powerOnHomeAlways, inputsConnectedOnly,
            Math.max(0, Math.min(600, openOnStartNetworkWait)), iconOnlyVpn, remoteKeysDebug, recoveryHomeRedirect)
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
}
