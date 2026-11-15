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
    private val chooseSetting: (String, Array<String>, IntConsumer) -> Unit,
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

    fun harmonyClockSettings() {
        if (!harmony()) return
        val names = arrayOf("Watch face", "Metal finish", "Clock accent", "Second hand", "Time format", "Clock size", "Hide clock", "Daily thoughts")
        val hints = arrayOf("Heritage, Chronograph, Skeleton", "Space Blue, Obsidian, Titanium, Ceramic",
                "Ice blue, Gold or Rose", "Smooth sweep or Quartz tick", "12-hour or 24-hour",
                "Fit the clock on the TV", "Show or hide the dial", "An original thought for every day")
        val icons = intArrayOf(R.drawable.ic_clock, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_clock,
                R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock)
        GlassSettings.options(host, "Harmony Vision Clock", names, hints, icons, { which ->
            when (which) {
                0 -> chooseSetting("Watch face", arrayOf("Heritage", "Chronograph", "Skeleton")) { i ->
                    prefs.edit().putInt("harmony_clock_face", i).apply()
                    configureHarmonyClock()
                }
                1 -> chooseSetting("Metal finish", arrayOf("Space Blue", "Obsidian", "Titanium", "Ceramic")) { i ->
                    prefs.edit().putInt("harmony_clock_finish", i).apply()
                    configureHarmonyClock()
                }
                2 -> chooseSetting("Clock accent", arrayOf("Ice Blue", "Gold", "Rose")) { i ->
                    prefs.edit().putInt("harmony_clock_accent", i).apply()
                    configureHarmonyClock()
                }
                3 -> chooseSetting("Second hand", arrayOf("Smooth sweep", "Quartz tick")) { i ->
                    prefs.edit().putBoolean("harmony_clock_sweep", i == 0).apply()
                    configureHarmonyClock()
                }
                4 -> chooseSetting("Time format", arrayOf("12-hour", "24-hour")) { i ->
                    prefs.edit().putBoolean("time_24h", i == 1).apply()
                    configureHarmonyClock()
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

    fun launcherSettings() {
        val cats = arrayOf("Tiles", "Clock", "Theme", "Wallpaper", "Weather", "Apps", "System")
        val catHints = arrayOf("App cards layout", "Time display", "Colors & appearance", "Background", "Widget, city and forecast", "Your apps", "Launcher system")
        val catIcons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_clock, R.drawable.ic_palette,
                R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_apps, R.drawable.ic_settings)
        GlassSettings.options(host, "Launcher settings", cats, catHints, catIcons, { which ->
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


    private fun subMenu(title: String, labels: Array<String>, hints: Array<String>, icons: IntArray, choices: Array<Runnable>) {
        GlassSettings.options(host, title, labels, hints, icons, { which -> choices[which].run() },
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
                        || (PanelTransparency.enabled(prefs) && GlassSettings.frostedAvailable(host))
            "Wallpaper darkness", "Bottom shade" -> "solid" != prefs.getString("wallpaper_mode", if (glassOs()) "solid" else "mountains")
            else -> true
        }
    }


    private fun digitalClockVisible(): Boolean {
        return clockVisible() && prefs.getInt("clock_style", 0) != 1
    }


    private fun appTitleSizeLabel(): String {
        return if (ThemeStyle.id(prefs) == ThemeStyle.CLASSIC) "Focused app title size" else "App label size"
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
                reloadIcons()
            } },
            Runnable { chooseSetting(appTitleSizeLabel(), arrayOf("Small", "Normal", "Large", "Extra large")) { which ->
                if (ThemeStyle.id(prefs) != ThemeStyle.CLASSIC) { prefs.edit().putInt("app_label_size", appLabelSizes()[which]).apply(); refreshApps() }
                else { val size = intArrayOf(24, 32, 40, 48)[which]; prefs.edit().putInt("focus_title_size", size).apply(); setAppHeadingSize(size) }
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
                resetClockTick(which == 1)
            } },
            Runnable { chooseSetting("Clock glow", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("clock_glow", which == 1).apply()
                wallpaper()?.invalidate()
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


    fun themeSettings() {
        // "Dark mode" removed 2026-11-01 (user): the theme glass color covers it.
        val labels = arrayOf("Visual style", "Accent preset", "Custom accent color", "Launcher font", "Glass intensity", "Glass finish", "Glass edge", "Panel transparency", "Header background", "Reset current theme")
        val hints = arrayOf("Switch theme", "Cyan, Orange, Purple or Custom", "Custom theme color", "Font for the launcher", "Transparency of panels", "Soft, Clear or Frosted panels", "Reflection around glass surfaces", "Solid panels when off; app drawer stays transparent", "Status-bar glass in host theme", "Restore defaults for the active style")
        val icons = intArrayOf(R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, android.R.drawable.ic_popup_sync)
        val choices = arrayOf<Runnable>(
            Runnable { chooseSetting("Visual style", arrayOf("Sadique Glass OS", "Classic", "Dune Loom", "Pearl Current", "Cobalt Rail", "Sage Fold", "Pine Mist", "Amber Night", "Harmony Vision"), { switchVisualStyle(it) }) },
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
                wallpaper()?.invalidate()
            } },
            Runnable { chooseSetting("Bottom shade", arrayOf("Off", "On")) { which ->
                prefs.edit().putBoolean("bottom_shade", which == 1).apply()
                wallpaper()?.invalidate()
            } },
            Runnable { chooseSetting("Wallpaper focus glow", arrayOf("On", "Off")) { which ->
                prefs.edit().putBoolean("focus_glow", which == 0).apply()
                wallpaper()?.focusAccent = 0
                wallpaper()?.invalidate()
            } }
        )
        subMenu("Wallpaper", labels, hints, icons, choices)
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
                    arrayOf<Runnable>(Runnable(::hiddenApps), Runnable { rearrangeApps() }))
            return
        }
        val labels = arrayOf("Hide / unhide apps", "Rearrange apps", "Continue watching")
        val hints = arrayOf("Choose which apps appear", "Choose the order of app cards", "Resume titles shared by your TV apps")
        val icons = intArrayOf(R.drawable.ic_apps, R.drawable.ic_swap_vert, R.drawable.ic_livetv)
        val choices = arrayOf<Runnable>(
            Runnable(::hiddenApps),
            Runnable { rearrangeApps() },
            Runnable { chooseSetting("Continue watching", arrayOf("Show below apps", "Hide row", "Allow TV Watch Next access")) { which ->
                if (which == 2) requestWatchNextAccess()
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
        val labels = arrayOf("Screensaver & weather", "Profile name & photo", "Performance", "Updates", "Export settings", "Import settings", "Reset layout & unhide apps", host.getString(R.string.compose_apps_menu))
        val hints = arrayOf("Photos, weather & idle timer", "Name and local profile picture", "Light, Balanced or Full", "Automatic GitHub updates", "Save to a file", "Restore from a file", "Restore the default layout", host.getString(R.string.compose_apps_hint))
        val icons = intArrayOf(R.drawable.ic_picture, R.drawable.ic_person, R.drawable.ic_settings, R.drawable.ic_internet, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_apps)
        val choices = arrayOf<Runnable>(
            Runnable(::screensaverSettings),
            Runnable { editProfile() },
            Runnable { chooseSetting("Performance", arrayOf("Auto", "Light", "Balanced", "Full")) { which ->
                prefs.edit().putInt("perf_mode", which).apply()
                applyPerformanceProfile()
                buildHomeSoon()
            } },
            Runnable { LauncherUpdatesUi.show(host) },
            Runnable { settingsTransfer.exportSettings() },
            Runnable { settingsTransfer.importSettings() },
            Runnable { ThemeDialogBuilder(host).setTitle("Reset layout and show all apps?").setPositiveButton("Reset") { _, _ ->
                prefs.edit().remove("app_order").remove("hidden_apps").remove("tv_columns").remove("phone_columns").remove("large_tiles")
                        .remove("focus_title_size").remove("clock_size").remove("background_dim").remove("theme_mode")
                        .remove("wallpaper_mode").remove("focus_glow").remove("continue_watching")
                        .remove("header_bg_mode").remove("header_bg").remove("header_blur").apply()
                buildHomeSoon()
            }.setNegativeButton(android.R.string.cancel, null).show() },
            Runnable { host.startActivity(Intent(host, ComposeAppsActivity::class.java)) }
        )
        subMenu("System", labels, hints, icons, choices)
    }

    private fun wallpaperSettings() {
        // Built-in Midnight mountains / Solid midnight removed 2026-11-01 (user).
        chooseSetting("Home wallpaper", arrayOf("Choose picture", "Saved custom picture")) { which ->
            if (which == 0) {
                try { host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 8) }
                catch (unavailable: ActivityNotFoundException) { Toast.makeText(host, "No image picker available on host device", Toast.LENGTH_LONG).show() }
                catch (unavailable: SecurityException) { Toast.makeText(host, "No image picker available on host device", Toast.LENGTH_LONG).show() }
            } else {
                if (!profileAsset("personal-wallpaper.jpg").exists()) { Toast.makeText(host, "Choose a picture first", Toast.LENGTH_SHORT).show(); return@chooseSetting }
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
        val dialog = GlassSettings.panel(host, "Show / hide apps", rows)
        for (app in apps) {
            val pkg = app.info.activityInfo.packageName
            val row = GlassSettings.row(host, app.name, if (hidden.contains(pkg)) "Hidden · select to show" else "Visible · select to hide",
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
        GlassSettings.options(host, "Weather", arrayOf("Home weather widget", "Widget animation", "Weather city", "Temperature unit", "Hourly / daily forecast"),
            arrayOf("Saved separately for host theme", "Animate only while Home is visible", "Shared weather location", "Celsius or Fahrenheit", "Open weather details"),
            intArrayOf(R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness), { which ->
                if (which == 0) chooseSetting("Home weather widget", arrayOf("On", "Off")) { value ->
                    val edit = prefs.edit().putBoolean("weather_widget", value == 0)
                    if (value == 0) edit.putBoolean("apps_only", false)
                    edit.apply()
                    buildHomeSoon()
                }
                else if (which == 1) chooseSetting("Widget animation", arrayOf("On", "Off")) { value ->
                    prefs.edit().putBoolean("weather_widget_animation", value == 0).apply()
                    activateWeatherWidget()
                }
                else if (which == 2) WeatherSettings.show(host)
                else if (which == 3) chooseSetting("Temperature unit", arrayOf("Celsius", "Fahrenheit")) { value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply() }
                else showForecast()
            }, { which -> if (which == 0) !(harmony() && tv) else which != 1 || weatherWidgetEnabled() })
    }
    private fun screensaverSettings() {
        chooseSetting("Online landscape screensaver", arrayOf("Preview", "Weather city", "Temperature unit", "Start after idle", "Set automatic screensaver")) { which ->
            if (which == 0) launch(Intent(host, LandscapeActivity::class.java))
            else if (which == 1) WeatherSettings.show(host)
            else if (which == 2) chooseSetting("Temperature unit", arrayOf("Celsius", "Fahrenheit")) { value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply() }
            else if (which == 3) chooseSetting("Start screensaver after idle", arrayOf("Off", "2 minutes", "5 minutes", "10 minutes", "15 minutes")) { value ->
                prefs.edit().putInt("idle_screensaver_minutes", intArrayOf(0, 2, 5, 10, 15)[value]).apply()
                armIdleScreensaver()
            }
            else launch(Intent(host, ScreensaverSetupActivity::class.java))
        }
    }
}
