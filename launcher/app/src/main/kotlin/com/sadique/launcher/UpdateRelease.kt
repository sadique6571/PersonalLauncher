package com.sadique.launcher

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Only uploaded stable APK assets from the fixed project are eligible. */
class UpdateRelease private constructor(
    @JvmField val id: Long,
    @JvmField val size: Long,
    @JvmField val url: String,
    @JvmField val sha256: String
) {
    companion object {
        const val REPO: String = "sadique6571/PersonalLauncher"
        const val MAX_APK: Long = 64L * 1024 * 1024

        @JvmStatic
        @Throws(Exception::class)
        fun parse(json: String): UpdateRelease {
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                throw IllegalArgumentException("Stable releases only")
            }
            val assets: JSONArray = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if ("PersonalLauncher.apk" != asset.optString("name")) continue
                val id = asset.getLong("id")
                val size = asset.getLong("size")
                val url = asset.getString("browser_download_url")
                val digest = asset.optString("digest").lowercase(Locale.ROOT)
                val uri = URI(url)
                if (id <= 0 || size <= 0 || size > MAX_APK || "uploaded" != asset.optString("state") ||
                    "https" != uri.scheme || "github.com" != uri.rawAuthority ||
                    uri.rawQuery != null || uri.rawFragment != null ||
                    !uri.path.startsWith("/$REPO/releases/download/") ||
                    !uri.path.endsWith("/PersonalLauncher.apk") || !digest.matches("sha256:[0-9a-f]{64}".toRegex())
                ) {
                    throw IllegalArgumentException("Untrusted release asset")
                }
                return UpdateRelease(id, size, url, digest.substring(7))
            }
            throw IllegalArgumentException("Release has no launcher APK")
        }
    }
}
