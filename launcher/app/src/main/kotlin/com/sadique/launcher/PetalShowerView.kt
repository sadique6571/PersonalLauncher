package com.sadique.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random

/**
 * A transparent full-screen overlay that plays a one-off shower of marigold petals. Each petal drops in from
 * just above the top edge, gathers speed as it falls, sways from side to side, spins, flips over (showing its
 * darker back) and fades out over the last quarter of its fall. Everything finishes within the given duration,
 * then the callback runs on the main thread. The view takes no focus, clicks or accessibility, uses a hardware
 * layer only while playing, and allocates nothing per frame.
 */
class PetalShowerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val COLORS = intArrayOf(
            0xFFFF8C1A.toInt(), 0xFFFFB627.toInt(), 0xFFF26A0D.toInt(), 0xFFFFC93C.toInt(), 0xFFE85D04.toInt()
        )
        private val EDGE = 0xFF7A2800.toInt()
        private val TAU = 2 * Math.PI

        /** One marigold petal, 1 unit tall around the origin: a narrow base fanning out to a frilled top. */
        private fun petalShape(r: Random): Path {
            val p = Path()
            val wTop = 0.36f + 0.08f * r.nextFloat()
            p.moveTo(0f, 0.5f)
            p.cubicTo(-0.16f, 0.36f, -wTop - 0.04f, 0.02f, -wTop, -0.3f)
            val scallops = 4 + r.nextInt(2)
            for (k in 0 until scallops) {
                val tx = -wTop + 2f * wTop * (k + 0.5f) / scallops.toFloat()
                val nx = -wTop + 2f * wTop * (k + 1f) / scallops.toFloat()
                val tipY = -0.5f + 0.2f * sq(tx / wTop) - 0.06f - 0.05f * r.nextFloat()
                val notchY = if (k == scallops - 1) -0.3f else -0.5f + 0.2f * sq(nx / wTop) + 0.05f + 0.03f * r.nextFloat()
                p.quadTo(tx, tipY, nx, notchY)
            }
            p.cubicTo(wTop + 0.04f, 0.02f, 0.16f, 0.36f, 0f, 0.5f)
            p.close()
            return p
        }

        private fun veinPath(r: Random): Path {
            val p = Path()
            val bend = (r.nextFloat() - 0.5f) * 0.06f
            p.moveTo(0f, 0.42f)
            p.quadTo(bend, 0f, 0f, -0.36f)
            p.moveTo(0f, 0.3f)
            p.quadTo(-0.12f, -0.05f, -0.2f, -0.3f)
            p.moveTo(0f, 0.3f)
            p.quadTo(0.12f, -0.05f, 0.2f, -0.3f)
            return p
        }

        private fun sq(v: Float): Float {
            return v * v
        }

        private fun scale(c: Int, f: Float): Int {
            val r = minOf(255, (((c shr 16) and 0xFF).toFloat() * f).toInt())
            val g = minOf(255, (((c shr 8) and 0xFF).toFloat() * f).toInt())
            val b = minOf(255, ((c and 0xFF).toFloat() * f).toInt())
            return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
    }

    private val density = context.resources.displayMetrics.density
    private val rnd = Random()
    private val shapes = arrayOfNulls<Path>(3)
    private val veins = arrayOfNulls<Path>(3)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vein = Paint(Paint.ANTI_ALIAS_FLAG)

    // per-petal state, grown only when a bigger shower is asked for
    private var count = 0
    private var xFrac = FloatArray(0)
    private var delay = FloatArray(0)
    private var life = FloatArray(0)
    private var drop = FloatArray(0)
    private var size = FloatArray(0)
    private var swayAmp = FloatArray(0)
    private var swayFreq = FloatArray(0)
    private var swayPhase = FloatArray(0)
    private var rot0 = FloatArray(0)
    private var spin = FloatArray(0)
    private var flipFreq = FloatArray(0)
    private var flipPhase = FloatArray(0)
    private var color = IntArray(0)
    private var shape = IntArray(0)

    private var playing = false
    private var start = 0L
    private var duration = 0L
    private var onDone: Runnable? = null
    private val finishTask: Runnable = Runnable { finish() }

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)

        val seeded = Random(0x9E7A1L)
        for (i in shapes.indices) {
            shapes[i] = petalShape(seeded)
            veins[i] = veinPath(seeded)
        }
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 0.045f
        edge.strokeJoin = Paint.Join.ROUND
        vein.style = Paint.Style.STROKE
        vein.strokeWidth = 0.035f
        vein.strokeCap = Paint.Cap.ROUND
        vein.color = EDGE
    }

    /**
     * Plays a shower of {@code petals} petals lasting {@code durationMs}, then runs {@code done} on the main
     * thread. Playing again before the last shower ends finishes that one first (its callback runs).
     */
    fun play(petals: Int, durationMs: Long, done: Runnable?) {
        if (playing) {
            removeCallbacks(finishTask)
            finish()
        }
        val n = Math.max(0, petals)
        ensureCapacity(n)
        count = n
        duration = Math.max(1L, durationMs)
        onDone = done
        for (i in 0 until n) {
            xFrac[i] = (i.toFloat() + rnd.nextFloat()) / n.toFloat() // spread evenly across the width
            delay[i] = rnd.nextFloat() * 0.32f // fractions of the duration
            life[i] = 0.6f + rnd.nextFloat() * (1f - delay[i] - 0.6f)
            drop[i] = 0.8f + rnd.nextFloat() * 0.35f // fraction of the height fallen over its life
            size[i] = 9f + rnd.nextFloat() * 7f // dp
            swayAmp[i] = 10f + rnd.nextFloat() * 24f // dp
            swayFreq[i] = 0.5f + rnd.nextFloat() * 0.6f // Hz
            swayPhase[i] = rnd.nextFloat() * TAU.toFloat()
            rot0[i] = rnd.nextFloat() * 360f
            spin[i] = (40f + rnd.nextFloat() * 120f) * (if (rnd.nextBoolean()) 1f else -1f) // degrees per second
            flipFreq[i] = 0.4f + rnd.nextFloat() // Hz
            flipPhase[i] = rnd.nextFloat() * TAU.toFloat()
            color[i] = rnd.nextInt(COLORS.size)
            shape[i] = rnd.nextInt(shapes.size)
        }
        playing = true
        start = SystemClock.uptimeMillis()
        postDelayed(finishTask, duration)
        postInvalidateOnAnimation()
    }

    private fun finish() {
        if (!playing) return
        playing = false
        invalidate()
        val done = onDone
        onDone = null
        done?.run()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (playing) {
            removeCallbacks(finishTask)
            finish()
        }
    }

    override fun onDraw(c: Canvas) {
        if (!playing) return
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - start).toFloat() / duration.toFloat()
        val secs = (now - start).toFloat() / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0 until count) {
            val p = (elapsed - delay[i]) / life[i]
            if (p <= 0f || p >= 1f) continue
            val len = size[i] * density
            val fall = (p + p * p) * 0.5f // gathers speed, like a petal against the air
            val y = -len * 1.5f + (h * drop[i] + len * 1.5f) * fall
            val sway = TAU * swayFreq[i].toDouble() * secs.toDouble() + swayPhase[i].toDouble()
            val x = xFrac[i] * w + swayAmp[i] * density * Math.sin(sway).toFloat()
            val angle = rot0[i] + spin[i] * secs + 22f * Math.cos(sway).toFloat() // tilts into the sway
            val flip = Math.cos(TAU * flipFreq[i].toDouble() * secs.toDouble() + flipPhase[i].toDouble()).toFloat()
            val sx = if (Math.abs(flip) < 0.08f) if (flip < 0f) -0.08f else 0.08f else flip
            val a = if (p > 0.75f) (255f * (1f - (p - 0.75f) / 0.25f)).toInt() else 255

            // turned edge-on it looks darker; the back is darker still
            val shade = (if (flip < 0f) 0.78f else 1f) * (0.8f + 0.2f * Math.abs(flip))
            val base = COLORS[color[i]]
            fill.color = scale(base, shade)
            fill.alpha = a
            edge.color = scale(base, 0.55f)
            edge.alpha = a * 3 / 4
            vein.alpha = a / 4

            c.save()
            c.translate(x, y)
            c.rotate(angle)
            c.scale(len * sx, len)
            shapes[shape[i]]?.let { c.drawPath(it, fill) }
            shapes[shape[i]]?.let { c.drawPath(it, edge) }
            veins[shape[i]]?.let { c.drawPath(it, vein) }
            c.restore()
        }
        postInvalidateOnAnimation()
    }

    private fun ensureCapacity(n: Int) {
        if (xFrac.size >= n) return
        xFrac = FloatArray(n)
        delay = FloatArray(n)
        life = FloatArray(n)
        drop = FloatArray(n)
        size = FloatArray(n)
        swayAmp = FloatArray(n)
        swayFreq = FloatArray(n)
        swayPhase = FloatArray(n)
        rot0 = FloatArray(n)
        spin = FloatArray(n)
        flipFreq = FloatArray(n)
        flipPhase = FloatArray(n)
        color = IntArray(n)
        shape = IntArray(n)
    }
}
