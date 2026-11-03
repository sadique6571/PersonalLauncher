package com.sadique.launcher;

import android.annotation.SuppressLint;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.IBinder;
import android.os.SystemClock;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One authenticated local connection. No broadcasts or per-step processes. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
final class ShellVolumeChannel implements AutoCloseable {
    private final LocalSocket socket = new LocalSocket();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final DataOutputStream output;
    private final VolumePanelUpdates updates;
    private volatile boolean closed;

    ShellVolumeChannel() throws Exception {
        try {
            // Verify the listener belongs to the installed launcher, not a socket-name impostor.
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "package");
            Object pm = Class.forName("android.content.pm.IPackageManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> type = Class.forName("android.content.pm.IPackageManager");
            Method uid;
            Object flags;
            try { uid = type.getMethod("getPackageUid", String.class, long.class, int.class); flags = 0L; }
            catch (NoSuchMethodException older) { uid = type.getMethod("getPackageUid", String.class, int.class, int.class); flags = 0; }
            int expectedUid = (Integer) uid.invoke(pm, "com.sadique.launcher", flags, 0);
            if (expectedUid < 10000) throw new IOException("Installed launcher UID unavailable");
            socket.connect(new LocalSocketAddress(VolumePanelSocket.NAME));
            socket.setSoTimeout(1500);
            if (socket.getPeerCredentials().getUid() != expectedUid) throw new IOException("Panel socket owner mismatch");
            int magic = new DataInputStream(socket.getInputStream()).readInt();
            if (magic != VolumePanelSocket.MAGIC) throw new IOException("Panel protocol mismatch; update launcher APK");
            output = new DataOutputStream(socket.getOutputStream());
            updates = new VolumePanelUpdates(sender::execute, sentAt -> {
                try { output.writeLong(sentAt); output.flush(); }
                catch (IOException lost) { System.err.println("Direct panel channel lost: " + lost); close(); }
            });
            System.out.println("Direct launcher panel channel connected.");
        } catch (Exception unavailable) {
            sender.shutdownNow();
            try { socket.close(); } catch (IOException ignored) { }
            throw new IOException("Direct panel unavailable: install the matching launcher APK and enable its accessibility helpers", unavailable);
        }
    }
    void signal() { updates.offer(SystemClock.uptimeMillis()); }
    boolean isOpen() { return !closed; }
    @Override public void close() {
        closed = true;
        updates.close(); sender.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
    }
}
