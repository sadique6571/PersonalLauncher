package com.sadique.launcher

/** Fit the complete source mark inside a 256px cache image, including at Large logo size. */
object ArtworkFit {
    @JvmStatic
    fun scale(width: Int, height: Int, requested: Float): Float {
        if (width <= 0 || height <= 0 || !requested.isFinite()) {
            throw IllegalArgumentException("Invalid logo dimensions")
        }
        val normal = Math.min(220f / width, 220f / height) * Math.max(0.5f, Math.min(1.2f, requested))
        return Math.min(normal, Math.min(240f / width, 240f / height))
    }
}
