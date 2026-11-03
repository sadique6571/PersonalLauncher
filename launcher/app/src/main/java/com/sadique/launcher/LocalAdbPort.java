package com.sadique.launcher;

import android.content.Context;
import android.net.nsd.*;
import java.io.IOException;
import java.net.*;
import java.util.ArrayDeque;
import java.util.List;

/** Discover only this device's current TLS endpoint, then connect through loopback. */
final class LocalAdbPort {
    static List<Integer> discover(Context context) throws Exception {
        NsdManager nsd=context.getSystemService(NsdManager.class);
        if (nsd==null) throw new IOException("Network discovery unavailable");
        LocalAdbCandidates candidates=new LocalAdbCandidates();
        ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();
        Object lock=new Object(); boolean[] resolving={false}, closed={false};
        class Resolver {
            void next() {
                synchronized (lock) {
                    if (closed[0] || resolving[0] || pending.isEmpty()) return;
                    resolving[0]=true;
                    try { nsd.resolveService(pending.remove(),new NsdManager.ResolveListener() {
                        public void onResolveFailed(NsdServiceInfo info,int error) { done(); }
                        public void onServiceResolved(NsdServiceInfo info) {
                            synchronized(lock) {
                                if (!closed[0] && local(info.getHost())) candidates.add(info.getPort());
                            }
                            done();
                        }
                        private void done() { synchronized(lock) { resolving[0]=false; } next(); }
                    }); } catch (RuntimeException error) { resolving[0]=false; next(); }
                }
            }
        }
        Resolver resolver=new Resolver();
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String type) { }
            public void onDiscoveryStopped(String type) { }
            public void onStartDiscoveryFailed(String type,int error) { candidates.finish(); }
            public void onStopDiscoveryFailed(String type,int error) { }
            public void onServiceLost(NsdServiceInfo info) { }
            public void onServiceFound(NsdServiceInfo info) {
                synchronized(lock) { if (closed[0] || pending.size()>=16) return; pending.add(info); }
                resolver.next();
            }
        };
        try {
            nsd.discoverServices("_adb-tls-connect._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);
            return candidates.await(15000,750);
        } finally {
            synchronized(lock) { closed[0]=true; pending.clear(); }
            try { nsd.stopServiceDiscovery(listener); } catch (RuntimeException ignored) { }
        }
    }
    private static boolean local(InetAddress address) {
        if (address==null) return false;
        try { return address.isLoopbackAddress() || NetworkInterface.getByInetAddress(address)!=null; }
        catch (SocketException unavailable) { return false; }
    }
}
