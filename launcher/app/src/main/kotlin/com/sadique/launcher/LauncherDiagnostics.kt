package com.sadique.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.Executors

/** Reads local launcher status once without running commands. */
object LauncherDiagnostics {
    @JvmStatic
    fun show(host: Activity) {
        val rows = HomeLibraryUi.rows(host)
        val text = GlassSettings.label(host, host.getString(R.string.h_di_loading), 14, 0xffeeeeee.toInt())
        text.setTextIsSelectable(true)
        rows.addView(text)
        rows.addView(GlassSettings.label(host, host.getString(R.string.h_di_note), 12, 0xffb5cadd.toInt()))
        val panel = GlassSettings.panel(host, host.getString(R.string.h_diagnostics), rows)
        panel.show()
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            val status = buildString {
                try {
                    val home = host.packageManager.resolveActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
                    )?.activityInfo
                    append("Android HOME: ").append(home?.let {
                        ComponentName(it.packageName, it.name).flattenToShortString()
                    } ?: "unresolved")
                    val config = Config.load(host)
                    append("\nStock Home redirect: ").append(config.stockHomeRedirect.ifEmpty { "off" })
                    append("\nHome service connected: ").append(HomeKeyService.isConnected())
                    val services = Settings.Secure.getString(host.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    for (service in arrayOf(HomeKeyService::class.java, InputKeyService::class.java)) {
                        val component = ComponentName(host, service)
                        val enabled = services?.split(':')?.any { component == ComponentName.unflattenFromString(it) } == true
                        append("\n").append(service.simpleName).append(" enabled: ").append(enabled)
                    }
                    append("\nKey filter configured: ").append(config.keyFilter)
                    append("\nPower-on Home: ").append(config.powerOnHomeAlways)
                    append("\nAndroid uptime: ").append(SystemClock.elapsedRealtime() / 1000).append("s")
                    append("\n\nLauncherBoot (this process):\n")
                    append(BootLog.snapshot().ifEmpty { "No timing events retained" })
                } catch (_: RuntimeException) {
                    append("\nSome status is unavailable on this firmware.")
                }
            }
            host.runOnUiThread {
                if (!host.isDestroyed && panel.isShowing) text.text = status
            }
        }
        worker.shutdown()
    }
}
