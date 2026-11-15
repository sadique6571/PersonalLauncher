package com.sadique.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.widget.Toast
import java.util.concurrent.Executor

internal class HomeSettingsTransfer(
    private val host: Activity,
    private val prefs: SharedPreferences,
    private val worker: Executor,
    private val handler: Handler,
    private val onImported: () -> Unit,
) {
    fun exportSettings() {
        try {
            val json = org.json.JSONObject()
            json.put("version", 1)
            json.put("app", "com.sadique.launcher")
            // Export safe prefs only (no secrets, no cache).
            val all = prefs.all
            val data = org.json.JSONObject()
            for ((k, v) in all) {
                // Skip secrets, caches, and transient state.
                if (k == ThemePreferences.MIGRATED || k == NotificationAccessSetup.ASKED || k.contains("key") || k.contains("secret") || k.contains("token")
                        || k.contains("password") || k.startsWith("weather_cached")) continue
                if (v is String || v is Int || v is Boolean || v is Long || v is Float) {
                    data.put(k, v)
                } else if (v is Set<*>) {
                    val arr = org.json.JSONArray()
                    for (item in v) arr.put(item.toString())
                    data.put(k, arr)
                }
            }
            json.put("settings", data)
            val content = json.toString(2)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "launcher-settings.json")
            // Store content for onActivityResult.
            pendingExport = content
            host.startActivityForResult(intent, 10)
        } catch (e: Exception) {
            Toast.makeText(host, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingExport: String? = null

    /** Import settings from JSON via document picker. Validates format version. */
    fun importSettings() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
            host.startActivityForResult(intent, 11)
        } catch (e: Exception) {
            Toast.makeText(host, "Import failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun onActivityResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request == 10 && result == Activity.RESULT_OK && data != null && data.data != null) {
            // Export: write JSON to the picked document.
            val uri = data.data!!
            val content = pendingExport
            pendingExport = null
            if (content != null) {
                worker.execute {
                    try {
                        val out = host.contentResolver.openOutputStream(uri)
                        out!!.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                            handler.post { Toast.makeText(host, "Settings exported", Toast.LENGTH_SHORT).show() }
                        }
                    } catch (e: Exception) {
                        handler.post { Toast.makeText(host, "Export failed", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            return true
        }
        if (request == 11 && result == Activity.RESULT_OK && data != null && data.data != null) {
            // Import: read and validate JSON, apply settings.
            val uri = data.data!!
            worker.execute {
                try {
                    val input = host.contentResolver.openInputStream(uri)!!
                    input.use {
                        val scanner = java.util.Scanner(it).useDelimiter("\\A")
                        val content = if (scanner.hasNext()) scanner.next() else ""
                        val json = org.json.JSONObject(content)
                        val version = json.optInt("version", 0)
                        if (version != 1) throw Exception("Unsupported version: $version")
                        if ("com.sadique.launcher" != json.optString("app")) {
                            throw Exception("Not a Personal Launcher export")
                        }
                        val settings = json.getJSONObject("settings")
                        val ed = prefs.edit()
                        val keys = settings.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k == ThemePreferences.MIGRATED || k == NotificationAccessSetup.ASKED) continue
                            val v = settings.get(k)
                            if (v is String) ed.putString(k, v)
                            else if (v is Int) ed.putInt(k, v)
                            else if (v is Boolean) ed.putBoolean(k, v)
                            else if (v is Long) ed.putLong(k, v)
                            else if (v is org.json.JSONArray) {
                                val set = HashSet<String>()
                                val arr = v
                                for (i in 0 until arr.length()) set.add(arr.getString(i))
                                ed.putStringSet(k, set)
                            }
                        }
                        ed.apply()
                        handler.post {
                            Toast.makeText(host, "Settings imported — restarting Home", Toast.LENGTH_SHORT).show()
                            onImported()
                        }
                    }
                } catch (e: Exception) {
                    handler.post { Toast.makeText(host, "Import failed: " + e.message, Toast.LENGTH_LONG).show() }
                }
            }
            return true
        }
        return false
    }
}
