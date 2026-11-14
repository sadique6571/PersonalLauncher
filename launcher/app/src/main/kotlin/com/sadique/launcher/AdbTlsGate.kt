package com.sadique.launcher

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Refuse legacy AUTH on an mDNS endpoint: the saved identity is used only after STLS. */
internal object AdbTlsGate {
    const val CNXN: Int = 0x4e584e43
    const val STLS: Int = 0x534c5453

    @JvmStatic
    @Throws(IOException::class)
    fun packet(input: InputStream, output: OutputStream, expected: Int) {
        val header = ByteArray(24)
        DataInputStream(input).readFully(header)
        val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = data.int
        val length = data.getInt(12)
        val magic = data.getInt(20)
        if (command != expected || magic != (command xor 0xffffffff.toInt()) || length < 0 || length > 4096
            || (expected == STLS && length != 0)
        ) {
            throw IOException("Local endpoint did not negotiate ADB TLS")
        }
        val payload = ByteArray(length)
        DataInputStream(input).readFully(payload)
        output.write(header)
        output.write(payload)
        output.flush()
    }
}
