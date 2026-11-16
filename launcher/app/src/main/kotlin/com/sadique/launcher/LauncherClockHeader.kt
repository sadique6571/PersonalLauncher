package com.sadique.launcher

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

internal class LauncherClockHeader(
    private val host: Activity,
    private val cfg: Config,
    private val density: Float,
) {
    val clockBlock: View = host.findViewById(R.id.leftBlock)
    private val greeting: TextView = host.findViewById(R.id.greeting)
    private val greetingColor = greeting.currentTextColor
    private val shamsiDate: TextView = host.findViewById(R.id.shamsiDate)
    private val lunarDate: TextView = host.findViewById(R.id.lunarDate)
    private val lunarOffset = Lunar.offsetFor(cfg.lunarUtcOffset, Locale.getDefault())

    init {
        if (!cfg.showGreeting) greeting.visibility = View.GONE
        if (cfg.headerWhite) {
            // The clock's own white for the date and the line before it, instead of their dimmed whites.
            host.findViewById<TextView>(R.id.date).setTextColor(Color.WHITE)
            host.findViewById<View>(R.id.clockDivider).setBackgroundColor(Color.WHITE)
        }
        if (host.resources.getBoolean(R.bool.rtl_menu_bar)) {
            // Center Hebrew greetings over the numeric time, independent of a visible AM/PM label. The clock's row
            // is watched too: date_below_clock can move the whole row right under a wider date.
            val clock = host.findViewById<View>(R.id.clock)
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
        if (cfg.lunarDate) lunarDate.visibility = View.VISIBLE
        if (cfg.dateBelowClock) moveDateBelowClock()
        if (cfg.shamsiDate) {
            shamsiDate.visibility = View.VISIBLE
            val date = host.findViewById<TextView>(R.id.date)
            date.setTextSize(11f)
            lunarDate.setTextSize(11f)
            date.setPadding(0, (4 * density).toInt(), 0, 0)
        }
    }

    /**
     * date_below_clock: the date column moves to its own line under the clock, in the vertical LinearLayout that holds
     * the clock's row, and the line between them goes. In Hebrew (rtl_menu_bar) the date and the clock's row sit right.
     */
    private fun moveDateBelowClock() {
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

    fun update(festive: Boolean, festival: Festival) {
        LauncherGreeting.update(host, greeting, cfg.greetingName, greetingColor, festive, festival)
        if (cfg.shamsiDate) shamsiDate.text = Shamsi.today(host.resources)
        if (cfg.lunarDate) lunarDate.text = Lunar.today(host.resources, lunarOffset)
    }
}
