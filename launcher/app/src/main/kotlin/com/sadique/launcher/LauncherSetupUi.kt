package com.sadique.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

/** Shows the existing Home and remote setup actions. */
object LauncherSetupUi {
    @JvmStatic
    fun show(host: Activity) {
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val status = GlassSettings.label(host, "", 14, 0xffe5f2ff.toInt())
        rows.addView(status)
        val help = GlassSettings.label(host,
            "With saved local pairing, automatic setup keeps Personal Launcher as Home and restores its Home/Settings helpers after boot or an app update. On a new TV, approve Android permissions and pairing once.",
            12, 0xffb7cbdf.toInt())
        help.setPadding(0, 12, 0, 12)
        rows.addView(help)
        fun row(title: String, hint: String, action: Runnable) {
            rows.addView(GlassSettings.row(host, title, hint, R.drawable.ic_settings, GlassSettings.themeAccent(host), action))
        }
        row("Enable automatic setup / Repair now", "Use this TV's saved local ADB authorization") { HomeAutomation.enable(host) }
        row("Stop automatic Home repair", "Stop future Home changes; current default stays selected") { HomeAutomation.disable(host) }
        row("Android Home picker", "Choose through Android when this TV supports it") {
            HomeAutomation.disable(host)
            host.startActivity(Intent(host, HomeSettingsActivity::class.java))
        }
        row("Home / Settings button permissions", "Enable this launcher's accessibility helpers") {
            open(host, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        row("App permissions / restricted settings", "Android may require Allow restricted settings for this app") {
            open(host, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", host.packageName, null)))
        }
        row("Local pairing and volume auto-start", "Pair once or inspect automatic volume recovery") { VolumeAutoUi.show(host) }
        val dialog = GlassSettings.panel(host, "Home & remote setup", rows)
        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                if (!dialog.isShowing || !rows.isAttachedToWindow) return
                status.text = setupStatus(host)
                handler.postDelayed(this, 1000)
            }
        }
        rows.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.removeCallbacks(refresh)
                handler.post(refresh)
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(refresh)
            }
        })
        dialog.show()
        handler.post(refresh)
    }

    private fun setupStatus(host: Activity): String = buildString {
        val granted = host.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        fun enabled(value: Boolean) = if (value) "On" else "Off"
        appendLine("Default Home: " + if (HomeAutomation.isDefault(host)) "Personal Launcher" else HomeAutomation.currentHome(host))
        appendLine("Automatic repair: " + enabled(HomeAutomation.enabled(host)))
        appendLine("Saved pairing: " + if (VolumeAutoStart.paired(host)) "Ready" else "Required")
        appendLine("Settings permission: " + if (granted) "Granted" else "Required")
        append("Home permission: " + enabled(HomeAutomation.helperEnabled(host, HomeKeyService::class.java)))
        appendLine(" · Settings permission: " + enabled(HomeAutomation.helperEnabled(host, InputKeyService::class.java)))
        appendLine("Home listener: " + if (HomeKeyService.isConnected()) "Connected" else "Waiting")
        appendLine("Home result: " + HomeAutomation.status(host))
        append("Volume: " + VolumeAutoStart.status(host))
    }

    private fun open(host: Activity, intent: Intent) {
        try {
            host.startActivity(intent)
        } catch (_: RuntimeException) {
            Toast.makeText(host, "Open this option from TV settings", Toast.LENGTH_LONG).show()
        }
    }
}
