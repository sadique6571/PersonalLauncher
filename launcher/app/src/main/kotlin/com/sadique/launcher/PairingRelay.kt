package com.sadique.launcher

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Local-only, one-connection relay bounds the upstream library's pairing socket lifetime. */
internal class PairingRelay @JvmOverloads constructor(targetPort: Int, tlsOnly: Boolean = false) : AutoCloseable {
    private val listener: ServerSocket
    private val workers: ScheduledExecutorService = Executors.newScheduledThreadPool(3)

    @Volatile
    private var client: Socket? = null

    @Volatile
    private var target: Socket? = null

    init {
        listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        listener.soTimeout = 20000
        workers.schedule({ this.close() }, (if (tlsOnly) 40 else 25).toLong(), TimeUnit.SECONDS)
        workers.execute {
            try {
                val c = listener.accept().also { client = it }
                val t = Socket().also { target = it }
                t.connect(InetSocketAddress("127.0.0.1", targetPort), 3000)
                if (tlsOnly) {
                    c.soTimeout = 5000
                    t.soTimeout = 5000
                    AdbTlsGate.packet(c.getInputStream(), t.getOutputStream(), AdbTlsGate.CNXN)
                    AdbTlsGate.packet(t.getInputStream(), c.getOutputStream(), AdbTlsGate.STLS)
                    c.soTimeout = 0
                    t.soTimeout = 0
                }
                workers.execute { copy(c, t) }
                copy(t, c)
            } catch (_: Exception) {
                close()
            }
        }
    }

    fun port(): Int = listener.localPort

    private fun copy(from: Socket, to: Socket) {
        try {
            val bytes = ByteArray(4096)
            var count: Int
            val input: InputStream = from.getInputStream()
            val output: OutputStream = to.getOutputStream()
            while (input.read(bytes).also { count = it } != -1) {
                output.write(bytes, 0, count)
                output.flush()
            }
        } catch (_: IOException) {
        } finally {
            close()
        }
    }

    override fun close() {
        try {
            listener.close()
        } catch (_: IOException) {
        }
        val a = client
        val b = target
        if (a != null) try {
            a.close()
        } catch (_: IOException) {
        }
        if (b != null) try {
            b.close()
        } catch (_: IOException) {
        }
        workers.shutdownNow()
    }
}
