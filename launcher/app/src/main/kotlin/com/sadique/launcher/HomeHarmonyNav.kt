package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout

/** Harmony hero focus navigation and shortcuts row. Pure move from PersonalHomeActivity.
 *  The hero itself (HarmonyHomeHero build + field assignment) stays in the Activity. */
internal class HomeHarmonyNav(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val tv: Boolean,
    private val dp: (Int) -> Int,
    private val launch: (Intent) -> Unit,
    private val showAppLibrary: () -> Unit,
    private val grid: () -> HomeAppGrid<PersonalApp>?,
    private val focusedPkg: () -> String?,
    private val searchButton: () -> ImageButton?,
    private val weatherWidget: () -> WeatherWidgetView?,
    private val harmonyClock: () -> HarmonyVisionClockView?,
    private val page: () -> LinearLayout?,
) {
    /** Lightweight English service shortcuts with D-pad return to the app dock. */
    fun harmonyShortcuts(): View {
        val links = LinearLayout(activity)
        links.orientation = LinearLayout.VERTICAL
        val apps = GlassSettings.wideCard(activity, "All apps", R.drawable.ic_apps,
                ThemeStyle.focusAccent(prefs)) { showAppLibrary() }
        val scenery = GlassSettings.wideCard(activity, "Scenery", R.drawable.ic_picture,
                ThemeStyle.focusAccent(prefs)) { launch(Intent(activity, LandscapeActivity::class.java)) }
        for (entry in arrayOf(apps, scenery)) {
            entry.setOnKeyListener { _, key, event ->
                if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN && grid() != null) {
                    grid()?.focusKey(focusedPkg())
                    true
                } else {
                    false
                }
            }
            val params = LinearLayout.LayoutParams(-1, dp(64))
            params.setMargins(0, dp(3), 0, dp(3))
            links.addView(entry, params)
        }
        return links
    }

    fun harmonyHeroKey(key: Int, event: KeyEvent): Boolean {
        val horizontal = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
        if (!horizontal && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return true
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            searchButton()?.requestFocus()
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusHarmonyBelowHero()
        } else {
            val rtl = page()?.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val target: View? = if ((key == KeyEvent.KEYCODE_DPAD_RIGHT) != rtl) weatherWidget() else harmonyClock()
            if (target != null && target.isShown) target.requestFocus()
        }
        return true
    }

    fun focusHarmonyHero(): Boolean {
        val clock = harmonyClock()
        if (clock != null && clock.isShown && clock.requestFocus()) return true
        val widget = weatherWidget()
        return widget != null && widget.isShown && widget.requestFocus()
    }

    private fun focusHarmonyBelowHero(): Boolean {
        val g = grid()
        if (g == null || g.appCount() == 0) return false
        g.focusKey(focusedPkg())
        return true
    }

    fun fixedHarmonyDock(): Boolean {
        return ThemeStyle.harmony(prefs) && tv && !prefs.getBoolean("apps_only", false)
    }

    fun focusBelowToolbar() {
        val g = grid()
        if (g != null && (!g.isHorizontalDock() || !focusHarmonyHero())) g.focusKey(focusedPkg())
    }
}
