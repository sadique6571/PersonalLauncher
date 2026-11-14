package com.sadique.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.service.notification.NotificationListenerService

/** Restores the Home listener when Android rebinds this approved service. */
open class GuardRevival : NotificationListenerService() {
    private var unlocked: BroadcastReceiver? = null

    override fun onListenerConnected() {
        val users = getSystemService(UserManager::class.java)
        if (users != null && !users.isUserUnlocked) {
            if (unlocked != null) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    stopWaiting()
                    GuardKeep.ensureHomeKey(this@GuardRevival)
                }
            }
            unlocked = receiver
            registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            if (!users.isUserUnlocked) return
            stopWaiting()
        }
        GuardKeep.ensureHomeKey(this)
    }

    private fun stopWaiting() {
        val receiver = unlocked ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: RuntimeException) {
            // May already have been released by Android.
        }
        unlocked = null
    }

    override fun onDestroy() {
        stopWaiting()
        super.onDestroy()
    }
}
