package com.sadique.launcher

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Bounded HTTPS reads with validation at every redirect. */
object OnlineContent {
    private val imageHosts = setOf(
        "images.unsplash.com", "live.staticflickr.com", "upload.wikimedia.org", "thumb.wikimedia.org",
        "oceanexplorer.noaa.gov", "wallspapers.org", "www.publicdomainpictures.net", "npgallery.nps.gov",
        "c.pxhere.com", "www.bing.com"
    )
    private val allowedHosts = imageHosts + setOf(
        "api.open-meteo.com", "geocoding-api.open-meteo.com", "wttr.in", "www.flickr.com",
        "commons.wikimedia.org", "nominatim.openstreetmap.org"
    )
    private val redirectStatuses = setOf(301, 302, 303, 307, 308)

    @JvmStatic
    @Throws(IOException::class)
    fun get(address: String, limit: Int): ByteArray = get(address, limit, 8_000, 10_000)

    @JvmStatic
    @Throws(IOException::class)
    fun get(address: String, limit: Int, connectTimeout: Int, readTimeout: Int): ByteArray {
        var current = address
        repeat(3) {
            val url = URL(current)
            val host = url.host
            if (url.protocol != "https" || host !in allowedHosts) throw IOException("Unsupported content source")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "PersonalLauncher/6.8 (https://github.com/sadique6571/PersonalLauncher)")
            val path = url.path
            val wallpaperMetadata = host == "wallspapers.org" && !path.startsWith("/download/")
            val accept = when {
                wallpaperMetadata || host == "www.flickr.com" -> "application/rss+xml,text/html"
                host in imageHosts && path != "/HPImageArchive.aspx" -> "image/jpeg,image/webp,image/png"
                else -> "application/json"
            }
            connection.setRequestProperty("Accept", accept)
            try {
                val status = connection.responseCode
                if (status in redirectStatuses) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrEmpty()) throw IOException("Redirect without Location")
                    current = URL(url, location).toString()
                    return@repeat
                }
                if (status != 200) throw IOException("Content service returned HTTP $status")
                if (connection.contentLengthLong > limit) throw IOException("Content too large")
                return connection.inputStream.use { readBounded(it, limit) }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (Thread.currentThread().isInterrupted) throw IOException("Cancelled")
            if (output.size() + count > limit) throw IOException("Content too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun json(address: String): String = String(get(address, 128 * 1024), Charsets.UTF_8)
}
