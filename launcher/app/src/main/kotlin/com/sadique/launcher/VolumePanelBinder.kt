package com.sadique.launcher

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock

/** Volume panel IPC guarded by the calling UID. */
object VolumePanelBinder {
    const val METHOD = "volume_panel_v1"
    const val DESCRIPTOR = "com.sadique.launcher.volume-panel.v1"
    const val PROTOCOL = 1
    const val RELAY_ACTIVE = IBinder.FIRST_CALL_TRANSACTION + 1
    const val RELAY_STOPPED = IBinder.FIRST_CALL_TRANSACTION + 2

    @Volatile private var active: VolumePanelUpdates? = null
    @Volatile private var relay: IBinder? = null
    private val binder = object : Binder() {
        init {
            attachInterface(null, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION && code != RELAY_ACTIVE && code != RELAY_STOPPED) {
                return super.onTransact(code, data, reply, flags)
            }
            enforceShell()
            data.enforceInterface(DESCRIPTOR)
            if (code != IBinder.FIRST_CALL_TRANSACTION) {
                val token = data.readStrongBinder()
                if (code == RELAY_ACTIVE && token != null) setRelay(token) else clearRelay(token)
                reply?.writeNoException()
                return true
            }
            val sentAt = data.readLong()
            val updates = active
            if (updates != null && VolumePanelUpdates.fresh(sentAt, SystemClock.uptimeMillis())) updates.offer(sentAt)
            reply?.writeNoException()
            reply?.writeInt(if (updates == null) 0 else 1)
            return true
        }
    }

    @JvmStatic
    fun enforceShell() {
        if (Binder.getCallingUid() != 2000) throw SecurityException("Only adb shell may request the volume panel")
    }

    @Synchronized
    @Throws(RemoteException::class)
    private fun setRelay(token: IBinder) {
        if (token == relay) return
        token.linkToDeath({ clearRelay(token) }, 0)
        relay = token
    }

    @Synchronized
    private fun clearRelay(token: IBinder?) {
        if (token != null && token == relay) relay = null
    }

    @JvmStatic fun ownsMediaKeys(): Boolean = active != null && relay?.isBinderAlive == true
    @JvmStatic fun attach(updates: VolumePanelUpdates?) { active = updates }
    @JvmStatic fun detach(updates: VolumePanelUpdates?) { if (active === updates) active = null }
    @JvmStatic fun endpoint(): IBinder? = if (active == null) null else binder
}
