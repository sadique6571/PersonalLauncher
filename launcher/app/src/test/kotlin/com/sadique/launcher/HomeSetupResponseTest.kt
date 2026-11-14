package com.sadique.launcher

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSetupResponseTest {
    private fun body(text: String): InputStream {
        return ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8))
    }

    @Test fun failureMarkerDoesNotBecomeSuccess() {
        assertEquals(HomeSetupCommand.FAILED, VolumeStartupResponse.awaitResult(body("Success\n" + HomeSetupCommand.FAILED + "\n"), 32768, HomeSetupCommand.MARKER, HomeSetupCommand.FAILED))
    }

    @Test fun confirmedHomeUsesItsOwnMarker() {
        assertEquals(HomeSetupCommand.MARKER, VolumeStartupResponse.awaitResult(body(HomeSetupCommand.MARKER + "\r\n"), 32768, HomeSetupCommand.MARKER, HomeSetupCommand.FAILED))
    }

    @Test(expected = IOException::class)
    fun volumeMarkerDoesNotConfirmHome() {
        VolumeStartupResponse.awaitResult(body("PLV_AUTO_START_OK\n"), 32768, HomeSetupCommand.MARKER, HomeSetupCommand.FAILED)
    }
}
