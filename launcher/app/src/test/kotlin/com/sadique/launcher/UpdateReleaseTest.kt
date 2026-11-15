package com.sadique.launcher

import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

class UpdateReleaseTest {
    private fun asset(): JSONObject {
        return JSONObject().put("name", "PersonalLauncher.apk").put("state", "uploaded").put("id", 123).put("size", 1024)
            .put("browser_download_url", "https://github.com/sadique6571/PersonalLauncher/releases/download/build-77/PersonalLauncher.apk")
            .put("digest", "sha256:" + String(CharArray(64)).replace('\u0000', 'a'))
    }
    private fun release(asset: JSONObject): String { return JSONObject().put("assets", JSONArray().put(asset)).toString() }
    private fun reject(asset: JSONObject) {
        try { UpdateRelease.parse(release(asset)); fail("Untrusted asset accepted") } catch (expected: IllegalArgumentException) { }
    }
    @Test fun stableUploadedAssetHasBoundedSizeAndDigest() {
        val result = UpdateRelease.parse(release(asset()))
        assertEquals(123, result.id); assertEquals(1024, result.size); assertEquals(64, result.sha256.length)
    }
    @Test fun foreignAndPlaintextDownloadsAreRejected() {
        for (url in arrayOf("http://github.com/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk",
            "https://evil.example/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk",
            "https://github.com/other/repo/releases/download/x/PersonalLauncher.apk",
            "https://github.com@evil.example/sadique6571/PersonalLauncher/releases/download/x/PersonalLauncher.apk"))
            reject(asset().put("browser_download_url", url))
    }
    @Test fun absentDigestOversizeAndPendingUploadsAreRejected() {
        reject(asset().put("digest", "")); reject(asset().put("size", UpdateRelease.MAX_APK + 1))
        reject(asset().put("size", 0)); reject(asset().put("state", "new"))
    }
    @Test fun draftAndPrereleaseAreRejected() {
        for (flag in arrayOf("draft", "prerelease")) {
            val data = JSONObject(release(asset())).put(flag, true)
            try { UpdateRelease.parse(data.toString()); fail() } catch (expected: IllegalArgumentException) { }
        }
    }
}
