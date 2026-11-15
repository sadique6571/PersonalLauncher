package com.sadique.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Samples app artwork to choose tile backgrounds and focus colors. */
internal object AppIconColors {
    /** Share of the tile height the icon takes in uniform_tiles mode (onCreateViewHolder). */
    const val ICON_SHARE = 0.62f

    /**
     * The colour covering most of an icon, for a card that should read as that app: a small saturated badge
     * (a red "new" corner on a blue icon) must not win, as it can with {@link #vividColor}. Grey if none.
     */
    fun dominantColor(live: Drawable?): Int {
        if (live == null) return 0xFF6E6E73.toInt()
        val cs = live.constantState
        val d = if (cs != null) cs.newDrawable().mutate() else live
        val saved = Rect(d.bounds)
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        d.setBounds(saved)
        val weight = FloatArray(12)
        val sum = Array(12) { FloatArray(3) }
        val hsv = FloatArray(3)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val p = bmp.getPixel(x, y)
                if (Color.alpha(p) < 128) continue
                Color.colorToHSV(p, hsv)
                if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue   // white, grey and black carry no hue
                val bin = (hsv[0] / 30f).toInt() % 12
                val w = hsv[1] * hsv[2]
                weight[bin] += w
                sum[bin][0] += Color.red(p) * w
                sum[bin][1] += Color.green(p) * w
                sum[bin][2] += Color.blue(p) * w
            }
        }
        bmp.recycle()
        var best = 0
        for (i in 1 until 12) if (weight[i] > weight[best]) best = i
        if (weight[best] < n * n * 0.03f) return 0xFF6E6E73.toInt()   // a monochrome icon, like a grey gear
        val w = weight[best]
        return Color.rgb((sum[best][0] / w).toInt(), (sum[best][1] / w).toInt(), (sum[best][2] / w).toInt())
    }

    /**
     * The uniform tile's fill. Where the icon sits on a flat colour of its own (a white or coloured rounded
     * square), the card takes that colour, so the mark reads as filling the tile as on tvOS. Otherwise a
     * gradient of the icon's main colour, deepened so the mark stands out. The card clips the corners.
     */
    fun backdrop(live: Drawable?, fallback: Int): IntArray {
        val plate = iconPlate(live)
        if (plate != null) return plate
        val hsv = FloatArray(3)
        Color.colorToHSV(fallback, hsv)
        // Yellow darkened turns olive: lean it to amber instead.
        if (hsv[0] > 42f && hsv[0] < 70f && hsv[1] > 0.3f) hsv[0] = 36f
        hsv[2] = Math.min(hsv[2], 0.62f)
        val top = Color.HSVToColor(hsv)
        hsv[2] *= 0.6f
        return intArrayOf(top, Color.HSVToColor(hsv))
    }

    /**
     * The plate an icon's artwork sits on (a flat or vertically graded rounded square), carried out to the
     * card's top and bottom edges so the mark reads as filling the tile; null when the icon has no
     * opaque plate (a bare mark on transparency).
     */
    private fun iconPlate(live: Drawable?): IntArray? {
        if (live == null) return null
        val cs = live.constantState
        val d = if (cs != null) cs.newDrawable().mutate() else live
        val saved = Rect(d.bounds)
        val n = 48
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        d.setBounds(saved)
        // Just inside a rounded square's corners; one side may go a step further in, past a corner badge.
        // An icon padded on both sides is a small plate in the middle, which no fill would meet.
        var a = plateColor(bmp, 7, n)
        var b = plateColor(bmp, n - 8, n)
        var topRow = 7
        var bottomRow = n - 8
        if (a == 0 && b != 0) {
            topRow = 10
            a = plateColor(bmp, topRow, n)
        } else if (b == 0 && a != 0) {
            bottomRow = n - 11
            b = plateColor(bmp, bottomRow, n)
        }
        bmp.recycle()
        if (a == 0 || b == 0) return null
        // The sampled rows sit at these heights of the tile; extend the line through them to its edges. A row
        // taken further in sits on artwork-side plate, so its slope is less certain: cap how far it reaches.
        val margin = (1f - ICON_SHARE) / 2f
        val yA = margin + ICON_SHARE * topRow / n
        val yB = margin + ICON_SHARE * bottomRow / n
        var t0 = -yA / (yB - yA)
        var t1 = (1f - yA) / (yB - yA)
        if (topRow != 7 || bottomRow != n - 8) {
            t0 = Math.max(-0.35f, t0)
            t1 = Math.min(1.35f, t1)
        }
        return intArrayOf(mix(a, b, t0), mix(a, b, t1))
    }

    /** {@link #plateColor(int[])} of row y, between the same insets as the row's distance from the edge. */
    private fun plateColor(bmp: Bitmap, y: Int, n: Int): Int {
        val inset = Math.min(y, n - 1 - y)
        val row = IntArray(n - 2 * inset)
        bmp.getPixels(row, 0, row.size, inset, y, row.size, 1)
        return plateColor(row)
    }

    /** The colour most of a row shares, ignoring artwork that crosses it; 0 if the row is not mostly plate. */
    private fun plateColor(row: IntArray): Int {
        val r = IntArray(row.size)
        val g = IntArray(row.size)
        val b = IntArray(row.size)
        var opaque = 0
        for (p in row) {
            if (Color.alpha(p) < 240) continue
            r[opaque] = Color.red(p)
            g[opaque] = Color.green(p)
            b[opaque] = Color.blue(p)
            opaque++
        }
        if (opaque.toFloat() < row.size * 0.9f) return 0
        val mr = median(r, opaque)
        val mg = median(g, opaque)
        val mb = median(b, opaque)
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var close = 0
        for (i in 0 until opaque) {
            if (Math.abs(r[i] - mr) + Math.abs(g[i] - mg) + Math.abs(b[i] - mb) >= 48) continue
            sr += r[i]
            sg += g[i]
            sb += b[i]
            close++
        }
        if (close.toFloat() < row.size * 0.7f) return 0
        return Color.rgb((sr / close).toInt(), (sg / close).toInt(), (sb / close).toInt())
    }

    private fun median(values: IntArray, count: Int): Int {
        val sorted = values.copyOf(count)
        sorted.sort()
        return sorted[count / 2]
    }

    /** a + (b - a) * t per channel, clamped; t outside 0..1 extrapolates. */
    private fun mix(a: Int, b: Int, t: Float): Int {
        return Color.rgb(clamp(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
            clamp(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
            clamp(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t))
    }

    private fun clamp(v: Float): Int {
        return Math.max(0, Math.min(255, Math.round(v)))
    }

    fun vividColor(live: Drawable?): Int {
        if (live == null) return 0xFF444444.toInt()
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        val cs = live.constantState
        val d = if (cs != null) cs.newDrawable().mutate() else live
        val saved = Rect(d.bounds)
        val n = 12
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, n, n)
        d.draw(c)
        d.setBounds(saved)
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        val hsv = FloatArray(3)
        var best = 0xFF444444.toInt()
        var bestScore = -1f
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in px) {
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            r += pr
            g += pg
            b += pb
            val max = Math.max(pr, Math.max(pg, pb))
            val min = Math.min(pr, Math.min(pg, pb))
            val sat = if (max == 0) 0f else (max - min).toFloat() / max
            val value = max / 255f
            val score = sat * Math.min(1f, value * 1.4f)   // saturated and not too dark
            if (score > bestScore) {
                bestScore = score
                best = p
            }
        }
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            val avg = Color.rgb((r / (n * n)).toInt(), (g / (n * n)).toInt(), (b / (n * n)).toInt())
            Color.colorToHSV(avg, hsv)
            hsv[2] = Math.max(hsv[2], 0.55f)
            return Color.HSVToColor(hsv)
        }
        Color.colorToHSV(best, hsv)
        hsv[1] = Math.min(1f, hsv[1] * 1.1f)
        hsv[2] = Math.max(0.5f, hsv[2])
        return Color.HSVToColor(hsv)
    }
}
