package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.view.View
import android.widget.LinearLayout

/** TV-style quick settings followed by launcher preferences and secondary tools. */
object HomeDashboard {
    const val SETTINGS = 0
    const val SEARCH = 1
    const val STORE = 2
    const val SCREENSAVER = 3
    const val CATEGORIES = 5
    const val WIFI = 7
    const val BLUETOOTH = 8
    const val INPUTS = 9
    const val SYSTEM = 10
    const val DISPLAY = 11
    const val SOUND = 12
    const val IDENTITY = 13

    fun interface Actions {
        fun open(action: Int)
    }

    @JvmStatic
    fun show(host: Activity, network: String, rebuild: Runnable, actions: Actions): AlertDialog {
        return show(host, network, rebuild, actions, false) { false }
    }

    fun show(
        host: Activity,
        network: String,
        rebuild: Runnable,
        actions: Actions,
        floating: Boolean,
        keepPanel: (Int) -> Boolean
    ): AlertDialog {
        val rows = HomeLibraryUi.rows(host)
        val prefs = ThemePreferences.get(host)
        val title = if (TvGlassStyle.enabled(prefs)) "Control Center" else host.getString(R.string.h_dashboard)
        val panel = GlassSettings.panel(host, title, rows, true)
        val systemGear = if (floating) {
            GlassSettings.row(host, host.getString(R.string.tv_settings), null,
                R.drawable.ic_settings, GlassSettings.themeAccent(host)) {
                panel.dismiss()
                actions.open(SYSTEM)
            }
        } else null
        val harmony = ThemeStyle.harmony(prefs)

        if (harmony) {
            rows.id = R.id.controlCenterPanel
            rows.addView(GlassSettings.label(host, "HARMONY CONTROL", 18, 0xfff5fbff.toInt()).apply {
                letterSpacing = .08f
                setPadding(GlassSettings.dp(host, 6f), GlassSettings.dp(host, 8f), 0, GlassSettings.dp(host, 4f))
            })
        } else if (ThemeStyle.id(prefs) == ThemeStyle.GLASS) {
            rows.id = R.id.controlCenterPanel
            rows.addView(GlassSettings.label(host, "${prefs.getString("name", "Profile")} • Android TV",
                12, 0xffa5b4cf.toInt()))
            if (!floating) {
                rows.addView(GlassSettings.row(host, "Close", null, android.R.drawable.ic_menu_close_clear_cancel,
                    GlassSettings.themeAccent(host), panel::dismiss))
            }
        }
        if (systemGear != null) rows.addView(systemGear)
        val networkColor = if (harmony) 0xffa9ccdf.toInt() else 0xffb5cadd.toInt()
        rows.addView(GlassSettings.label(host, network, 12, networkColor))
        val openTool: (Int) -> Unit = { action ->
            if (!keepPanel(action)) panel.dismiss()
            actions.open(action)
        }
        section(host, rows, R.string.h_quick_settings)
        rows.addView(ControlCenterGrid(host) { openTool(it) })
        section(host, rows, R.string.h_personalize)
        row(host, rows, R.string.h_launcher_settings, R.string.h_layout_hint, R.drawable.ic_palette) {
            if (harmony) panel.dismiss()
            actions.open(SETTINGS)
        }
        if (!floating) {
            row(host, rows, R.string.h_single_identity, 0, R.drawable.ic_person) {
                panel.dismiss()
                actions.open(IDENTITY)
            }
            if (!harmony) {
                val appsOnly = prefs.getBoolean("apps_only", false)
                row(host, rows, R.string.h_apps_only, if (appsOnly) R.string.h_on else R.string.h_off,
                    R.drawable.ic_apps) {
                    ThemePreferences.get(host).edit().putBoolean("apps_only", !appsOnly).apply()
                    panel.dismiss()
                    rebuild.run()
                }
            }
        }
        val pin = {
            row(host, rows, R.string.h_local_pin, R.string.h_local_pin_hint, R.drawable.ic_person) {
                if (!floating) panel.dismiss()
                LauncherPin.settings(host)
            }
        }
        val sleep = {
            row(host, rows, R.string.h_sleep_timer, R.string.h_sleep_timer_hint, R.drawable.ic_media_pause) {
                if (!floating) panel.dismiss()
                LauncherSleep.show(host, floating)
            }
        }
        if (harmony) {
            sleep()
            pin()
        } else {
            pin()
            sleep()
        }
        if (!floating || !harmony) {
            section(host, rows, R.string.h_more_tools)
            if (!floating) {
                val strip = LinearLayout(host)
                card(host, strip, R.string.h_search, R.drawable.ic_search) { openTool(SEARCH) }
                card(host, strip, R.string.h_store, R.drawable.ic_apps) { openTool(STORE) }
                rows.addView(strip)
            } else {
                row(host, rows, R.string.h_store, 0, R.drawable.ic_apps) {
                    panel.dismiss()
                    actions.open(STORE)
                }
            }
        }
        addVersion(host, rows)
        panel.show()
        systemGear?.requestFocus()
        return panel
    }

    @Suppress("DEPRECATION")
    private fun addVersion(host: Activity, rows: LinearLayout) {
        val version = try {
            host.packageManager.getPackageInfo(host.packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: host.getString(R.string.not_available)
        val build = host.getString(R.string.launcher_build_number)
        val label = GlassSettings.label(host, host.getString(R.string.h_installed_version, version, build), 12,
            ThemeStyle.muted(ThemePreferences.get(host)))
        label.setPadding(GlassSettings.dp(host, 6f), GlassSettings.dp(host, 16f), 0, GlassSettings.dp(host, 8f))
        label.isFocusable = true
        rows.addView(label)
    }

    private fun card(host: Activity, strip: LinearLayout, title: Int, icon: Int, action: Runnable) {
        val card = GlassSettings.wideCard(host, host.getString(title), icon, GlassSettings.themeAccent(host), action)
        card.minimumHeight = GlassSettings.dp(host, 64f)
        val params = LinearLayout.LayoutParams(0, -2, 1f)
        val margin = GlassSettings.dp(host, 3f)
        params.setMargins(margin, margin, margin, margin)
        strip.addView(card, params)
    }

    private fun section(host: Activity, rows: LinearLayout, title: Int) {
        val label = GlassSettings.label(host, host.getString(title), 12, GlassSettings.themeAccent(host))
        label.setPadding(4, GlassSettings.dp(host, 14f), 0, GlassSettings.dp(host, 6f))
        rows.addView(label)
    }

    private fun row(host: Activity, rows: LinearLayout, title: Int, hint: Int, icon: Int, action: Runnable) {
        val detail = if (hint == 0) null else host.getString(hint)
        rows.addView(GlassSettings.row(host, host.getString(title), detail, icon, GlassSettings.themeAccent(host), action))
    }
}
