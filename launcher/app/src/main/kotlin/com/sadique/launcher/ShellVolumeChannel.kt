package com.sadique.launcher

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import java.io.IOException
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Uses the same external-provider acquisition path as adb's content command, then direct Binder IPC. */
// Only loaded by the standalone shell runtime, never by the installed target-SDK app.
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
internal class ShellVolumeChannel : AutoCloseable {
    companion object {
        private const val AUTHORITY = "com.sadique.launcher.theme"
    }

    private val sender: ExecutorService = Executors.newSingleThreadExecutor()
    private val token: IBinder = Binder()
    private val death = IBinder.DeathRecipient { close() }
    private var activity: Any? = null
    private var activityType: Class<*>? = null
    private var endpoint: IBinder? = null
    private var updates: VolumePanelUpdates? = null
    private var acquired = false
    private var registered = false
    @Volatile
    private var closed = false

    init {
        try {
            activity = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
            activityType = Class.forName("android.app.IActivityManager")
            val type = activityType!!
            var holder: Any?
            try {
                holder = type.getMethod(
                    "getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType,
                    IBinder::class.java, String::class.java
                ).invoke(activity, AUTHORITY, 0, token, "personal-launcher-volume")
            } catch (older: NoSuchMethodException) {
                holder = type.getMethod(
                    "getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType,
                    IBinder::class.java
                ).invoke(activity, AUTHORITY, 0, token)
            }
            if (holder == null) throw IOException("Launcher theme provider is unavailable")
            acquired = true
            val provider = holder.javaClass.getField("provider").get(holder)
            val providerType = Class.forName("android.content.IContentProvider")
            var response: Bundle?
            if (Build.VERSION.SDK_INT >= 31) {
                val source = AttributionSource.Builder(2000).setPackageName("com.android.shell").build()
                val call: Method = providerType.getMethod(
                    "call", AttributionSource::class.java, String::class.java, String::class.java,
                    String::class.java, Bundle::class.java
                )
                response = call.invoke(provider, source, AUTHORITY, VolumePanelBinder.METHOD, null, null) as Bundle?
            } else {
                try {
                    response = providerType.getMethod(
                        "call", String::class.java, String::class.java, String::class.java,
                        String::class.java, Bundle::class.java
                    ).invoke(provider, "com.android.shell", AUTHORITY, VolumePanelBinder.METHOD, null, null) as Bundle?
                } catch (older: NoSuchMethodException) {
                    response = providerType.getMethod(
                        "call", String::class.java, String::class.java, String::class.java, Bundle::class.java
                    ).invoke(provider, "com.android.shell", VolumePanelBinder.METHOD, null, null) as Bundle?
                }
            }
            if (response == null || response.getInt("protocol") != VolumePanelBinder.PROTOCOL) {
                throw IOException("Update the installed launcher APK: panel Binder protocol is unavailable")
            }
            val ep = response.getBinder("panel")
            if (ep == null) throw IOException("Enable the launcher accessibility helpers before starting panel mode")
            endpoint = ep
            if (VolumePanelBinder.DESCRIPTOR != ep.interfaceDescriptor) throw IOException("Panel Binder descriptor mismatch")
            updates = VolumePanelUpdates({ task -> sender.execute(task) }, { sentAt -> send(sentAt) })
            ep.linkToDeath(death, 0)
            send(0) // Readiness check only: no volume popup until a real press/change.
            if (closed) throw IOException("Panel Binder endpoint is not active")
            println("Direct launcher panel Binder connected through the theme provider.")
        } catch (unavailable: Exception) {
            close()
            throw IOException("Direct panel Binder unavailable", unavailable)
        }
    }

    private fun send(sentAt: Long) {
        if (closed) return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(VolumePanelBinder.DESCRIPTOR)
            data.writeLong(sentAt)
            val ep = endpoint
            if (ep == null || !ep.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) throw IOException("Panel Binder rejected request")
            reply.readException()
            if (reply.readInt() != 1) throw IOException("Panel accessibility helper is not active")
        } catch (lost: Exception) {
            if (!closed) System.err.println("Direct panel Binder lost: $lost")
            close()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Synchronized
    fun relayInstalled() {
        if (closed) throw IOException("Direct panel Binder is closed")
        registered = relayState(VolumePanelBinder.RELAY_ACTIVE)
    }

    private fun relayState(transaction: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(VolumePanelBinder.DESCRIPTOR)
            data.writeStrongBinder(token)
            val ep = endpoint!!
            if (!ep.transact(transaction, data, reply, 0)) return false
            reply.readException()
            return true
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun signal() {
        updates?.offer(SystemClock.uptimeMillis())
    }

    fun isOpen(): Boolean = !closed && endpoint?.isBinderAlive() == true

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (registered && endpoint?.isBinderAlive() == true) {
            try {
                relayState(VolumePanelBinder.RELAY_STOPPED)
            } catch (unavailable: Exception) {
                System.err.println("Could not release panel relay: $unavailable")
            }
        }
        registered = false
        updates?.close()
        sender.shutdownNow()
        endpoint?.let {
            try {
                it.unlinkToDeath(death, 0)
            } catch (alreadyRemoved: RuntimeException) {
            }
        }
        if (acquired) {
            acquired = false
            try {
                val type = activityType!!
                try {
                    type.getMethod(
                        "removeContentProviderExternalAsUser", String::class.java, IBinder::class.java,
                        Int::class.javaPrimitiveType
                    ).invoke(activity, AUTHORITY, token, 0)
                } catch (older: NoSuchMethodException) {
                    type.getMethod(
                        "removeContentProviderExternal", String::class.java, IBinder::class.java
                    ).invoke(activity, AUTHORITY, token)
                }
            } catch (failure: ReflectiveOperationException) {
                System.err.println("Could not release panel provider: $failure")
            }
        }
    }
}
