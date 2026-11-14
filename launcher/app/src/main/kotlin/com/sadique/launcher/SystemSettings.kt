package com.sadique.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/** The standard settings intent, or vendor fallback. */
object SystemSettings {
    private val AOSP_TV_SETTINGS = ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")

    @JvmStatic
    fun intent(context: Context, action: String): Intent {
        val pm = context.packageManager
        val standard = Intent(action)
        val resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard
        val vendor = vendorIntent(context)
        if (vendor != null) return vendor
        val aosp = Intent().setComponent(AOSP_TV_SETTINGS)
        return try {
            pm.getPackageInfo(AOSP_TV_SETTINGS.packageName, 0)
            aosp
        } catch (_: PackageManager.NameNotFoundException) {
            standard
        }
    }

    @JvmStatic
    fun openBluetoothAccessories(context: Context) {
        val slice = Intent("android.settings.SLICE_SETTINGS")
            .setClassName("com.android.tv.settings", "com.android.tv.settings.slice.SliceActivity")
            .putExtra("slice_uri", "content://com.android.tv.settings.accessories.sliceprovider/general")
        if (open(context, slice)) return
        if (!open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun open(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (unavailable: Exception) {
            if (unavailable is ActivityNotFoundException || unavailable is SecurityException) {
                Log.d("PersonalSettings", "Settings route unavailable: " + intent.action, unavailable)
                false
            } else throw unavailable
        }
    }

    @JvmStatic
    fun vendorIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (value in context.resources.getStringArray(R.array.settings_fallback_components)) {
            val component = ComponentName.unflattenFromString(value) ?: continue
            try {
                pm.getPackageInfo(component.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
            return Intent().setComponent(component)
        }
        return null
    }

    @JvmStatic
    fun appInfo(context: Context, pkg: String): Intent? {
        val info = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        return if (context.packageManager.resolveActivity(info, PackageManager.MATCH_DEFAULT_ONLY) != null) info else null
    }
}
