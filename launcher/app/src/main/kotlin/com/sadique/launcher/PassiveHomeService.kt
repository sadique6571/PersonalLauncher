package com.sadique.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.HashSet

/** Alternative to the key helpers: observes stock Home windows and never requests key filtering. */
class PassiveHomeService : AccessibilityService() {
    private val homeActivities = HashSet<String>()
    private var stockPackage = ""
    private var lastRedirect: Long = 0
    private var unlock: BroadcastReceiver? = null

    override fun onServiceConnected() {
        val userManager = getSystemService(UserManager::class.java)
        if (userManager?.isUserUnlocked == true) {
            configure()
        } else {
            unlock = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    stopWaiting()
                    configure()
                }
            }
            registerReceiver(unlock, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            if (userManager?.isUserUnlocked == true) {
                stopWaiting()
                configure()
            }
        }
    }

    private fun configure() {
        val config = Config.load(this)
        stockPackage = config.stockHomeRedirect
        homeActivities.clear()
        if (stockPackage.isEmpty()) return
        val stock = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(stockPackage)
        for (entry in packageManager.queryIntentActivities(stock, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (entry.activityInfo != null) {
                homeActivities.add(entry.activityInfo.name)
            }
        }
        Log.d("PersonalVolume", "Passive Home connected; no key-filter capability, stock activities=" + homeActivities.size)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            !matchesHome(stockPackage, homeActivities, event.packageName, event.className)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 1500) return
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            lastRedirect = now
        } catch (_: RuntimeException) {
            Log.w("PersonalVolume", "Passive Home redirect failed")
        }
    }

    private fun stopWaiting() {
        val receiver = unlock
        if (receiver != null) {
            unregisterReceiver(receiver)
            unlock = null
        }
    }

    override fun onDestroy() {
        stopWaiting()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        @JvmStatic
        fun matchesHome(stockPackage: String, activities: Set<String>, pkg: CharSequence?, cls: CharSequence?): Boolean {
            return stockPackage.isNotEmpty() && pkg != null && cls != null &&
                    stockPackage.contentEquals(pkg) && activities.contains(cls.toString())
        }

        @JvmStatic
        fun enabled(context: Context): Boolean {
            val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val mine = ComponentName(context, PassiveHomeService::class.java)
            if (services != null) {
                for (service in services.split(":")) {
                    if (mine == ComponentName.unflattenFromString(service)) return true
                }
            }
            return false
        }
    }
}
