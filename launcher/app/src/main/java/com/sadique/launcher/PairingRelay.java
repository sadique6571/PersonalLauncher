package com.sadique.launcher;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Local-only, one-connection relay bounds the upstream library's pairing socket lifetime. */
final class PairingRelay implements AutoCloseable {
    private final ServerSocket listener;
    private final ScheduledExecutorService workers=Executors.newScheduledThreadPool(3);
    private volatile Socket client, target;
    PairingRelay(int targetPort) throws IOException { this(targetPort,false); }
    PairingRelay(int targetPort,boolean tlsOnly) throws IOException {
        listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1")); listener.setSoTimeout(20000);
        workers.schedule(this::close,tlsOnly?40:25,TimeUnit.SECONDS);
        workers.execute(() -> {
            try {
                client=listener.accept(); target=new Socket();
                target.connect(new InetSocketAddress("127.0.0.1",targetPort),3000);
                if (tlsOnly) {
                    client.setSoTimeout(5000); target.setSoTimeout(5000);
                    AdbTlsGate.packet(client.getInputStream(),target.getOutputStream(),AdbTlsGate.CNXN);
                    AdbTlsGate.packet(target.getInputStream(),client.getOutputStream(),AdbTlsGate.STLS);
                    client.setSoTimeout(0); target.setSoTimeout(0);
                }
                workers.execute(() -> copy(client,target)); copy(target,client);
            } catch (IOException | RuntimeException ended) { close(); }
        });
    }
    int port() { return listener.getLocalPort(); }
    private void copy(Socket from,Socket to) {
        try {
            byte[] bytes=new byte[4096]; int count;
            InputStream input=from.getInputStream(); OutputStream output=to.getOutputStream();
            while ((count=input.read(bytes))!=-1) { output.write(bytes,0,count); output.flush(); }
        } catch (IOException ended) { /* Deadline or peer shutdown. */ }
        finally { close(); }
    }
    @Override public void close() {
        try { listener.close(); } catch (IOException ignored) { }
        Socket a=client,b=target;
        if (a!=null) try { a.close(); } catch (IOException ignored) { }
        if (b!=null) try { b.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}
