package com.sadique.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

/** Handles the app that Home opens once per boot. */
object OpenOnStart {
    const val KEY = "open_on_start"
    const val BOOT_PREFS = "boot"
    const val BOOT_KEY = "live_tv_boot"

    @JvmStatic
    fun due(boot: Int, handledBoot: Int, pkg: String?, self: String?): Boolean {
        return boot >= 0 && boot != handledBoot && !pkg.isNullOrEmpty() && pkg != self
    }

    @JvmStatic
    fun boot(context: Context): Int {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }

    @JvmStatic
    fun skipThisBoot(context: Context) {
        context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
            .putInt(BOOT_KEY, boot(context)).commit()
    }

    @JvmStatic
    fun opensNow(online: Boolean, now: Long, deadline: Long): Boolean = online || now >= deadline

    @JvmStatic
    fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    @JvmStatic
    fun intent(pm: PackageManager, pkg: String): Intent? {
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }
}
