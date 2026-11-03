package com.sadique.launcher;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.SystemClock;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Local, shell-authenticated signal pipe owned by the active accessibility helper. */
final class VolumePanelSocket implements AutoCloseable {
    static final String NAME = "com.sadique.launcher.volume-panel.v1";
    static final int MAGIC = 0x504c5601;
    private final LocalServerSocket server;
    private final VolumePanelUpdates updates;
    private volatile boolean closed;
    private volatile LocalSocket client;

    VolumePanelSocket(VolumePanelUpdates updates) throws IOException {
        this.updates = updates;
        server = new LocalServerSocket(NAME);
        Thread reader = new Thread(this::read, "launcher-volume-signals");
        reader.setDaemon(true); reader.start();
    }
    private void read() {
        while (!closed) {
            try (LocalSocket connection = server.accept()) {
                client = connection;
                if (closed) return;
                if (connection.getPeerCredentials().getUid() != 2000) continue;
                DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                output.writeInt(MAGIC); output.flush();
                Log.d("PersonalVolume", "direct panel channel connected");
                DataInputStream input = new DataInputStream(connection.getInputStream());
                while (!closed) {
                    long sentAt = input.readLong();
                    if (VolumePanelUpdates.fresh(sentAt, SystemClock.uptimeMillis())) updates.offer(sentAt);
                }
            } catch (IOException disconnected) {
                if (!closed) Log.d("PersonalVolume", "direct panel channel disconnected");
            } finally { client = null; }
        }
    }
    @Override public void close() {
        closed = true; updates.close();
        try { server.close(); } catch (IOException ignored) { }
        LocalSocket connection = client;
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
    }
}
