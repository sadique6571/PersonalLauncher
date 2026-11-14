package com.sadique.launcher

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import org.junit.Test
import org.junit.Assert.*

class WeatherMoonAssetTest {
    @Test fun bundledImageMatchesTheReviewedSurfaceAndDecodeBounds() {
        val asset = "src/main/res/drawable-nodpi/weather_moon_nasa.jpg"
        var file = File(asset); if (!file.isFile) file = File("app/" + asset); if (!file.isFile) file = File("launcher/app/" + asset)
        assertTrue("Bundled moon JPEG missing", file.isFile); val image = Files.readAllBytes(file.toPath()); assertTrue(image.size < 300_000)
        assertEquals(0xff, image[0].toInt() and 255); assertEquals(0xd8, image[1].toInt() and 255)
        var dimensions = false
        var p = 2
        while (p + 8 < image.size) {
            assertEquals(0xff, image[p++].toInt() and 255); while ((image[p].toInt() and 255) == 0xff) p++; val marker = image[p++].toInt() and 255
            if (marker == 0xd9 || marker == 0xda) break
            val length = ((image[p].toInt() and 255) shl 8) or (image[p + 1].toInt() and 255); assertTrue(length >= 2 && p + length <= image.size)
            if (marker == 0xc0 || marker == 0xc1 || marker == 0xc2) {
                assertEquals(1024, ((image[p + 3].toInt() and 255) shl 8) or (image[p + 4].toInt() and 255))
                assertEquals(1024, ((image[p + 5].toInt() and 255) shl 8) or (image[p + 6].toInt() and 255))
                dimensions = true; break
            }
            p += length
        }
        assertTrue("Expected 1024px lunar image", dimensions)
        // Locks the visually reviewed asset: its grey terrain has measured luminance deviation >34.
        val hash = StringBuilder()
        for (b in MessageDigest.getInstance("SHA-256").digest(image)) hash.append(String.format(Locale.ROOT, "%02x", b.toInt() and 255))
        assertEquals("f6766945087efb8f0f91ac0ec8e74a194ff9b7b65414f2d0859669367992a172", hash.toString())
    }
}
