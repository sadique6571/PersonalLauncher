package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.function.IntConsumer

internal class HomeSettingsMenus(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val settingsTransfer: HomeSettingsTransfer,
    private val refreshApps: () -> Unit,
    private val buildHomeSoon: () -> Unit,
    private val updateClock: () -> Unit,
    private val resetClockTick: (Boolean) -> Unit,
    private val configureHarmonyClock: () -> Unit,
    private val reloadIcons: () -> Unit,
    private val setAppHeadingSize: (Int) -> Unit,
    private val wallpaper: () -> HomeWallpaperView?,
    private val switchVisualStyle: (Int) -> Unit,
    private val resetCurrentTheme: () -> Unit,
    private val applyAppearance: () -> Unit,
    private val profileAsset: (String) -> File,
    private val loadWallpaper: () -> Unit,
    private val rearrangeApps: () -> Unit,
    private val requestWatchNextAccess: () -> Unit,
    private val editProfile: () -> Unit,
    private val applyPerformanceProfile: () -> Unit,
    private val cachedApps: () -> List<PersonalApp>?,
    private val loadApps: () -> Unit,
    private val accentColor: () -> Int,
    private val activateWeatherWidget: () -> Unit,
    private val showForecast: () -> Unit,
    private val launch: (Intent) -> Unit,
    private val armIdleScreensaver: () -> Unit,
) {
    private fun harmony(): Boolean = ThemeStyle.harmony(prefs)
    private fun glassOs(): Boolean = ThemeStyle.id(prefs) == ThemeStyle.GLASS || harmony()
    private fun clockVisible(): Boolean = !prefs.getBoolean("hide_clock", false) && !prefs.getBoolean("apps_only", false)
    private fun gradientWallpaper(): Boolean = glassOs() && "solid" == prefs.getString("wallpaper_mode", "solid")
    private fun headerBgMode(): Int = HeaderAppearance.mode(prefs)
    private fun weatherWidgetEnabled(): Boolean =
        ((harmony() && tv) || prefs.getBoolean("weather_widget", ThemeStyle.id(prefs) == ThemeStyle.GLASS)) &&
            !prefs.getBoolean("apps_only", false)
    private fun hasFavoriteWallpaper(): Boolean = profileAsset("favorite_wallpaper.jpg").isFile
    private fun appLabelSizes(): IntArray =
        if (glassOs()) intArrayOf(14, 16, 20, 22) else intArrayOf(14, 17, 20, 24)

    private fun str(res: Int): String = host.getString(res)
    private fun arr(res: Int): Array<String> = host.resources.getStringArray(res)

    /** Canonical chooser key: the s_* resource entry name, stable across locales. */
    private fun key(res: Int): String = host.resources.getResourceEntryName(res)

    /** Localized chooser: displays getString(titleRes), matches current option by canonical key. */
    private fun chooseKeyed(titleRes: Int, options: Array<String>, selected: IntConsumer) {
        GlassSettings.choices(host, str(titleRes), options,
            { HomeSettingsChoices.currentIndex(key(titleRes), prefs, tv, glassOs(), this::headerBgMode,
                this::appLabelSizes, this::hasFavoriteWallpaper, this::gradientWallpaper) },
            { which ->
                selected.accept(which)
                GlassSettings.refresh(host)
            })
    }

    private fun chooseKeyed(titleRes: Int, optionsRes: Int, selected: IntConsumer) =
        chooseKeyed(titleRes, arr(optionsRes), selected)

    fun harmonyClockSettings() {
        if (!harmony()) return
        val names = arrayOf(str(R.string.s_watch_face), str(R.string.s_metal_finish), str(R.string.s_clock_accent),
            str(R.string.s_second_hand), str(R.string.s_time_format), str(R.string.s_clock_size),
            str(R.string.s_hide_clock), str(R.string.s_daily_thoughts))
        val hints = arrayOf(str(R.string.hint_watch_face), str(R.string.hint_metal_finish),
            str(R.string.hint_clock_accent), str(R.string.hint_second_hand), str(R.string.hint_time_format),
            str(R.string.hint_harmony_clock_size), str(R.string.hint_hide_dial), str(R.string.hint_daily_thoughts))
        val icons = intArrayOf(R.drawable.ic_clock, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_clock,
            R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock)
        GlassSettings.options(host, str(R.string.s_harmony_clock), names, hints, icons, { which ->
            when (which) {
                0 -> chooseKeyed(R.string.s_watch_face, R.array.o_watch_faces) { i ->
                    prefs.edit().putInt("harmony_clock_face", i).apply()
                    configureHarmonyClock()
                }
                1 -> chooseKeyed(R.string.s_metal_finish, R.array.o_metal_finish) { i ->
                    prefs.edit().putInt("harmony_clock_finish", i).apply()
                    configureHarmonyClock()
                }
                2 -> chooseKeyed(R.string.s_clock_accent, R.array.o_clock_accent) { i ->
                    prefs.edit().putInt("harmony_clock_accent", i).apply()
                    configureHarmonyClock()
                }
                3 -> chooseKeyed(R.string.s_second_hand, R.array.o_second_hand) { i ->
                    prefs.edit().putBoolean("harmony_clock_sweep", i == 0).apply()
                    configureHarmonyClock()
                }
                4 -> chooseKeyed(R.string.s_time_format, R.array.o_time_format) { i ->
                    prefs.edit().putBoolean("time_24h", i == 1).apply()
                    configureHarmonyClock()
                    updateClock()
                }
                5 -> chooseKeyed(R.string.s_harmony_clock_size, R.array.o_sizes3) { i ->
                    prefs.edit().putInt("harmony_clock_size", i).apply()
                    buildHomeSoon()
                }
                6 -> chooseKeyed(R.string.s_hide_clock, R.array.o_show_hide) { i ->
                    prefs.edit().putBoolean("hide_clock", i == 1).apply()
                    buildHomeSoon()
                }
                7 -> chooseKeyed(R.string.s_daily_thoughts, R.array.o_on_off) { i ->
                    prefs.edit().putBoolean("harmony_daily_quote", i == 0).apply()
                    buildHomeSoon()
                }
            }
        }, { which -> which >= 6 || clockVisible() })
    }

    private fun chooseAccentColor() {
        val colors = intArrayOf(0xff40d9ff.toInt(), 0xffff9a5c.toInt(), 0xffb388ff.toInt(), 0xff69f0ae.toInt(), 0xffff5252.toInt(), 0xffffea00.toInt(), 0xffff80ab.toInt(), 0xffffffff.toInt(),
            0xff000000.toInt(), 0xff343b46.toInt(), 0xffcbd3df.toInt(), 0xff508dff.toInt(), 0xff44c6bc.toInt(), 0xffe8c66a.toInt(), 0xffd890a5.toInt(), 0xff7986ed.toInt())
        chooseKeyed(R.string.s_custom_accent, R.array.o_accent_colors) { which ->
            prefs.edit().putInt("theme_name", 3).putInt("custom_accent", colors[which]).apply()
            applyAppearance()
            refreshApps()
        }
    }

    fun launcherSettings() {
        val catKeys = intArrayOf(R.string.s_tiles, R.string.s_clock, R.string.s_theme,
            R.string.s_wallpaper, R.string.s_weather, R.string.s_apps, R.string.s_system)
        val cats = Array(catKeys.size) { str(catKeys[it]) }
        val catHints = arrayOf(str(R.string.hint_tiles), str(R.string.hint_clock), str(R.string.hint_theme),
            str(R.string.hint_wallpaper), str(R.string.hint_weather), str(R.string.hint_apps), str(R.string.hint_system))
        val catIcons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_clock, R.drawable.ic_palette,
            R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_apps, R.drawable.ic_settings)
        GlassSettings.options(host, str(R.string.s_launcher_settings), cats, catHints, catIcons, { which ->
            when (which) {
                0 -> tileSettings()
                1 -> clockSettings()
                2 -> themeSettings()
                3 -> wallpaperMenu()
                4 -> weatherMenu()
                5 -> appMenu()
                else -> systemMenu()
            }
        }, { which -> showsSetting(key(catKeys[which])) })
    }

    private fun subMenu(titleRes: Int, labelRes: IntArray, hintRes: IntArray, icons: IntArray, choices: Array<Runnable>) {
        val labels = Array(labelRes.size) { str(labelRes[it]) }
        val hints = Array(hintRes.size) { str(hintRes[it]) }
        GlassSettings.options(host, str(titleRes), labels, hints, icons, { which -> choices[which].run() },
            { which -> showsSetting(key(labelRes[which])) })
    }

    /** Visibility rules match canonical s_* keys, never displayed text. */
    private fun showsSetting(k: String): Boolean {
        return when (k) {
            "s_tiles" -> !harmony()
            "s_app_columns", "s_tile_size", "s_tile_spacing", "s_tile_corners", "s_logo_size", "s_app_label_size" -> !harmony()
            "s_focus_border" -> ThemeStyle.id(prefs) == ThemeStyle.CLASSIC
            "s_focus_glow" -> !gradientWallpaper()
            "s_launcher_font" -> !ThemeStyle.fixedTypography(prefs)
            "s_clock_style", "s_clock_shadow", "s_hijri_adjust" -> clockVisible()
            "s_clock_size", "s_time_format", "s_clock_seconds" -> digitalClockVisible()
            "s_clock_font" -> digitalClockVisible() && !ThemeStyle.fixedTypography(prefs)
            "s_clock_align" -> digitalClockVisible() && !ThemeStyle.fixedTypography(prefs) && !weatherWidgetEnabled()
            "s_clock_glow" -> clockVisible() && !gradientWallpaper()
            "s_glass_edge" -> ThemeStyle.id(prefs) != ThemeStyle.CLASSIC || headerBgMode() != 0
            "s_glass_finish" -> ThemeStyle.id(prefs) != ThemeStyle.CLASSIC || headerBgMode() == 1
                || (PanelTransparency.enabled(prefs) && GlassSettings.frostedAvailable(host))
            "s_wallpaper_darkness", "s_bottom_shade" -> "solid" != prefs.getString("wallpaper_mode", if (glassOs()) "solid" else "mountains")
            else -> true
        }
    }

    private fun digitalClockVisible(): Boolean {
        return clockVisible() && prefs.getInt("clock_style", 0) != 1
    }

    private fun appTitleSizeRes(): Int =
        if (ThemeStyle.id(prefs) == ThemeStyle.CLASSIC) R.string.s_focus_title_size else R.string.s_app_label_size

    private fun tileSettings() {
        val classic = ThemeStyle.id(prefs) == ThemeStyle.CLASSIC
        val labelRes = intArrayOf(R.string.s_app_columns, R.string.s_tile_size, R.string.s_tile_spacing,
            R.string.s_tile_corners, R.string.s_logo_size, appTitleSizeRes(), R.string.s_focus_border)
        val hintRes = intArrayOf(R.string.hint_app_columns, R.string.hint_tile_size, R.string.hint_tile_spacing,
            R.string.hint_tile_corners, R.string.hint_logo_size,
            if (classic) R.string.hint_focus_title else R.string.hint_app_label, R.string.hint_focus_border)
        val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_settings, R.drawable.ic_apps)
        val choices = arrayOf<Runnable>(
            Runnable { chooseKeyed(R.string.s_app_columns, R.array.o_app_columns) { which ->
                prefs.edit().putInt(if (tv) "tv_columns" else "phone_columns", if (which == 0) 0 else which + 1).apply()
                refreshApps()
            } },
            Runnable { chooseKeyed(R.string.s_tile_size, R.array.o_tile_size) { which ->
                prefs.edit().putBoolean("large_tiles", which == 1).putInt(if (tv) "tv_columns" else "phone_columns", 0).apply()
                refreshApps()
            } },
            Runnable { chooseKeyed(R.string.s_tile_spacing, R.array.o_tile_spacing) { which ->
                prefs.edit().putInt("tile_spacing", which).apply()
                refreshApps()
            } },
            Runnable { chooseKeyed(R.string.s_tile_corners, R.array.o_tile_corners) { which ->
                prefs.edit().putInt("tile_corners", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_logo_size, R.array.o_sizes3) { which ->
                prefs.edit().putInt("logo_size", which).apply()
                reloadIcons()
            } },
            Runnable { chooseKeyed(appTitleSizeRes(), R.array.o_sizes4) { which ->
                if (!classic) { prefs.edit().putInt("app_label_size", appLabelSizes()[which]).apply(); refreshApps() }
                else { val size = intArrayOf(24, 32, 40, 48)[which]; prefs.edit().putInt("focus_title_size", size).apply(); setAppHeadingSize(size) }
            } },
            Runnable { chooseKeyed(R.string.s_focus_border, R.array.o_focus_border) { which ->
                prefs.edit().putInt("focus_border", which).apply()
                refreshApps()
            } }
        )
        subMenu(R.string.s_tiles, labelRes, hintRes, icons, choices)
    }

    private fun clockSettings() {
        if (harmony() && tv) { harmonyClockSettings(); return }
        val labelRes = intArrayOf(R.string.s_clock_size, R.string.s_clock_style, R.string.s_clock_font,
            R.string.s_time_format, R.string.s_clock_align, R.string.s_clock_seconds, R.string.s_clock_glow,
            R.string.s_clock_shadow, R.string.s_hide_clock, R.string.s_hide_greeting, R.string.s_hijri_adjust)
        val hintRes = intArrayOf(R.string.hint_clock_size, R.string.hint_clock_style, R.string.hint_clock_font,
            R.string.hint_time_format, R.string.hint_clock_align, R.string.hint_clock_seconds, R.string.hint_clock_glow,
            R.string.hint_clock_shadow, R.string.hint_hide_clock, R.string.hint_hide_greeting, R.string.hint_hijri)
        val icons = IntArray(11) { R.drawable.ic_clock }
        val choices = arrayOf<Runnable>(
            Runnable { chooseKeyed(R.string.s_clock_size, R.array.o_sizes3) { which ->
                prefs.edit().putInt("clock_size", (if (ThemeStyle.custom(prefs)) DemoGeometry.clockSizes(tv) else if (tv) intArrayOf(42, 52, 62) else intArrayOf(36, 44, 52))[which]).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_clock_style, R.array.o_clock_style) { which ->
                prefs.edit().putInt("clock_style", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_clock_font, R.array.o_clock_font) { which ->
                prefs.edit().putInt("clock_font", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_time_format, R.array.o_time_format) { which ->
                prefs.edit().putBoolean("time_24h", which == 1).apply()
                updateClock()
            } },
            Runnable { chooseKeyed(R.string.s_clock_align, R.array.o_clock_align) { which ->
                prefs.edit().putInt("clock_align", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_clock_seconds, R.array.o_off_on) { which ->
                prefs.edit().putBoolean("clock_seconds", which == 1).apply()
                resetClockTick(which == 1)
            } },
            Runnable { chooseKeyed(R.string.s_clock_glow, R.array.o_off_on) { which ->
                prefs.edit().putBoolean("clock_glow", which == 1).apply()
                wallpaper()?.invalidate()
            } },
            Runnable { chooseKeyed(R.string.s_clock_shadow, R.array.o_off_on) { which ->
                prefs.edit().putBoolean("clock_shadow", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_hide_clock, R.array.o_show_hide) { which ->
                prefs.edit().putBoolean("hide_clock", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_hide_greeting, R.array.o_show_hide) { which ->
                prefs.edit().putBoolean("hide_greeting", which == 1).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_hijri_adjust, R.array.o_hijri) { which ->
                prefs.edit().putInt("hijri_adjustment", which - 2).apply(); updateClock()
            } }
        )
        subMenu(R.string.s_clock, labelRes, hintRes, icons, choices)
    }

    fun themeSettings() {
        val labelRes = intArrayOf(R.string.s_visual_style, R.string.s_accent_preset, R.string.s_custom_accent,
            R.string.s_launcher_font, R.string.s_glass_intensity, R.string.s_glass_finish, R.string.s_glass_edge,
            R.string.s_panel_transparency, R.string.s_header_bg, R.string.s_reset_theme)
        val hintRes = intArrayOf(R.string.hint_visual_style, R.string.hint_accent_preset, R.string.hint_custom_accent,
            R.string.hint_launcher_font, R.string.hint_glass_intensity, R.string.hint_glass_finish, R.string.hint_glass_edge,
            R.string.hint_panel_transparency, R.string.hint_header_bg, R.string.hint_reset_theme)
        val icons = intArrayOf(R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, android.R.drawable.ic_popup_sync)
        val choices = arrayOf<Runnable>(
            Runnable { chooseKeyed(R.string.s_visual_style, R.array.o_visual_styles) { switchVisualStyle(it) } },
            Runnable { chooseKeyed(R.string.s_accent_preset, arrayOf(
                if (ThemeStyle.custom(prefs)) str(R.string.o_theme_default) else if (harmony()) str(R.string.o_harmony_blue) else str(R.string.o_midnight_cyan),
                str(R.string.o_sunset_orange), str(R.string.o_deep_purple), str(R.string.o_custom))) { which ->
                prefs.edit().putInt("theme_name", which).apply()
                if (which == 3) chooseAccentColor()
                else { applyAppearance(); refreshApps() }
            } },
            Runnable { chooseAccentColor() },
            Runnable { chooseKeyed(R.string.s_launcher_font, R.array.o_launcher_fonts) { which ->
                prefs.edit().putInt("app_font", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_glass_intensity, R.array.o_glass_intensity) { which ->
                prefs.edit().putInt("glass_alpha", intArrayOf(30, 70, 100)[which]).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_glass_finish, R.array.o_glass_finish) { which ->
                prefs.edit().putInt("glass_finish", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_glass_edge, R.array.o_glass_edge) { which ->
                prefs.edit().putInt("glass_edge", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_panel_transparency, R.array.o_on_off) { which ->
                prefs.edit().putBoolean("panel_transparency", which == 0).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_header_bg, if (glassOs()) R.array.o_header_bg_glass else R.array.o_header_bg_full) { which ->
                prefs.edit().putInt("header_bg_mode", which).apply()
                buildHomeSoon()
            } },
            Runnable { chooseKeyed(R.string.s_reset_theme, R.array.o_reset_theme) { which ->
                if (which == 1) resetCurrentTheme()
            } }
        )
        subMenu(R.string.s_theme, labelRes, hintRes, icons, choices)
    }

    private fun wallpaperMenu() {
        val labelRes = intArrayOf(R.string.s_wallpaper, R.string.s_wallpaper_source, R.string.s_wallpaper_darkness,
            R.string.s_bottom_shade, R.string.s_focus_glow)
        val hintRes = intArrayOf(R.string.hint_own_picture, R.string.hint_wallpaper_source,
            R.string.hint_wallpaper_darkness, R.string.hint_bottom_shade, R.string.hint_focus_glow)
        val icons = intArrayOf(R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness)
        val choices = arrayOf<Runnable>(
            Runnable(::wallpaperSettings),
            Runnable(::chooseWallpaperSource),
            Runnable { chooseKeyed(R.string.s_wallpaper_darkness, R.array.o_wallpaper_darkness) { which ->
                prefs.edit().putInt("background_dim", which * 20).apply()
                wallpaper()?.invalidate()
            } },
            Runnable { chooseKeyed(R.string.s_bottom_shade, R.array.o_off_on) { which ->
                prefs.edit().putBoolean("bottom_shade", which == 1).apply()
                wallpaper()?.invalidate()
            } },
            Runnable { chooseKeyed(R.string.s_focus_glow, R.array.o_on_off) { which ->
                prefs.edit().putBoolean("focus_glow", which == 0).apply()
                wallpaper()?.focusAccent = 0
                wallpaper()?.invalidate()
            } }
        )
        subMenu(R.string.s_wallpaper, labelRes, hintRes, icons, choices)
    }

    private fun chooseWallpaperSource() {
        // Index contract (persisted): 0 Built-in, 1 Bing daily, [2 Favorite], [2|3 Gradient only].
        val sources = arrayListOf(str(R.string.o_builtin), str(R.string.o_bing_daily))
        if (hasFavoriteWallpaper()) sources.add(str(R.string.o_favorite))
        val gradientLabel = str(R.string.o_gradient_only)
        if (glassOs()) sources.add(gradientLabel)
        chooseKeyed(R.string.s_wallpaper_source, sources.toTypedArray()) { which ->
            val gradient = gradientLabel == sources[which]
            prefs.edit().putInt("wallpaper_source", if (gradient) 0 else which)
                .putString("wallpaper_mode", if (gradient) "solid" else "mountains").apply()
            buildHomeSoon()
        }
    }

    private fun appMenu() {
        if (harmony()) {
            subMenu(R.string.s_apps,
                intArrayOf(R.string.s_hide_unhide, R.string.s_rearrange),
                intArrayOf(R.string.hint_hide_unhide, R.string.hint_rearrange),
                intArrayOf(R.drawable.ic_apps, R.drawable.ic_swap_vert),
                arrayOf<Runnable>(Runnable(::hiddenApps), Runnable { rearrangeApps() }))
            return
        }
        val labelRes = intArrayOf(R.string.s_hide_unhide, R.string.s_rearrange, R.string.s_continue_watching)
        val hintRes = intArrayOf(R.string.hint_hide_unhide, R.string.hint_rearrange, R.string.hint_continue_watching)
        val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_swap_vert, R.drawable.ic_livetv)
        val choices = arrayOf<Runnable>(
            Runnable(::hiddenApps),
            Runnable { rearrangeApps() },
            Runnable { chooseKeyed(R.string.s_continue_watching, R.array.o_continue_watching) { which ->
                if (which == 2) requestWatchNextAccess()
                else {
                    val settings = prefs.edit().putBoolean("continue_watching", which == 0)
                    if (which == 0) settings.putBoolean("apps_only", false)
                    settings.apply()
                    buildHomeSoon()
                }
            } }
        )
        subMenu(R.string.s_apps, labelRes, hintRes, icons, choices)
    }

    private fun systemMenu() {
        val labelRes = intArrayOf(R.string.s_screensaver_weather, R.string.s_profile, R.string.s_performance,
            R.string.s_updates, R.string.s_export, R.string.s_import, R.string.s_reset_layout, R.string.compose_apps_menu)
        val hintRes = intArrayOf(R.string.hint_screensaver_weather, R.string.hint_profile, R.string.hint_performance,
            R.string.hint_updates, R.string.hint_export, R.string.hint_import, R.string.hint_reset_layout, R.string.compose_apps_hint)
        val icons = intArrayOf(R.drawable.ic_picture, R.drawable.ic_person, R.drawable.ic_settings, R.drawable.ic_internet, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_apps)
        val choices = arrayOf<Runnable>(
            Runnable(::screensaverSettings),
            Runnable { editProfile() },
            Runnable { chooseKeyed(R.string.s_performance, R.array.o_perf) { which ->
                prefs.edit().putInt("perf_mode", which).apply()
                applyPerformanceProfile()
                buildHomeSoon()
            } },
            Runnable { LauncherUpdatesUi.show(host) },
            Runnable { settingsTransfer.exportSettings() },
            Runnable { settingsTransfer.importSettings() },
            Runnable { ThemeDialogBuilder(host).setTitle(R.string.h_reset_layout_title).setPositiveButton(R.string.h_reset) { _, _ ->
                prefs.edit().remove("app_order").remove("hidden_apps").remove("tv_columns").remove("phone_columns").remove("large_tiles")
                    .remove("focus_title_size").remove("clock_size").remove("background_dim").remove("theme_mode")
                    .remove("wallpaper_mode").remove("focus_glow").remove("continue_watching")
                    .remove("header_bg_mode").remove("header_bg").remove("header_blur").apply()
                buildHomeSoon()
            }.setNegativeButton(android.R.string.cancel, null).show() },
            Runnable { host.startActivity(Intent(host, ComposeAppsActivity::class.java)) }
        )
        subMenu(R.string.s_system, labelRes, hintRes, icons, choices)
    }

    private fun wallpaperSettings() {
        chooseKeyed(R.string.s_home_wallpaper, R.array.o_home_wallpaper) { which ->
            if (which == 0) {
                try { host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 8) }
                catch (unavailable: ActivityNotFoundException) { Toast.makeText(host, R.string.h_no_picker, Toast.LENGTH_LONG).show() }
                catch (unavailable: SecurityException) { Toast.makeText(host, R.string.h_no_picker, Toast.LENGTH_LONG).show() }
            } else {
                if (!profileAsset("personal-wallpaper.jpg").exists()) { Toast.makeText(host, R.string.h_choose_picture_first, Toast.LENGTH_SHORT).show(); return@chooseKeyed }
                prefs.edit().putString("wallpaper_mode", "custom").putInt("wallpaper_source", 0).apply()
                loadWallpaper()
            }
        }
    }

    private fun hiddenApps() {
        val apps = cachedApps()
        if (apps == null) { loadApps(); return }
        val hidden = HashSet(prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet())
        val rows = LinearLayout(host)
        rows.orientation = LinearLayout.VERTICAL
        val dialog = GlassSettings.panel(host, str(R.string.s_show_hide_apps), rows)
        for (app in apps) {
            val pkg = app.info.activityInfo.packageName
            val row = GlassSettings.row(host, app.name,
                str(if (hidden.contains(pkg)) R.string.h_hidden_select_show else R.string.h_visible_select_hide),
                R.drawable.ic_apps, accentColor(), Runnable {})
            row.setOnClickListener { _ ->
                if (!hidden.add(pkg)) hidden.remove(pkg)
                prefs.edit().putStringSet("hidden_apps", HashSet(hidden)).apply()
                val copy = (row as LinearLayout).getChildAt(1) as LinearLayout
                (copy.getChildAt(1) as TextView).text =
                    str(if (hidden.contains(pkg)) R.string.h_hidden_select_show else R.string.h_visible_select_hide)
                row.contentDescription = app.name +
                    str(if (hidden.contains(pkg)) R.string.h_hidden_suffix else R.string.h_visible_suffix)
                refreshApps()
            }
            rows.addView(row)
        }
        dialog.show()
    }

    private fun weatherMenu() {
        GlassSettings.options(host, str(R.string.s_weather),
            arrayOf(str(R.string.s_weather_widget), str(R.string.s_widget_animation), str(R.string.s_weather_city),
                str(R.string.s_temp_unit), str(R.string.s_forecast)),
            arrayOf(str(R.string.hint_weather_widget), str(R.string.hint_widget_animation), str(R.string.hint_weather_city),
                str(R.string.hint_temp_unit), str(R.string.hint_forecast)),
            intArrayOf(R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness), { which ->
                if (which == 0) chooseKeyed(R.string.s_weather_widget, R.array.o_on_off) { value ->
                    val edit = prefs.edit().putBoolean("weather_widget", value == 0)
                    if (value == 0) edit.putBoolean("apps_only", false)
                    edit.apply()
                    buildHomeSoon()
                }
                else if (which == 1) chooseKeyed(R.string.s_widget_animation, R.array.o_on_off) { value ->
                    prefs.edit().putBoolean("weather_widget_animation", value == 0).apply()
                    activateWeatherWidget()
                }
                else if (which == 2) WeatherSettings.show(host)
                else if (which == 3) chooseKeyed(R.string.s_temp_unit, R.array.o_temp_unit) { value ->
                    prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply()
                }
                else showForecast()
            }, { which -> if (which == 0) !(harmony() && tv) else which != 1 || weatherWidgetEnabled() })
    }

    private fun screensaverSettings() {
        GlassSettings.navigation(host, str(R.string.s_screensaver),
            arrayOf(str(R.string.s_preview), str(R.string.s_weather_city), str(R.string.s_temp_unit),
                str(R.string.s_idle_start_setting), str(R.string.s_auto_screensaver)),
            { which ->
                if (which == 0) launch(Intent(host, LandscapeActivity::class.java))
                else if (which == 1) WeatherSettings.show(host)
                else if (which == 2) chooseKeyed(R.string.s_temp_unit, R.array.o_temp_unit) { value ->
                    prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply()
                }
                else if (which == 3) chooseKeyed(R.string.s_idle_start, R.array.o_idle_start) { value ->
                    prefs.edit().putInt("idle_screensaver_minutes", intArrayOf(0, 2, 5, 10, 15)[value]).apply()
                    armIdleScreensaver()
                }
                else launch(Intent(host, ScreensaverSetupActivity::class.java))
            })
    }
}
