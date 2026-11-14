package com.sadique.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/** Manages the existing opt-in Home repair preferences. */
object HomeAutomation {
    private val policy = HomeRepairPolicy()

    @Synchronized
    private fun prefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences("home_automation", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("initialized", false)) {
            val migrate = VolumeAutoStart.paired(context) && VolumeAutoStart.enabled(context)
            prefs.edit().putBoolean("initialized", true).putBoolean("enabled", migrate).apply()
        }
        return prefs
    }

    @JvmStatic fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    @JvmStatic
    fun enable(context: Context) {
        prefs(context).edit().putBoolean("enabled", true).apply()
        state(context, if (VolumeAutoStart.paired(context)) "Home setup queued" else "One-time local pairing is required")
        VolumeAutoStart.requestHome(context)
    }

    @JvmStatic
    fun disable(context: Context) {
        prefs(context).edit().putBoolean("enabled", false).apply()
        state(context, "Automatic Home repair off")
    }

    @JvmStatic
    fun status(context: Context): String? {
        return prefs(context).getString("status",
            if (enabled(context)) "Waiting for automatic setup" else "Automatic Home repair off")
    }

    @JvmStatic
    fun state(context: Context, value: String?) {
        prefs(context).edit().putString("status", value).apply()
    }

    @JvmStatic
    fun currentHome(context: Context): String {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName ?: "Unavailable"
    }

    @JvmStatic fun isDefault(context: Context): Boolean = context.packageName == currentHome(context)

    @JvmStatic
    fun helperEnabled(context: Context, helper: Class<*>): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val expected = ComponentName(context, helper)
        return services?.split(':')?.any { expected == ComponentName.unflattenFromString(it) } == true
    }

    @JvmStatic
    fun onResume(context: Context) {
        ScreensaverPrefetch.schedule(context)
        if (enabled(context) && VolumeAutoStart.paired(context) && !VolumeAutoStart.busy() &&
            policy.allow(SystemClock.elapsedRealtime(), isDefault(context))) {
            VolumeAutoStart.requestHome(context)
        }
    }
}
