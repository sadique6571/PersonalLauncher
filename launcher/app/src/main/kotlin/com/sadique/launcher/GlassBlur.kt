package com.sadique.launcher

/** Three separable box passes on a bounded thumbnail; never run for each rendered frame. */
internal object GlassBlur {
    @JvmStatic
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (width <= 0 || height <= 0 || width.toLong() * height != pixels.size.toLong() || radius < 0 || radius > 32)
            throw IllegalArgumentException("Invalid blur bounds")
        if (radius == 0) return
        val scratch = IntArray(pixels.size)
        for (pass in 0..2) {
            line(pixels, scratch, width, height, radius, false)
            line(scratch, pixels, height, width, radius, true)
        }
    }

    private fun line(source: IntArray, out: IntArray, length: Int, lines: Int, radius: Int, vertical: Boolean) {
        val stride = if (vertical) lines else 1
        val lineStride = if (vertical) 1 else length
        val count = radius * 2 + 1
        for (row in 0 until lines) {
            val start = row * lineStride
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val color = source[start + Math.max(0, Math.min(length - 1, i)) * stride]
                r += (color shr 16) and 255
                g += (color shr 8) and 255
                b += color and 255
            }
            for (x in 0 until length) {
                out[start + x * stride] = 0xff000000.toInt() or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                val old = source[start + Math.max(0, x - radius) * stride]
                val next = source[start + Math.min(length - 1, x + radius + 1) * stride]
                r += ((next shr 16) and 255) - ((old shr 16) and 255)
                g += ((next shr 8) and 255) - ((old shr 8) and 255)
                b += (next and 255) - (old and 255)
            }
        }
    }
}
