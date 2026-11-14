package com.sadique.launcher

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AdbTlsGateTest {
    fun header(command: Int, length: Int): ByteArray {
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(0).putInt(0).putInt(length).putInt(0).putInt(command xor 0xffffffff.toInt()).array()
    }

    @Test fun tlsUpgradeIsForwarded() {
        val packet = header(AdbTlsGate.STLS, 0)
        val output = ByteArrayOutputStream()
        AdbTlsGate.packet(ByteArrayInputStream(packet), output, AdbTlsGate.STLS)
        assertArrayEquals(packet, output.toByteArray())
    }

    @Test fun legacyAuthCannotUseTheSavedKeyAsSigningOracle() {
        val output = ByteArrayOutputStream()
        try {
            AdbTlsGate.packet(ByteArrayInputStream(header(0x48545541, 20)), output, AdbTlsGate.STLS)
            fail()
        } catch (expected: IOException) {
            assertEquals(0, output.size())
        }
    }

    @Test fun oversizedOrTruncatedFramesAreRefused() {
        for (bytes in arrayOf(header(AdbTlsGate.CNXN, 5000), ByteArray(3))) {
            try {
                AdbTlsGate.packet(ByteArrayInputStream(bytes), ByteArrayOutputStream(), AdbTlsGate.CNXN)
                fail()
            } catch (expected: IOException) {
            }
        }
    }
}
