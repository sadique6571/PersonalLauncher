package com.sadique.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import java.util.ArrayList
import java.util.Comparator
import java.util.HashMap

/** Read-only bonded devices and public audio profiles; pairing/connection stays in system settings. */
@SuppressLint("MissingPermission")
internal class GlassBluetooth(private val host: Activity, private val changed: Runnable) {
    companion object {
        const val PERMISSION = 731
    }

    private val adapter: BluetoothAdapter? = host.getSystemService(BluetoothManager::class.java)?.adapter
    private val profiles = HashMap<Int, BluetoothProfile>()
    private var running = false
    private var registered = false
    @JvmField var requestingPermission = false

    private val services = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(type: Int, profile: BluetoothProfile) {
            if (!running) {
                adapter?.closeProfileProxy(type, profile)
                return
            }
            profiles[type] = profile
            changed.run()
        }

        override fun onServiceDisconnected(type: Int) {
            profiles.remove(type)
            changed.run()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            changed.run()
        }
    }

    private fun allowed(): Boolean {
        return Build.VERSION.SDK_INT < 31 || host.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (running) return
        running = true
        if (adapter == null || !allowed()) return
        try {
            adapter.getProfileProxy(host, services, BluetoothProfile.A2DP)
            adapter.getProfileProxy(host, services, BluetoothProfile.HEADSET)
            val f = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED).apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                host.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            } else {
                host.registerReceiver(receiver, f)
            }
            registered = true
        } catch (ignored: RuntimeException) {
        }
    }

    fun stop() {
        running = false
        if (registered) {
            host.unregisterReceiver(receiver)
            registered = false
        }
        if (adapter != null) {
            for ((key, value) in profiles) {
                adapter.closeProfileProxy(key, value)
            }
        }
        profiles.clear()
    }

    fun connectedName(): String {
        if (!allowed()) return "Bluetooth"
        try {
            for (profile in profiles.values) {
                for (device in profile.connectedDevices) {
                    val name = device.name
                    if (!name.isNullOrEmpty()) return name
                }
            }
        } catch (ignored: RuntimeException) {
        }
        return "Bluetooth"
    }

    fun show() {
        if (adapter != null && !allowed()) {
            requestingPermission = true
            host.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), PERMISSION)
            return
        }
        val rows = HomeLibraryUi.rows(host).apply { id = R.id.btDrawer }
        try {
            if (adapter == null) {
                rows.addView(GlassSettings.label(host, "Bluetooth unavailable", 13, 0xffa5b4cf.toInt()))
            } else {
                val bonded = adapter.bondedDevices
                if (bonded.isEmpty()) {
                    rows.addView(GlassSettings.label(host, "No paired devices", 13, 0xffa5b4cf.toInt()))
                } else {
                    val devices = ArrayList(bonded)
                    devices.sortWith(Comparator.comparing({ d: BluetoothDevice -> d.name ?: "null" }, String.CASE_INSENSITIVE_ORDER))
                    for (device in devices) {
                        var connected = false
                        for (p in profiles.values) {
                            if (p.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) connected = true
                        }
                        var name = device.name
                        if (name.isNullOrEmpty()) name = "Bluetooth device"
                        rows.addView(GlassSettings.row(host, name, if (connected) "Connected • Audio" else "Paired • Manage connection", symbol(device), if (connected) 0xff22c55e.toInt() else GlassSettings.themeAccent(host), Runnable { settings() }))
                    }
                }
            }
        } catch (denied: RuntimeException) {
            rows.addView(GlassSettings.label(host, "Device details unavailable. Use Bluetooth settings.", 13, 0xffa5b4cf.toInt()))
        }
        rows.addView(GlassSettings.row(host, "Scan / Pair", null, android.R.drawable.stat_sys_data_bluetooth, GlassSettings.themeAccent(host), Runnable { settings() }))
        GlassSettings.panel(host, "Bluetooth", rows).show()
    }

    private fun symbol(d: BluetoothDevice): Int {
        val c = d.bluetoothClass
        if (c == null) return android.R.drawable.stat_sys_data_bluetooth
        if (c.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
            return if (c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET) R.drawable.ic_volume_headphones else R.drawable.ic_volume_speaker
        }
        if (c.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL) return R.drawable.ic_input
        return android.R.drawable.stat_sys_data_bluetooth
    }

    private fun settings() {
        SystemSettings.openBluetoothAccessories(host)
    }
}
