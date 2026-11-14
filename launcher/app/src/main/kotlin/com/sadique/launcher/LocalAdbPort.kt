package com.sadique.launcher

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.ArrayDeque

/** Discover only this device's current TLS endpoint, then connect through loopback. */
object LocalAdbPort {

    @JvmStatic
    @Throws(Exception::class)
    fun discover(context: Context): List<Int> {
        val nsd = context.getSystemService(NsdManager::class.java)
            ?: throw IOException("Network discovery unavailable")
        val candidates = LocalAdbCandidates()
        val pending = ArrayDeque<NsdServiceInfo>()
        val lock = Any()
        val resolving = booleanArrayOf(false)
        val closed = booleanArrayOf(false)

        class Resolver {
            fun next() {
                synchronized(lock) {
                    if (closed[0] || resolving[0] || pending.isEmpty()) return
                    resolving[0] = true
                    try {
                        nsd.resolveService(pending.remove(), object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                                done()
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                synchronized(lock) {
                                    if (!closed[0] && local(info.host)) candidates.add(info.port)
                                }
                                done()
                            }

                            private fun done() {
                                synchronized(lock) { resolving[0] = false }
                                next()
                            }
                        })
                    } catch (_: RuntimeException) {
                        resolving[0] = false
                        next()
                    }
                }
            }
        }

        val resolver = Resolver()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                candidates.finish()
            }

            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(lock) {
                    if (closed[0] || pending.size >= 16) return
                    pending.add(info)
                }
                resolver.next()
            }
        }

        try {
            nsd.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            return candidates.await(15000, 750)
        } finally {
            synchronized(lock) {
                closed[0] = true
                pending.clear()
            }
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: RuntimeException) {
            }
        }
    }

    private fun local(address: InetAddress?): Boolean {
        if (address == null) return false
        return try {
            address.isLoopbackAddress || NetworkInterface.getByInetAddress(address) != null
        } catch (_: SocketException) {
            false
        }
    }
}
