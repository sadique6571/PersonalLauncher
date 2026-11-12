package com.sadique.launcher;

import android.annotation.SuppressLint;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses the same external-provider acquisition path as adb's content command, then direct Binder IPC. */
// Only loaded by the standalone shell runtime, never by the installed target-SDK app.
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
final class ShellVolumeChannel implements AutoCloseable {
    private static final String AUTHORITY = "com.sadique.launcher.theme";
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final IBinder token = new Binder();
    private final IBinder.DeathRecipient death = this::close;
    private Object activity;
    private Class<?> activityType;
    private IBinder endpoint;
    private VolumePanelUpdates updates;
    private boolean acquired, registered;
    private volatile boolean closed;

    ShellVolumeChannel() throws Exception {
        try {
            activity = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            activityType = Class.forName("android.app.IActivityManager");
            Object holder;
            try {
                holder = activityType.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                        .invoke(activity, AUTHORITY, 0, token, "personal-launcher-volume");
            } catch (NoSuchMethodException older) {
                holder = activityType.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class)
                        .invoke(activity, AUTHORITY, 0, token);
            }
            if (holder == null) throw new IOException("Launcher theme provider is unavailable");
            acquired = true;
            Object provider = holder.getClass().getField("provider").get(holder);
            Class<?> providerType = Class.forName("android.content.IContentProvider");
            Bundle response;
            if (Build.VERSION.SDK_INT >= 31) {
                AttributionSource source = new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
                Method call = providerType.getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class);
                response = (Bundle) call.invoke(provider, source, AUTHORITY, VolumePanelBinder.METHOD, null, null);
            } else {
                try {
                    response = (Bundle) providerType.getMethod("call", String.class, String.class, String.class, String.class, Bundle.class)
                            .invoke(provider, "com.android.shell", AUTHORITY, VolumePanelBinder.METHOD, null, null);
                } catch (NoSuchMethodException older) {
                    response = (Bundle) providerType.getMethod("call", String.class, String.class, String.class, Bundle.class)
                            .invoke(provider, "com.android.shell", VolumePanelBinder.METHOD, null, null);
                }
            }
            if (response == null || response.getInt("protocol") != VolumePanelBinder.PROTOCOL) {
                throw new IOException("Update the installed launcher APK: panel Binder protocol is unavailable");
            }
            endpoint = response.getBinder("panel");
            if (endpoint == null) throw new IOException("Enable the launcher accessibility helpers before starting panel mode");
            if (!VolumePanelBinder.DESCRIPTOR.equals(endpoint.getInterfaceDescriptor())) throw new IOException("Panel Binder descriptor mismatch");
            updates = new VolumePanelUpdates(sender::execute, this::send);
            endpoint.linkToDeath(death, 0);
            send(0); // Readiness check only: no volume popup until a real press/change.
            if (closed) throw new IOException("Panel Binder endpoint is not active");
            System.out.println("Direct launcher panel Binder connected through the theme provider.");
        } catch (Exception unavailable) {
            close();
            throw new IOException("Direct panel Binder unavailable", unavailable);
        }
    }
    private void send(long sentAt) {
        if (closed) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VolumePanelBinder.DESCRIPTOR);
            data.writeLong(sentAt);
            if (!endpoint.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) throw new IOException("Panel Binder rejected request");
            reply.readException();
            if (reply.readInt() != 1) throw new IOException("Panel accessibility helper is not active");
        } catch (Exception lost) {
            if (!closed) System.err.println("Direct panel Binder lost: " + lost);
            close();
        } finally { data.recycle(); reply.recycle(); }
    }
    synchronized void relayInstalled() throws Exception {
        if (closed) throw new IOException("Direct panel Binder is closed");
        registered = relayState(VolumePanelBinder.RELAY_ACTIVE);
    }
    private boolean relayState(int transaction) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VolumePanelBinder.DESCRIPTOR);
            data.writeStrongBinder(token);
            if (!endpoint.transact(transaction, data, reply, 0)) return false;
            reply.readException();
            return true;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
    void signal() { VolumePanelUpdates pending = updates; if (pending != null) pending.offer(SystemClock.uptimeMillis()); }
    boolean isOpen() { return !closed && endpoint != null && endpoint.isBinderAlive(); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (registered && endpoint != null && endpoint.isBinderAlive()) {
            try { relayState(VolumePanelBinder.RELAY_STOPPED); }
            catch (Exception unavailable) { System.err.println("Could not release panel relay: " + unavailable); }
        }
        registered = false;
        if (updates != null) updates.close();
        sender.shutdownNow();
        if (endpoint != null) try { endpoint.unlinkToDeath(death, 0); } catch (RuntimeException alreadyRemoved) { }
        if (acquired) {
            acquired = false;
            try {
                try {
                    activityType.getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                            .invoke(activity, AUTHORITY, token, 0);
                } catch (NoSuchMethodException older) {
                    activityType.getMethod("removeContentProviderExternal", String.class, IBinder.class)
                            .invoke(activity, AUTHORITY, token);
                }
            } catch (ReflectiveOperationException failure) { System.err.println("Could not release panel provider: " + failure); }
        }
    }
}
