package com.sadique.launcher

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log

/** Loads queued, bundled and live photos on the slideshow worker. */
internal class LandscapePhotoLoader(private val context: Context) {
    private val liveFeed = LandscapeFeed()
    private var bundledFallbackIndex = 0

    fun load(placeholder: Int, online: () -> Boolean): Pair<Bitmap?, String> {
        var result: Bitmap? = null
        var subject = ""
        try {
            LandscapeHistory(context).use { history ->
                var selected: LandscapeHistory.SavedPhoto?
                var i = 0
                while (result == null && i < LandscapeHistory.RESERVE_SIZE && !Thread.currentThread().isInterrupted) {
                    selected = history.takeOffline()
                    if (selected == null) break
                    result = ScreensaverPrefetch.decode(selected.bytes)
                    if (result != null) subject = selected.subject
                    i++
                }
                if (result == null) {
                    for (j in OFFLINE_RESOURCES.indices) {
                        if (result != null || Thread.currentThread().isInterrupted) break
                        if (j == placeholder || history.contains(OFFLINE_IDS[j])) continue
                        context.resources.openRawResource(OFFLINE_RESOURCES[j]).use { input ->
                            selected = LandscapeHistory.SavedPhoto(
                                OFFLINE_IDS[j], OFFLINE_SUBJECTS[j],
                                OnlineContent.readBounded(input, LandscapeHistory.RESERVE_PHOTO_LIMIT)
                            )
                            result = rememberPhoto(history, selected)
                            if (result != null) subject = selected!!.subject
                        }
                    }
                }
                if (result == null && online()) {
                    // Reserve empty and no bundled photo left: download the next
                    // unseen photo directly instead of waiting for the prefetch
                    // job to be scheduled, or the slideshow stalls on one photo.
                    val live = downloadLivePhoto(history)
                    result = live.first
                    subject = live.second
                }
            }
        } catch (unavailable: Exception) {
            Log.w("PersonalLandscape", "Photo history unavailable", unavailable)
        }
        if (result == null) {
            // Last resort: cycle a bundled photo so the slideshow never sticks
            // on one picture. Uses no history, so it also covers a broken
            // photo database and a device with no network at all.
            try {
                val index = bundledFallbackIndex
                bundledFallbackIndex = (bundledFallbackIndex + 1) % OFFLINE_RESOURCES.size
                context.resources.openRawResource(OFFLINE_RESOURCES[index]).use { input ->
                    val bytes = OnlineContent.readBounded(input, LandscapeHistory.RESERVE_PHOTO_LIMIT)
                    val fallback = ScreensaverPrefetch.decode(bytes)
                    if (fallback != null) {
                        result = fallback
                        subject = OFFLINE_SUBJECTS[index]
                    }
                }
            } catch (ignored: Exception) {
                Log.d("PersonalLandscape", "Bundled photo fallback unavailable", ignored)
            }
        }
        return result to subject
    }

    /**
     * Downloads the next photo straight from the feeds, with the same
     * decode and duplicate checks as the prefetch job. Runs only on the
     * single photo worker thread. Returns the bitmap and its caption, or a
     * null bitmap when nothing could be downloaded in time.
     */
    private fun downloadLivePhoto(history: LandscapeHistory): Pair<Bitmap?, String> {
        val deadline = SystemClock.elapsedRealtime() + 45_000L
        var attempts = 0
        while (attempts < 6 && !Thread.currentThread().isInterrupted &&
            SystemClock.elapsedRealtime() < deadline) {
            attempts++
            val scene = try {
                liveFeed.next({ id -> history.contains(id) || history.reserved(id) }, deadline)
            } catch (failure: Exception) {
                Log.w("PersonalLandscape", "Live photo feed unavailable", failure)
                null
            } ?: break
            try {
                val downloaded = OnlineContent.get(scene.url, 3 * 1024 * 1024, 3500, 8000)
                val bytes = LandscapePhotoFraming.prepare(downloaded, scene) ?: continue
                val image = ScreensaverPrefetch.decode(bytes) ?: continue
                var accepted = false
                try {
                    // A recycled scene is already in the shown history — showing it
                    // again is the point, so skip the dedup record instead of
                    // rejecting it.
                    accepted = if (history.contains(scene.id)) true else history.remember(scene.id, bytes, ScreensaverPrefetch.fingerprint(image))
                    if (accepted) return image to scene.subject
                } finally {
                    if (!accepted) image.recycle()
                }
            } catch (failure: Exception) {
                if (Thread.currentThread().isInterrupted) break
                Log.d("PersonalLandscape", "Skipping unavailable live photo from ${scene.id.substringBefore(':')}", failure)
            }
        }
        return null to ""
    }

    companion object {
        private val OFFLINE_RESOURCES = intArrayOf(
            R.drawable.offline_autumn, R.drawable.offline_mountain_road, R.drawable.offline_mississippi
        )
        private val OFFLINE_IDS = arrayOf(
            "publicdomainpictures:200000/velka/fall-by-the-lake-14767797082J2.jpg",
            "publicdomainpictures:470000/velka/landschap-bergweg-haarspeldbocht-1665124155qIR.jpg",
            "nps:29fc4bc2-b103-4b1b-939f-19d4b91ce209"
        )
        private val OFFLINE_SUBJECTS = arrayOf(
            "Autumn by the lake", "Winding mountain road", "Mississippi River · U.S. National Park Service"
        )

        val bundledPhotoCount: Int get() = OFFLINE_RESOURCES.size

        fun bundledResource(index: Int): Int = OFFLINE_RESOURCES[index]

        fun bundledSubject(index: Int): String = OFFLINE_SUBJECTS[index]

        private fun rememberPhoto(history: LandscapeHistory, photo: LandscapeHistory.SavedPhoto?): Bitmap? {
            if (photo == null || history.contains(photo.id) || Thread.currentThread().isInterrupted) return null
            val image = ScreensaverPrefetch.decode(photo.bytes) ?: return null
            var accepted = false
            try {
                accepted = history.remember(photo.id, photo.bytes, ScreensaverPrefetch.fingerprint(image))
                return if (accepted) image else null
            } finally {
                if (!accepted) image.recycle()
            }
        }
    }
}
