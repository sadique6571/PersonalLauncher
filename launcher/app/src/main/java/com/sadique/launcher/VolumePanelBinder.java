package com.sadique.launcher;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** Direct panel IPC, published only through the existing shell-protected theme provider. */
final class VolumePanelBinder {
    static final String METHOD = "volume_panel_v1";
    static final String DESCRIPTOR = "com.sadique.launcher.volume-panel.v1";
    static final int PROTOCOL = 1;
    static final int RELAY_ACTIVE = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int RELAY_STOPPED = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static volatile VolumePanelUpdates active;
    private static volatile IBinder relay;
    private static final Binder ENDPOINT = new Binder() {
        { attachInterface(null, DESCRIPTOR); }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws android.os.RemoteException {
            if (code != IBinder.FIRST_CALL_TRANSACTION && code != RELAY_ACTIVE && code != RELAY_STOPPED)
                return super.onTransact(code, data, reply, flags);
            // ContentProvider.call does not itself enforce a provider's read/write permission.
            // Validate both acquisition and each subsequent binder request explicitly.
            enforceShell();
            data.enforceInterface(DESCRIPTOR);
            if (code != IBinder.FIRST_CALL_TRANSACTION) {
                IBinder token = data.readStrongBinder();
                if (code == RELAY_ACTIVE && token != null) setRelay(token);
                else clearRelay(token);
                if (reply != null) reply.writeNoException();
                return true;
            }
            long sentAt = data.readLong();
            VolumePanelUpdates updates = active;
            boolean ready = updates != null;
            if (ready && VolumePanelUpdates.fresh(sentAt, android.os.SystemClock.uptimeMillis())) updates.offer(sentAt);
            if (reply != null) { reply.writeNoException(); reply.writeInt(ready ? 1 : 0); }
            return true;
        }
    };
    private VolumePanelBinder() { }
    static void enforceShell() {
        if (Binder.getCallingUid() != 2000) throw new SecurityException("Only adb shell may request the volume panel");
    }
    private static synchronized void setRelay(IBinder token) throws android.os.RemoteException {
        if (token.equals(relay)) return;
        token.linkToDeath(() -> clearRelay(token), 0);
        relay = token;
    }
    private static synchronized void clearRelay(IBinder token) {
        if (token != null && token.equals(relay)) relay = null;
    }
    static boolean ownsMediaKeys() {
        IBinder token = relay;
        return active != null && token != null && token.isBinderAlive();
    }
    static void attach(VolumePanelUpdates updates) { active = updates; }
    static void detach(VolumePanelUpdates updates) { if (active == updates) active = null; }
    static IBinder endpoint() { return active == null ? null : ENDPOINT; }
}
