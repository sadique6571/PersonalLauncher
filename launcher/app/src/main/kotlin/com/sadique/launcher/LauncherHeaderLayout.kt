package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

internal object LauncherHeaderLayout {
    /**
     * date_below_clock: the date column moves to its own line under the clock, in the vertical LinearLayout that holds
     * the clock's row, and the line between them goes. In Hebrew (rtl_menu_bar) the date and the clock's row sit right.
     */
    fun moveDateBelowClock(host: Activity, density: Float) {
        val column = host.findViewById<View>(R.id.date).parent as View
        val clockRow = host.findViewById<View>(R.id.clock).parent as View
        val clockColumn = clockRow.parent as LinearLayout
        (column.parent as ViewGroup).removeView(column)
        host.findViewById<View>(R.id.clockDivider).visibility = View.GONE
        // Its own width, not the column's: in Hebrew the column is only as wide as the clock.
        val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = Math.round(4 * density)
        if (host.resources.getBoolean(R.bool.rtl_menu_bar)) {
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

    /**
     * Puts each top_bar_apps app in the top bar as a round pill with the app's own logo, left of the network pill.
     * The logo keeps its colours: the pill style's tint, which turns the other icons white, is cleared.
     */
    fun addApps(
        host: Activity, cfg: Config, btnWifi: View, btnVpn: View, density: Float,
        topAppButtons: MutableList<View>, open: (Intent) -> Unit,
    ) {
        val bar = host.findViewById<ViewGroup>(R.id.topButtons)
        val pm = host.packageManager
        val size = Math.round(22 * density)
        for (pkg in cfg.topBarApps) {
            var launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg)
            if (launch == null) continue   // not installed, or disabled
            // Use the same configured launch_actions as tiles; retain the normal entry if unavailable.
            launch = LaunchActions.select(pkg, cfg.launchActions, launch) { targetPackage, action ->
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
            val button = host.layoutInflater.inflate(R.layout.top_app_button, bar, false) as TextView
            // pillKeys finds the bar's ends by id: without one of its own, every app button would be an end.
            button.id = View.generateViewId()
            logo.setBounds(0, 0, size, size)
            button.setCompoundDrawablesRelative(logo, null, null, null)
            button.compoundDrawableTintList = null
            button.contentDescription = label
            val target = launch
            button.setOnClickListener { open(Intent(target)) }
            // After the network pill and the VPN pill; the Hebrew bar runs the other way, so before both there.
            bar.addView(button, bar.indexOfChild(if (host.resources.getBoolean(R.bool.rtl_menu_bar)) btnVpn else btnWifi))
            topAppButtons.add(button)
        }
    }
}
