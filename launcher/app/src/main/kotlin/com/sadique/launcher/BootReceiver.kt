package com.sadique.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * BOOT_COMPLETED pre-warm: compose every app's icon card into the disk cache while the system
 * finishes booting, so the home screen opens with icons ready instead of composing them cold.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        VolumeAutoStart.request(context)
        if (action != Intent.ACTION_BOOT_COMPLETED) return

        val result = goAsync()
        // Use a scheduled executor so we can enforce a time bound.
        // Respect BroadcastReceiver time limits: goAsync() gives us ~10s.
        val exec = Executors.newSingleThreadScheduledExecutor()
        exec.execute {
            try {
                IconCache.warm(context)
            } finally {
                try {
                    result.finish()
                } finally {
                    exec.shutdown() // Don't leak the thread.
                }
            }
        }
        // Safety: force shutdown after 8s to respect receiver time limits.
        exec.schedule({
            if (!exec.isShutdown) exec.shutdownNow()
        }, 8, TimeUnit.SECONDS)
    }
}
