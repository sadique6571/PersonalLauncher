package com.sadique.launcher

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Reads the shell response until an exact readiness marker arrives. */
object VolumeStartupResponse {
    @JvmStatic
    @Throws(IOException::class)
    fun awaitReady(input: InputStream, limit: Int) {
        await(input, limit, arrayOf("PLV_AUTO_START_OK"), "Helper did not confirm readiness")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun awaitMarker(input: InputStream, limit: Int, marker: String) {
        await(input, limit, arrayOf(marker), "Setup command did not confirm readiness")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun awaitResult(input: InputStream, limit: Int, success: String, failed: String): String {
        return await(input, limit, arrayOf(success, failed), "Setup command did not confirm readiness")
    }

    private fun await(input: InputStream, limit: Int, markers: Array<String>, failure: String): String {
        val line = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            for (index in 0 until count) {
                if (++total > limit) throw IOException("Response too large")
                val value = buffer[index].toInt() and 255
                if (value == '\n'.code) {
                    val text = String(line.toByteArray(), Charsets.UTF_8).removeSuffix("\r")
                    if (text in markers) return text
                    line.reset()
                } else {
                    line.write(value)
                }
            }
        }
        throw IOException(failure)
    }
}
