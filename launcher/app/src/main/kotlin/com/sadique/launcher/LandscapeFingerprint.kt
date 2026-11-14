package com.sadique.launcher

/** Difference hashes from horizontal and vertical luminance changes in a 9×9 image sample. */
object LandscapeFingerprint {
    const val SIZE: Int = 9

    @JvmStatic
    fun of(pixels: IntArray): LongArray {
        var horizontal = 0L
        var vertical = 0L
        for (y in 0 until SIZE - 1) {
            for (x in 0 until SIZE - 1) {
                val i = y * SIZE + x
                val value = luminance(pixels[i])
                horizontal = (horizontal shl 1) or (if (value < luminance(pixels[i + 1])) 1L else 0L)
                vertical = (vertical shl 1) or (if (value < luminance(pixels[i + SIZE])) 1L else 0L)
            }
        }
        return longArrayOf(horizontal, vertical)
    }

    @JvmStatic
    fun matches(candidate: LongArray, horizontal: Long, vertical: Long): Boolean {
        return java.lang.Long.bitCount(candidate[0] xor horizontal) + java.lang.Long.bitCount(candidate[1] xor vertical) <= 8
    }

    private fun luminance(pixel: Int): Int {
        return 299 * ((pixel shr 16) and 255) + 587 * ((pixel shr 8) and 255) + 114 * (pixel and 255)
    }
}
