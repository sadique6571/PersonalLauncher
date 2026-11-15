package com.sadique.launcher

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object GitHubReleaseDownload {
    private fun connection(location: String): HttpURLConnection {
        var current = location
        for (redirects in 0 until 6) {
            val url = URL(current)
            val host = url.host
            if ("https" != url.protocol || url.userInfo != null || (url.port != -1 && url.port != 443)
                || !(host == "api.github.com" || host == "github.com" || host == "release-assets.githubusercontent.com" || host == "objects.githubusercontent.com")
            ) throw IOException("Untrusted download host")
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "PersonalLauncher/7.0")
            conn.setRequestProperty("Accept", if (host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            val code = conn.responseCode
            if (code == 200) return conn
            val next = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && next != null) {
                current = URL(url, next).toString()
                continue
            }
            throw IOException("GitHub response $code")
        }
        throw IOException("Too many redirects")
    }

    fun fetch(location: String, limit: Int): ByteArray {
        val conn = connection(location)
        try {
            return conn.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        if (output.size() + n > limit) throw IOException("Response limit")
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun download(release: UpdateRelease, target: File, progress: UpdateDownloadUi) {
        val conn = connection(release.url)
        var size = 0L
        val until = System.currentTimeMillis() + 180_000
        try {
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        size += n
                        if (size > release.size || size > UpdateRelease.MAX_APK || System.currentTimeMillis() > until) throw IOException("APK limit")
                        output.write(buffer, 0, n)
                        progress.report("Downloading update", UpdateProgress.percent(size, release.size, false))
                    }
                    output.fd.sync()
                }
            }
        } finally {
            conn.disconnect()
        }
        if (size != release.size || release.sha256 != GitHubUpdates.hash(target)) throw IOException("APK integrity mismatch")
    }
}
