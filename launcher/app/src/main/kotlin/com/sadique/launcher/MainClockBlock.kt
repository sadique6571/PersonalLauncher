package com.sadique.launcher

import android.app.Activity
import android.os.Handler
import android.view.View
import android.widget.TextView

/**
 * The header's clock block: the clock, the greeting, and the Shamsi and lunar dates under it.
 * Everything it needs is fixed when Home is built, so it takes plain values, not callbacks.
 */
class MainClockBlock(
    private val host: Activity,
    private val handler: Handler,
    private val clockBlock: View,
    private val greeting: TextView,
    private val shamsiDate: TextView,
    private val lunarDate: TextView,
    /** greeting_name: said after the greeting, which is shown in capitals. */
    private val greetingName: String,
    private val greetingColor: Int,
    private val festive: Boolean,
    private val festival: Festival,
    private val shamsi: Boolean,
    private val lunar: Boolean,
    private val lunarOffset: Int,
) {
    companion object {
        /**
         * 2025-01-01. TVs without a clock battery boot at the epoch (08:00 in China) and keep it until network
         * time arrives; the clock, date and greeting stay hidden before then.
         */
        private const val TIME_VALID_AFTER_MS = 1_735_689_600_000L
    }

    /** Some TVs (Coocaa OS) set the network time without ACTION_TIME_CHANGED, so keep checking until it is valid. */
    private val clockCheck = Runnable { updateClockVisibility() }

    fun updateClockVisibility() {
        val valid = System.currentTimeMillis() >= TIME_VALID_AFTER_MS
        clockBlock.visibility = if (valid) View.VISIBLE else View.INVISIBLE
        handler.removeCallbacks(clockCheck)
        if (valid) {
            updateGreeting()
        } else {
            handler.postDelayed(clockCheck, 1_000L)
        }
    }

    fun updateGreeting() {
        LauncherGreeting.update(host, greeting, greetingName, greetingColor, festive, festival)
        if (shamsi) shamsiDate.text = Shamsi.today(host.resources)
        if (lunar) lunarDate.text = Lunar.today(host.resources, lunarOffset)
    }

    /** Cancels the pending re-check (onStop). */
    fun cancelPendingCheck() {
        handler.removeCallbacks(clockCheck)
    }
}
