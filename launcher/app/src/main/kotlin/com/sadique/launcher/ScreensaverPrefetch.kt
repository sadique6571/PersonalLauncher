package com.sadique.launcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Downloads ahead independently of slideshow rendering. */
class ScreensaverPrefetch : JobService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var active: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        active = params
        task = worker.submit {
            val retry = try {
                fill()
            } catch (failure: Exception) {
                Log.w(TAG, "Photo queue refill failed", failure)
                true
            }
            main.post {
                if (active === params) {
                    active = null
                    task = null
                    jobFinished(params, retry)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (active === params) {
            active = null
            task?.cancel(true)
            task = null
        }
        return true
    }

    override fun onDestroy() {
        active = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun fill(): Boolean {
        LandscapeHistory(this).use { history ->
            val deadline = SystemClock.elapsedRealtime() + 150_000L
            while (!history.reserveFull() && !Thread.currentThread().isInterrupted &&
                SystemClock.elapsedRealtime() < deadline) {
                val scene = feed.next({ history.contains(it) || history.reserved(it) }, deadline) ?: break
                try {
                    val downloaded = OnlineContent.get(scene.url, 3 * 1024 * 1024, 3500, 8000)
                    val bytes = LandscapePhotoFraming.prepare(downloaded, scene) ?: continue
                    val image = decode(bytes) ?: continue
                    try {
                        val stored = if (bytes.size <= LandscapeHistory.RESERVE_PHOTO_LIMIT) bytes else encode(image)
                        if (stored != null && !Thread.currentThread().isInterrupted) {
                            history.saveForOffline(scene.id, scene.subject, stored, fingerprint(image))
                        }
                    } finally {
                        image.recycle()
                    }
                } catch (failure: Exception) {
                    if (Thread.currentThread().isInterrupted) break
                    Log.d(TAG, "Skipping unavailable photo from ${scene.id.substringBefore(':')}", failure)
                }
            }
            return !history.reserveFull()
        }
    }

    private fun encode(image: Bitmap): ByteArray? {
        ByteArrayOutputStream().use { output ->
            for (quality in intArrayOf(82, 68, 52)) {
                output.reset()
                if (image.compress(Bitmap.CompressFormat.JPEG, quality, output) &&
                    output.size() <= LandscapeHistory.RESERVE_PHOTO_LIMIT) return output.toByteArray()
            }
            return null
        }
    }

    companion object {
        private const val TAG = "PersonalLandscape"
        private const val JOB = 74130
        // Used only by the single refill worker. Keep cursors across successive jobs.
        private val feed = LandscapeFeed()

        @JvmStatic
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB) != null) return
            val job = JobInfo.Builder(JOB, ComponentName(context, ScreensaverPrefetch::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(10_000L, JobInfo.BACKOFF_POLICY_LINEAR)
                .setOverrideDeadline(120_000L)
                .build()
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "Android did not schedule the photo queue refill")
            }
        }

        @JvmStatic
        fun decode(bytes: ByteArray): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (!LandscapeFeed.fitsWidescreen(options.outWidth, options.outHeight)) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (options.outWidth / options.inSampleSize > 1920 &&
                options.outHeight / options.inSampleSize / 2 > 480) options.inSampleSize *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        @JvmStatic
        fun fingerprint(image: Bitmap): LongArray {
            val width = minOf(image.width, Math.round(image.height * 16f / 9))
            val height = minOf(image.height, Math.round(image.width * 9f / 16))
            val left = (image.width - width) / 2
            val top = (image.height - height) / 2
            val size = LandscapeFingerprint.SIZE
            val sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                Canvas(sample).drawBitmap(image, Rect(left, top, left + width, top + height),
                    Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                val pixels = IntArray(size * size)
                sample.getPixels(pixels, 0, size, 0, 0, size, size)
                return LandscapeFingerprint.of(pixels)
            } finally {
                sample.recycle()
            }
        }
    }
}
