package com.sadique.launcher

import android.Manifest
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Remembers opted-in accessibility helpers and restores them after a process restart. */
object ServiceRepair {
    private fun prefs(context: Context) = context.getSharedPreferences("accessibility_helpers", Context.MODE_PRIVATE)

    @JvmStatic
    fun connected(context: Context, service: Class<*>) {
        prefs(context).edit().putBoolean(service.name, true).apply()
    }

    @JvmStatic
    fun disconnected(context: Context, service: Class<*>) {
        prefs(context).edit().remove(service.name).apply()
    }

    @JvmStatic
    fun restore(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val resolver = context.contentResolver
            val before = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val enabled = before?.split(':')?.mapNotNull(ComponentName::unflattenFromString).orEmpty()
            val recorded = prefs(context).all.keys.map { ComponentName(context.packageName, it) }
            if (recorded.all { it in enabled }) return
            val others = enabled.filterNot { it in recorded }
            val without = others.joinToString(":") { it.flattenToShortString() }
            val with = (others + recorded).joinToString(":") { it.flattenToShortString() }
            // Remove then re-add crashed helpers. Stop if settings changed during either pause.
            Thread({
                try {
                    if (!replaceList(resolver, before, with)) return@Thread
                    Thread.sleep(1000)
                    if (!replaceList(resolver, with, without)) return@Thread
                    Thread.sleep(1000)
                    if (!replaceList(resolver, without, with)) return@Thread
                    Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                } catch (_: RuntimeException) {
                    // The system may reject writes.
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }, "ServiceRepair").start()
        } catch (_: RuntimeException) {
            // The system may reject reads.
        }
    }

    private fun replaceList(resolver: ContentResolver, expected: String?, updated: String): Boolean {
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        return Settings.Secure.getString(resolver, key).orEmpty() == expected.orEmpty() &&
            Settings.Secure.putString(resolver, key, updated)
    }
}
