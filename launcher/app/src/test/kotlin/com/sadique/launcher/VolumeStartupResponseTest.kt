package com.sadique.launcher

import java.io.*
import java.nio.charset.StandardCharsets
import org.junit.Test
import org.junit.Assert.*

class VolumeStartupResponseTest {
    private fun response(text: String, chunk: Int, failAfter: Boolean): InputStream {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        return object : InputStream() {
            var offset = 0
            override fun read(): Int { throw AssertionError("Use bulk reads") }
            override fun read(out: ByteArray, start: Int, length: Int): Int {
                if (offset == bytes.size) {
                    if (failAfter) throw IOException("Stream closed.")
                    return -1
                }
                val count = Math.min(Math.min(chunk, length), bytes.size - offset)
                System.arraycopy(bytes, offset, out, start, count); offset += count; return count
            }
        }
    }
    @Test fun acceptsMarkerWithoutReadingClosedStreamAgain() {
        VolumeStartupResponse.awaitReady(response("Fast helper running\nPLV_AUTO_START_OK\n", 1024, true), 32768)
    }
    @Test fun acceptsFragmentedCrLfMarker() {
        VolumeStartupResponse.awaitReady(response("status\r\nPLV_AUTO_START_OK\r\n", 1, true), 32768)
    }
    @Test fun missingOrPartialMarkerNeverConfirms() {
        for (text in arrayOf("", "helper failed\n", "PLV_AUTO_START_OK", "prefix PLV_AUTO_START_OK\n", "PLV_AUTO_START_OK_suffix\n")) {
            try { VolumeStartupResponse.awaitReady(response(text, 3, false), 32768); fail(text) }
            catch (expected: IOException) { assertEquals("Helper did not confirm readiness", expected.message) }
        }
    }
    @Test fun disconnectBeforeConfirmationRemainsFailure() {
        try { VolumeStartupResponse.awaitReady(response("helper failed\n", 2, true), 32768); fail() }
        catch (expected: IOException) { assertEquals("Stream closed.", expected.message) }
    }
    @Test fun outputLimitAppliesBeforeMarker() {
        try { VolumeStartupResponse.awaitReady(response("123456\nPLV_AUTO_START_OK\n", 1024, false), 6); fail() }
        catch (expected: IOException) { assertEquals("Response too large", expected.message) }
    }
    @Test fun outputAfterConfirmationIsNotRequired() {
        VolumeStartupResponse.awaitReady(response("PLV_AUTO_START_OK\nignored", 1024, true), 18)
    }
    @Test fun shellWaitsForClientCloseAfterConfirming() {
        org.junit.Assume.assumeTrue(File("/bin/sh").canExecute())
        val child = ProcessBuilder("/bin/sh", "-c", "echo PLV_AUTO_START_OK && read -r plv_ack").start()
        try {
            VolumeStartupResponse.awaitReady(child.inputStream, 32768)
            assertTrue(child.isAlive)
            child.outputStream.close()
            assertTrue(child.waitFor(3, java.util.concurrent.TimeUnit.SECONDS))
        } finally { child.destroyForcibly() }
    }
}
