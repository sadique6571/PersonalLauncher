package com.sadique.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings

/** Restores the opted-in Home helper after the screen leaves standby. */
object GuardKeep {
    const val ACTION = "com.sadique.launcher.GUARD"
    private const val PREFS = "guard"
    private const val KEY_OFF = "off_in_standby"
    private var screenOn: BroadcastReceiver? = null

    @JvmStatic
    fun offInStandby(context: Context): Boolean {
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked == false) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false)
    }

    @JvmStatic
    fun setOffInStandby(context: Context, off: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OFF, off).commit()
    }

    @JvmStatic
    fun ensureHomeKey(context: Context) {
        HomeKeyService.applyStandbyOptIn()
        if (!offInStandby(context) || context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            != PackageManager.PERMISSION_GRANTED) return
        if (context.getSystemService(PowerManager::class.java)?.isInteractive == false) {
            watchScreenOn(context.applicationContext)
            return
        }
        try {
            val mine = ComponentName(context, HomeKeyService::class.java)
            val resolver = context.contentResolver
            val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val list = Settings.Secure.getString(resolver, key)
            val entries = if (list == null || list == "null") emptyList()
                else list.split(':').filter { it.isNotEmpty() }
            if (entries.none { mine == ComponentName.unflattenFromString(it) }) {
                Settings.Secure.putString(resolver, key, (entries + mine.flattenToShortString()).joinToString(":"))
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: RuntimeException) {
            // System settings may reject the update.
        }
    }

    @JvmStatic
    fun stepOut(service: AccessibilityService) {
        watchScreenOn(service.applicationContext)
        service.disableSelf()
    }

    private fun watchScreenOn(app: Context) {
        if (screenOn != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (screenOn !== this) return
                screenOn = null
                try {
                    app.unregisterReceiver(this)
                } catch (_: RuntimeException) {
                    // The receiver may already be released.
                }
                ensureHomeKey(app)
            }
        }
        screenOn = receiver
        app.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}
