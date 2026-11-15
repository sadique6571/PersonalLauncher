package com.sadique.launcher

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import java.util.Random

/**
 * A marigold (genda phool) seen from the front: four rings of ruffled petals, deep saffron outside to golden
 * inside, around a dark centre. Petal shapes and colours come from a fixed seed and are built once. Light comes
 * from the top left and stays there while the flower turns, and each ring shades the base of the one behind it.
 * {@link #turn()} rotates the flower a further 60 degrees; the inner rings turn slightly more than the outer
 * ones. Drawn in a 24 x 24 unit box scaled into the bounds.
 */
class MarigoldDrawable(private val density: Float) : Drawable() {

    companion object {
        private const val BOX = 24f
        private const val C = 12f
        private val COUNT = intArrayOf(13, 11, 9, 7)
        private val RADIUS = floatArrayOf(11.6f, 9.4f, 7.2f, 5.0f)
        /** How much faster each ring turns than the outer one, so a turn ruffles the flower a little. */
        private val SPIN = floatArrayOf(1f, 1.04f, 1.09f, 1.15f)
        private const val CORE_SPIN = 1.2f
        private val SHADES = arrayOf(
            intArrayOf(0xFFE5600A.toInt(), 0xFFF0800E.toInt()),
            intArrayOf(0xFFF0800E.toInt(), 0xFFF79A16.toInt()),
            intArrayOf(0xFFF9A31B.toInt(), 0xFFFFB524.toInt()),
            intArrayOf(0xFFFFB524.toInt(), 0xFFFFC63E.toInt())
        )
        private const val CORE_R = 2.9f
        /** Direction the light comes from (top left), in degrees with y pointing down. */
        private const val LIGHT_DEG = 225f
        private val LIT = 0xFFFFE9A6.toInt()
        private val SHADOWED = 0xFFA43E00.toInt()
        private val EDGE = 0xFF6E2400.toInt()
        /** Shadows fall a little down and right of each ring, away from the light. */
        private const val SHADOW_DX = 0.35f
        private const val SHADOW_DY = 0.45f
        private const val TURN_MS = 900L

        /** One petal: narrow at the base, fanning out to a frilled, notched edge. */
        private fun petal(a: Float, hw: Float, r0: Float, r: Float, scallops: Int, rnd: Random): Path {
            val p = Path()
            val a0 = a - hw
            val a1 = a + hw
            p.moveTo(px(r0, a - hw * 0.35f), py(r0, a - hw * 0.35f))
            val sideA = a0 - hw * 0.08f
            p.quadTo(px(r * 0.62f, sideA), py(r * 0.62f, sideA), px(r * 0.86f, a0), py(r * 0.86f, a0))
            val steps = scallops * 2
            for (k in 1 until steps step 2) {
                val ta = a0 + (a1 - a0) * k.toFloat() / steps.toFloat() + (rnd.nextFloat() - 0.5f) * hw * 0.12f
                val na = a0 + (a1 - a0) * (k + 1).toFloat() / steps.toFloat()
                val tr = r * (1.0f + 0.06f * rnd.nextFloat())
                val nr = if (k + 1 >= steps) r * 0.86f else r * (0.83f + 0.06f * rnd.nextFloat())
                p.quadTo(px(tr, ta), py(tr, ta), px(nr, na), py(nr, na))
            }
            val sideB = a1 + hw * 0.08f
            val endA = a + hw * 0.35f
            p.quadTo(px(r * 0.62f, sideB), py(r * 0.62f, sideB), px(r0, endA), py(r0, endA))
            p.close()
            return p
        }

        private fun px(r: Float, a: Float): Float = C + r * Math.cos(a.toDouble()).toFloat()

        private fun py(r: Float, a: Float): Float = C + r * Math.sin(a.toDouble()).toFloat()

        private fun lerp(a: Int, b: Int, t: Float): Int {
            val ar = (a shr 16) and 0xFF
            val ag = (a shr 8) and 0xFF
            val ab = a and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            return 0xFF000000.toInt() or
                ((ar.toFloat() + (br - ar).toFloat() * t).toInt() shl 16) or
                ((ag.toFloat() + (bg - ag).toFloat() * t).toInt() shl 8) or
                (ab.toFloat() + (bb - ab).toFloat() * t).toInt()
        }
    }

    private val petals = Array(COUNT.size) { ring -> arrayOfNulls<Path>(COUNT[ring]) }
    private val angles = Array(COUNT.size) { ring -> FloatArray(COUNT[ring]) }
    private val colors = Array(COUNT.size) { ring -> IntArray(COUNT[ring]) }
    private val edges = Array(COUNT.size) { ring -> IntArray(COUNT[ring]) }
    private val shadowR = FloatArray(COUNT.size)
    private val shadowPaints = arrayOfNulls<Paint>(COUNT.size)
    private val core = Path()
    private val seeds = FloatArray(18)

    private val fill: Paint
    private val edge: Paint
    private val coreFill: Paint
    private val coreEdge: Paint
    private val seedPaint: Paint
    private val paints: Array<Paint?>
    private val animator: ValueAnimator

    private var rotation = 0.0
    private var from = 0.0
    private var target = 0.0
    private var reduced = false
    private var alphaValue = 255

    init {
        val rnd = Random(0x6E7DL)
        for (i in COUNT.indices) buildRing(i, rnd)
        buildCore(rnd)

        fill = Paint(Paint.ANTI_ALIAS_FLAG)
        edge = Paint(Paint.ANTI_ALIAS_FLAG)
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 0.3f
        edge.strokeJoin = Paint.Join.ROUND
        for (i in COUNT.indices) {
            val inner = if (i + 1 < COUNT.size) RADIUS[i + 1] else CORE_R
            val sr = inner + 0.6f * (RADIUS[i] - inner)
            val q = inner / sr
            shadowR[i] = sr
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = RadialGradient(
                C + SHADOW_DX, C + SHADOW_DY, sr,
                intArrayOf(0x805A1E00.toInt(), 0x805A1E00.toInt(), 0x665A1E00, 0x005A1E00),
                floatArrayOf(0f, q * 0.9f, q, 1f), Shader.TileMode.CLAMP
            )
            shadowPaints[i] = paint
        }
        coreFill = Paint(Paint.ANTI_ALIAS_FLAG)
        coreFill.shader = RadialGradient(
            C - 0.7f, C - 0.7f, 3.6f,
            intArrayOf(0xFFE0821A.toInt(), 0xFFB04E08.toInt(), 0xFF7A3004.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        coreEdge = Paint(Paint.ANTI_ALIAS_FLAG)
        coreEdge.style = Paint.Style.STROKE
        coreEdge.strokeWidth = 0.3f
        coreEdge.strokeJoin = Paint.Join.ROUND
        coreEdge.color = 0xFF5C2403.toInt()
        seedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        seedPaint.color = 0xFFFFB84A.toInt()

        paints = arrayOfNulls(COUNT.size + 5)
        System.arraycopy(shadowPaints, 0, paints, 0, COUNT.size)
        paints[COUNT.size] = fill
        paints[COUNT.size + 1] = edge
        paints[COUNT.size + 2] = coreFill
        paints[COUNT.size + 3] = coreEdge
        paints[COUNT.size + 4] = seedPaint

        animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = TURN_MS
        animator.interpolator = DecelerateInterpolator(1.6f)
        animator.addUpdateListener { a ->
            rotation = from + (target - from) * (a.animatedValue as Float).toDouble()
            invalidateSelf()
        }
    }

    /** Turns the flower a further 60 degrees, easing to a stop. Repeated calls add up smoothly. */
    fun turn() {
        if (reduced) return
        from = rotation
        target += 60.0
        animator.cancel()
        animator.start()
    }

    /** With reduced motion {@link #turn()} does nothing; a turn in progress jumps to its end. */
    fun setReducedMotion(reducedMotion: Boolean) {
        reduced = reducedMotion
        if (reduced && animator.isRunning) animator.end()
    }

    // ---- geometry, built once ----

    private fun buildRing(ring: Int, rnd: Random) {
        val n = COUNT[ring]
        val offset = ring.toFloat() * (180f / n.toFloat()) + (rnd.nextFloat() - 0.5f) * 8f
        val edgeDepth = if (ring == 0) 0.55f else 0.45f
        for (j in 0 until n) {
            val deg = offset + j.toFloat() * 360f / n.toFloat() + (rnd.nextFloat() - 0.5f) * 10f
            val r = RADIUS[ring] * (0.96f + 0.04f * rnd.nextFloat())
            val hw = (Math.PI / n.toDouble()).toFloat() * (1.18f + 0.15f * rnd.nextFloat())
            val scallops = 3 + rnd.nextInt(2)
            petals[ring][j] = petal(Math.toRadians(deg.toDouble()).toFloat(), hw, r * 0.25f, r, scallops, rnd)
            angles[ring][j] = deg
            val base = lerp(SHADES[ring][0], SHADES[ring][1], rnd.nextFloat())
            colors[ring][j] = base
            edges[ring][j] = lerp(base, EDGE, edgeDepth)
        }
    }

    private fun buildCore(rnd: Random) {
        val bumps = 18
        for (k in 0..bumps * 2) {
            val a = (Math.PI * k.toDouble() / bumps.toDouble()).toFloat()
            val r = if (k % 2 == 1) CORE_R * (1.0f + 0.06f * rnd.nextFloat()) else CORE_R * 0.84f
            if (k == 0) core.moveTo(px(r, a), py(r, a))
            else if (k % 2 == 1) {
                val na = (Math.PI * (k + 1).toDouble() / bumps.toDouble()).toFloat()
                val nr = CORE_R * 0.84f
                core.quadTo(px(r, a), py(r, a), px(nr, na), py(nr, na))
            }
        }
        core.close()
        // tiny florets in a sunflower spiral
        val golden = Math.PI * (3 - Math.sqrt(5.0))
        for (k in 0 until seeds.size / 2) {
            val r = 0.62f * Math.sqrt(k.toDouble() + 0.5).toFloat()
            val a = (k.toDouble() * golden).toFloat()
            seeds[k * 2] = px(r, a)
            seeds[k * 2 + 1] = py(r, a)
        }
    }

    // ---- drawing ----

    override fun draw(@NonNull c: Canvas) {
        val b: Rect = bounds
        if (b.isEmpty) return
        val s = minOf(b.width(), b.height()).toFloat() / BOX
        val save = if (alphaValue < 255) c.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), alphaValue) else c.save()
        c.clipRect(b)
        c.translate(b.exactCenterX() - C * s, b.exactCenterY() - C * s)
        c.scale(s, s)

        for (i in COUNT.indices) {
            val rot = ((rotation * SPIN[i].toDouble()) % 360.0).toFloat()
            c.save()
            c.rotate(rot, C, C)
            val ring = petals[i]
            for (j in ring.indices) {
                val l = Math.cos(Math.toRadians((angles[i][j] + rot - LIGHT_DEG).toDouble())).toFloat()
                val base = colors[i][j]
                fill.color = if (l >= 0f) lerp(base, LIT, 0.3f * l) else lerp(base, SHADOWED, -0.24f * l)
                c.drawPath(ring[j]!!, fill)
                edge.color = edges[i][j]
                c.drawPath(ring[j]!!, edge)
            }
            c.restore()
            c.drawCircle(C + SHADOW_DX, C + SHADOW_DY, shadowR[i], shadowPaints[i]!!)
        }

        c.save()
        c.rotate(((rotation * CORE_SPIN.toDouble()) % 360.0).toFloat(), C, C)
        c.drawPath(core, coreFill)
        c.drawPath(core, coreEdge)
        for (k in seeds.indices step 2) c.drawCircle(seeds[k], seeds[k + 1], 0.26f, seedPaint)
        c.restore()

        c.restoreToCount(save)
    }

    // ---- helpers ----

    override fun getIntrinsicWidth(): Int {
        return Math.round(BOX * density)
    }

    override fun getIntrinsicHeight(): Int {
        return Math.round(BOX * density)
    }

    override fun setAlpha(alpha: Int) {
        if (alphaValue == alpha) return
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int {
        return alphaValue
    }

    override fun setColorFilter(@Nullable filter: ColorFilter?) {
        for (paint in paints) paint?.setColorFilter(filter)
        invalidateSelf()
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
