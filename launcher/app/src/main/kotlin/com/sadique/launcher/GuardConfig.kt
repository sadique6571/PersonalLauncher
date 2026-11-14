package com.sadique.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opt in to leaving the home helper while the screen is off.
 * `adb shell am broadcast -a com.sadique.launcher.GUARD -n com.sadique.launcher/.GuardConfig --ez guard_off_in_standby true`
 */
open class GuardConfig : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("guard_off_in_standby")) {
            GuardKeep.setOffInStandby(context, intent.getBooleanExtra("guard_off_in_standby", false))
            GuardKeep.ensureHomeKey(context)
        }
        resultData = "guard_off_in_standby=" + GuardKeep.offInStandby(context)
    }
}
