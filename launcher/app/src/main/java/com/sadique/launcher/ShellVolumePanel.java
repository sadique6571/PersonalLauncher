package com.sadique.launcher;

import android.annotation.SuppressLint;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Shell-only relay: the installed launcher still draws its existing accessibility overlay. */
// This class runs exclusively in the standalone shell app_process, not in the target-34 app.
// Discover transaction IDs from that runtime instead of hard-coding version-dependent numbers.
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
final class ShellVolumePanel implements AutoCloseable {
    private final VolumePanelSession<IBinder> session;
    private final ShellVolumeChannel channel;

    ShellVolumePanel() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder audioBinder = (IBinder) serviceManager.getMethod("getService", String.class).invoke(null, "audio");
        Class<?> audioType = Class.forName("android.media.IAudioService");
        Object audio = Class.forName("android.media.IAudioService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, audioBinder);
        Class<?> controllerType = Class.forName("android.media.IVolumeController");
        Class<?> controllerStub = Class.forName("android.media.IVolumeController$Stub");
        Method get = audioType.getMethod("getVolumeController");
        Method set = audioType.getMethod("setVolumeController", controllerType);
        Method asBinder = controllerType.getMethod("asBinder");
        Method asInterface = controllerStub.getMethod("asInterface", IBinder.class);
        Object originalController = get.invoke(audio);
        if (originalController == null) throw new IllegalStateException("No stock volume controller to preserve");
        IBinder original = (IBinder) asBinder.invoke(originalController);
        if (!original.isBinderAlive()) throw new IllegalStateException("Stock volume controller is not alive");
        java.lang.reflect.Field volumeTransaction = controllerStub.getDeclaredField("TRANSACTION_volumeChanged");
        volumeTransaction.setAccessible(true);
        int volumeChanged = volumeTransaction.getInt(null);
        String descriptor = original.getInterfaceDescriptor();
        if (!"android.media.IVolumeController".equals(descriptor)) throw new IllegalStateException("Unknown volume-controller protocol");
        channel = new ShellVolumeChannel();
        Binder relay = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int transactionFlags) throws RemoteException {
                if (code != volumeChanged) {
                    // Forward warnings, dismiss, accessibility mode and future callbacks unchanged.
                    return original.transact(code, data, reply, transactionFlags);
                }
                data.enforceInterface(descriptor);
                int stream = data.readInt();
                int flags = data.readInt();
                if (stream == 3) channel.signal();
                Parcel filtered = Parcel.obtain();
                try {
                    filtered.writeInterfaceToken(descriptor);
                    filtered.writeInt(stream);
                    // A lost app connection must not leave the user without a volume popup.
                    filtered.writeInt(channel.isOpen() ? VolumePanelSession.flags(stream, flags) : flags);
                    int remaining = data.dataAvail();
                    if (remaining > 0) filtered.appendFrom(data, data.dataPosition(), remaining);
                    filtered.setDataPosition(0);
                    return original.transact(code, filtered, reply, transactionFlags);
                } finally { filtered.recycle(); }
            }
        };
        // A dynamic interface is sufficient: the framework setter only marshals asBinder().
        Object replacement = Proxy.newProxyInstance(controllerType.getClassLoader(), new Class<?>[]{controllerType},
                (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())) return relay;
                    if ("toString".equals(method.getName())) return "PersonalLauncherVolumeRelay";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        session = new VolumePanelSession<>(new VolumePanelSession.Endpoint<IBinder>() {
            public IBinder get() throws Exception {
                Object current = get.invoke(audio);
                return current == null ? null : (IBinder) asBinder.invoke(current);
            }
            public void set(IBinder binder) throws Exception {
                set.invoke(audio, binder == relay ? replacement : asInterface.invoke(null, binder));
            }
        }, original, relay);
    }
    void install() throws Exception {
        session.install();
        System.out.println("Launcher volume panel active: stock media-popup flag suppressed; warnings forwarded.");
    }
    void signal() { channel.signal(); }
    @Override public void close() {
        try { session.close(); }
        catch (Exception error) { System.err.println("Could not restore volume controller; reboot restores SystemUI registration."); error.printStackTrace(System.err); }
        channel.close();
    }
}
