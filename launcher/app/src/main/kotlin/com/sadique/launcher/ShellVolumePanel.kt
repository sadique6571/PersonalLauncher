package com.sadique.launcher

import android.annotation.SuppressLint
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Shell-only relay: the installed launcher still draws its existing accessibility overlay. */
// This class runs exclusively in the standalone shell app_process, not in the target-34 app.
// Discover transaction IDs from that runtime instead of hard-coding version-dependent numbers.
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
internal class ShellVolumePanel : AutoCloseable {
    private val session: VolumePanelSession<IBinder>
    private val channel: ShellVolumeChannel
    @Volatile
    private var restoreFailedState = false

    init {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val audioBinder = serviceManager.getMethod("getService", String::class.java).invoke(null, "audio") as IBinder
        val audioType = Class.forName("android.media.IAudioService")
        val audio = Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, audioBinder)
        val controllerType = Class.forName("android.media.IVolumeController")
        val controllerStub = Class.forName("android.media.IVolumeController\$Stub")
        val get: Method = audioType.getMethod("getVolumeController")
        val set: Method = audioType.getMethod("setVolumeController", controllerType)
        val asBinder: Method = controllerType.getMethod("asBinder")
        val asInterface: Method = controllerStub.getMethod("asInterface", IBinder::class.java)
        val originalController = get.invoke(audio)
        if (originalController == null) throw IllegalStateException("No stock volume controller to preserve")
        val original = asBinder.invoke(originalController) as IBinder
        if (!original.isBinderAlive) throw IllegalStateException("Stock volume controller is not alive")
        val volumeTransaction = controllerStub.getDeclaredField("TRANSACTION_volumeChanged")
        volumeTransaction.isAccessible = true
        val volumeChanged = volumeTransaction.getInt(null)
        val descriptor = original.interfaceDescriptor
        if ("android.media.IVolumeController" != descriptor) throw IllegalStateException("Unknown volume-controller protocol")
        channel = ShellVolumeChannel()
        val relay = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, transactionFlags: Int): Boolean {
                if (code != volumeChanged) {
                    // Forward warnings, dismiss, accessibility mode and future callbacks unchanged.
                    return original.transact(code, data, reply, transactionFlags)
                }
                data.enforceInterface(descriptor)
                val stream = data.readInt()
                val flags = data.readInt()
                val forwardedFlags = if (channel.isOpen()) VolumePanelSession.flags(stream, flags) else flags
                val filtered = Parcel.obtain()
                try {
                    filtered.writeInterfaceToken(descriptor)
                    filtered.writeInt(stream)
                    // A lost app connection must not leave the user without a volume popup.
                    filtered.writeInt(forwardedFlags)
                    val remaining = data.dataAvail()
                    if (remaining > 0) filtered.appendFrom(data, data.dataPosition(), remaining)
                    filtered.setDataPosition(0)
                    val forwarded = original.transact(code, filtered, reply, transactionFlags)
                    if (forwarded && forwardedFlags != flags) channel.signal()
                    return forwarded
                } finally {
                    filtered.recycle()
                }
            }
        }
        // A dynamic interface is sufficient: the framework setter only marshals asBinder().
        val replacement = Proxy.newProxyInstance(
            controllerType.classLoader, arrayOf(controllerType)
        ) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> relay
                "toString" -> "PersonalLauncherVolumeRelay"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        session = VolumePanelSession(object : VolumePanelSession.Endpoint<IBinder> {
            override fun get(): IBinder? {
                val current = get.invoke(audio)
                return if (current == null) null else asBinder.invoke(current) as IBinder
            }

            override fun set(controller: IBinder) {
                set.invoke(audio, if (controller === relay) replacement else asInterface.invoke(null, controller))
            }
        }, original, relay)
    }

    fun install() {
        session.install()
        channel.relayInstalled()
        println("Launcher volume panel active: stock media-popup flag suppressed; warnings forwarded.")
    }

    fun restoreFailed(): Boolean = restoreFailedState

    override fun close() {
        try {
            session.close()
            restoreFailedState = false
        } catch (error: Exception) {
            restoreFailedState = true
            System.err.println("Could not restore volume controller; reboot restores SystemUI registration.")
            error.printStackTrace(System.err)
        }
        channel.close()
    }
}
