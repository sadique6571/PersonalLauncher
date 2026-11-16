package com.sadique.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler

/**
 * Home's system listeners: the network and VPN callbacks, the TV-input callback, the package and time
 * receivers, and the minute tick. They only report; what Home does about each event arrives as a callback,
 * so this class owns registration and the listeners' own bookkeeping, nothing else.
 */
class MainHomeCallbacks(
    private val context: Context,
    private val handler: Handler,
    private val minuteMs: Long,
    /** The network changed: refresh the network pill (posted, so it never runs on a binder thread). */
    private val onNetworkChanged: () -> Unit,
    /** The network just validated: open the boot's app if one is waiting for it. */
    private val onNetworkValidated: () -> Unit,
    /** wifi_status_warnings is on: validation changes refresh the pill too. */
    private val warnsAboutInternet: () -> Boolean,
    /** An input appeared or vanished: re-check whether the Inputs pill has anything to list. */
    private val onInputChanged: () -> Unit,
    /** A package changed: mark the tiles stale, or rebuild them if Home is in front. */
    private val onPackagesChanged: () -> Unit,
    /** The clock or time zone changed: re-check whether the clock is valid yet. */
    private val onTimeChanged: () -> Unit,
    /** A minute passed: greeting, sports, sky, wallpaper, and the diya's flame. */
    private val onMinuteTick: () -> Unit,
) {
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkChanged()
        }

        override fun onLost(network: Network) {
            onNetworkChanged()
        }

        /** wifi_status_warnings follows validation; signal-strength updates also land here, so skip those. */
        private var lastValidated = false

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            // open_on_start_network_wait: the boot's app opens on the first validated network.
            if (validated) onNetworkValidated()
            if (validated == lastValidated) return
            lastValidated = validated
            if (warnsAboutInternet()) onNetworkChanged()
        }
    }

    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputAdded(inputId: String) {
            onInputChanged()
        }

        override fun onInputRemoved(inputId: String) {
            onInputChanged()
        }
    }

    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkChanged()
        }

        override fun onLost(network: Network) {
            onNetworkChanged()
        }
    }

    private val packageFilter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addDataScheme("package")
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED == intent.action
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
            // In the background this only marks the tiles stale; onResume rebuilds them once.
            onPackagesChanged()
        }
    }

    // Right after boot the clock is often wrong until network time arrives; don't wait a minute to catch up.
    private val timeFilter = IntentFilter(Intent.ACTION_TIME_CHANGED).apply {
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onTimeChanged()
        }
    }

    private val minuteTick = object : Runnable {
        override fun run() {
            onMinuteTick()
            handler.postDelayed(this, minuteMs)
        }
    }

    /**
     * Registered for the activity's whole life, not only while it is started: an app installed or updated while
     * another app is in front must still mark the tiles stale, or Home would have to rebuild on every return.
     */
    fun registerPersistent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(packageReceiver, packageFilter)
        }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        tim?.registerCallback(inputCallback, handler)
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback)
            cm.registerNetworkCallback(NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback)
        }
    }

    fun unregisterPersistent() {
        context.unregisterReceiver(packageReceiver)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        tim?.unregisterCallback(inputCallback)
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback)
            cm.unregisterNetworkCallback(vpnCallback)
        }
    }

    fun registerTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(timeReceiver, timeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(timeReceiver, timeFilter)
        }
    }

    fun unregisterTime() {
        context.unregisterReceiver(timeReceiver)
    }

    fun startMinuteTick() {
        handler.postDelayed(minuteTick, minuteMs)
    }

    fun stopMinuteTick() {
        handler.removeCallbacks(minuteTick)
    }
}
