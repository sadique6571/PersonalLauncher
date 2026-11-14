package com.sadique.launcher

/** Shared native/SVG geometry commands: 0 move, 1 line, 2 quadratic, 3 close. */
internal object DemoGeometry {

    @JvmStatic
    fun clockSizes(tv: Boolean): IntArray {
        return if (tv) intArrayOf(64, 84, 104) else intArrayOf(44, 60, 76)
    }

    @JvmStatic
    fun ratio(): Float = 2.6f

    @JvmStatic
    fun footer(style: Int, density: Float): Float {
        return when (style) {
            ThemeStyle.DUNE, ThemeStyle.COBALT -> 8 * density
            ThemeStyle.SAGE -> 3 * density
            else -> 0f
        }
    }

    @JvmStatic
    fun outline(style: Int, w: Float, h: Float, radius: Float): Array<FloatArray> {
        var c = Math.max(1f, Math.min(radius, Math.min(w, h) * 0.45f))
        val p = ArrayList<FloatArray>()
        if (style == ThemeStyle.DUNE) {
            c = Math.min(c + h * 0.06f, h * 0.32f)
            p.add(floatArrayOf(0f, c, 0f))
            p.add(floatArrayOf(1f, w - c, 0f))
            p.add(floatArrayOf(1f, w, c))
            p.add(floatArrayOf(1f, w, h - c))
            p.add(floatArrayOf(1f, w - c, h))
            p.add(floatArrayOf(1f, c, h))
            p.add(floatArrayOf(1f, 0f, h - c))
            p.add(floatArrayOf(1f, 0f, c * 1.6f))
            p.add(floatArrayOf(1f, c * 0.45f, c))
            p.add(floatArrayOf(1f, c * 0.7f, c))
            p.add(floatArrayOf(1f, c, 0f))
        } else if (style == ThemeStyle.PEARL) {
            c = Math.min(c * 0.7f, h * 0.3f)
            val d = c * 0.4f
            p.add(floatArrayOf(0f, c, 0f))
            p.add(floatArrayOf(1f, w - c, 0f))
            p.add(floatArrayOf(2f, w - d, 0f, w - d, d))
            p.add(floatArrayOf(2f, w, d, w, c))
            p.add(floatArrayOf(1f, w, h - c))
            p.add(floatArrayOf(2f, w, h - d, w - d, h - d))
            p.add(floatArrayOf(2f, w - d, h, w - c, h))
            p.add(floatArrayOf(1f, c, h))
            p.add(floatArrayOf(2f, d, h, d, h - d))
            p.add(floatArrayOf(2f, 0f, h - d, 0f, h - c))
            p.add(floatArrayOf(1f, 0f, c))
            p.add(floatArrayOf(2f, 0f, d, d, d))
            p.add(floatArrayOf(2f, d, 0f, c, 0f))
        } else {
            c = Math.min(c * 0.7f, h * 0.4f)
            p.add(floatArrayOf(0f, c, 0f))
            if (style == ThemeStyle.SAGE) {
                val f = Math.min(w * 0.15f, h * 0.22f)
                p.add(floatArrayOf(1f, w - f, 0f))
                p.add(floatArrayOf(1f, w, f))
            } else {
                p.add(floatArrayOf(1f, w - c, 0f))
                p.add(floatArrayOf(2f, w, 0f, w, c))
            }
            p.add(floatArrayOf(1f, w, h - c))
            p.add(floatArrayOf(2f, w, h, w - c, h))
            p.add(floatArrayOf(1f, c, h))
            p.add(floatArrayOf(2f, 0f, h, 0f, h - c))
            p.add(floatArrayOf(1f, 0f, c))
            p.add(floatArrayOf(2f, 0f, 0f, c, 0f))
        }
        p.add(floatArrayOf(3f))
        return p.toTypedArray()
    }
}
