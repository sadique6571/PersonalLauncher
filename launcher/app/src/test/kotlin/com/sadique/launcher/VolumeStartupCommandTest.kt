package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class VolumeStartupCommandTest {
    @Test fun shellQuoteKeepsCommandSubstitutionLiteral() {
        org.junit.Assume.assumeTrue(java.io.File("/bin/sh").canExecute())
        val value = "a'\$(printf unsafe)`printf unsafe`\$HOME"
        val child = ProcessBuilder("/bin/sh", "-c", "printf %s " + VolumeStartupCommand.quote(value)).start()
        val actual = String(child.inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        assertEquals(0, child.waitFor()); assertEquals(value, actual)
    }
    @Test fun helperStopsBeforePayloadIsReplacedAndUsesAutomaticInput() {
        val command = VolumeStartupCommand.build("YWJj", "/data/app/example/base.apk")
        assertTrue(command.indexOf(" stop") < command.indexOf(" && cp "))
        assertTrue(command.indexOf(" && cp ") < command.indexOf(" start auto 50 launcher"))
        assertTrue(command.endsWith(" && echo PLV_AUTO_START_OK && read -r plv_ack"))
        assertFalse(command.contains("tcpip:"))
    }
    @Test fun untrustedScriptEncodingAndApkLocationsAreRefused() {
        try { VolumeStartupCommand.build("YWJj; id", "/data/app/x/base.apk"); fail() } catch (expected: IllegalArgumentException) { }
        try { VolumeStartupCommand.build("YWJj", "/sdcard/random.apk"); fail() } catch (expected: IllegalArgumentException) { }
    }
}
