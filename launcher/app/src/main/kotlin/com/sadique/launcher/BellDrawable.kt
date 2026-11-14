package com.sadique.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * A brass temple bell (ghanta): a loop and knob on top, a domed body with two engraved bands and a flared lip,
 * and the clapper hanging below. [ring] swings it about the loop, a damped swing of about 16 degrees
 * that dies away within a second, with the clapper trailing the body. Each ring starts the other way from the
 * last one, and ringing again mid-swing carries on from where the bell is. The highlight stays put as the bell
 * swings, as if lit from the top left. Drawn in a 24 x 24 unit box scaled into the bounds; the swing is sized so
 * the bell stays inside it.
 */
class BellDrawable(private val density: Float) : Drawable() {
    private val body = Path()
    private val lip = Path()
    private val lipTop = Path()
    private val band1 = Path()
    private val band2 = Path()
    private val sheen = Path()
    private val crown = RectF(10.3f, 3.1f, 13.7f, 5.5f)

    private val bodyPaint: Paint
    private val lipPaint: Paint
    private val crownPaint: Paint
    private val outline: Paint
    private val groove: Paint
    private val bandDark: Paint
    private val bandLight: Paint
    private val sheenPaint: Paint
    private val loopDark: Paint
    private val loopBrass: Paint
    private val rodPaint: Paint
    private val ballPaint: Paint
    private val ballEdge: Paint
    private val paints: Array<Paint>
    private val animator: ValueAnimator

    private var angle = 0f
    private var clapperAngle = 0f
    private var startAngle = 0f
    private var amplitude = 0f
    private var direction = -1
    private var reduced = false
    private var alphaVal = 255

    init {
        buildPaths()

        val brass = intArrayOf(0xFF6B430E.toInt(), 0xFFB98424.toInt(), 0xFFF0CF72.toInt(), 0xFFD6A13A.toInt(), 0xFF9C6A1A.toInt(), 0xFF5C380A.toInt())
        val stops = floatArrayOf(0f, 0.16f, 0.33f, 0.52f, 0.8f, 1f)
        bodyPaint = fill().apply {
            shader = LinearGradient(5f, 0f, 19f, 0f, brass, stops, Shader.TileMode.CLAMP)
        }
        lipPaint = fill().apply {
            shader = LinearGradient(
                4.8f, 0f, 19.2f, 0f,
                intArrayOf(0xFF7A4E12.toInt(), 0xFFD9A43A.toInt(), 0xFFFBE08A.toInt(), 0xFFE3B04A.toInt(), 0xFFA87420.toInt(), 0xFF60400E.toInt()),
                stops, Shader.TileMode.CLAMP
            )
        }
        crownPaint = fill().apply {
            shader = LinearGradient(crown.left, 0f, crown.right, 0f, brass, stops, Shader.TileMode.CLAMP)
        }
        outline = stroke(0xFF452A05.toInt(), 0.55f)
        groove = stroke(0xFF5A3608.toInt(), 0.35f)
        bandDark = stroke(0x995A3608.toInt(), 0.38f)
        bandLight = stroke(0x66FFF2C0.toInt(), 0.25f)
        sheenPaint = stroke(0xB3FFF4D2.toInt(), 0.9f)
        loopDark = stroke(0xFF452A05.toInt(), 1.15f)
        loopBrass = stroke(0xFFD6A13A.toInt(), 0.55f)
        rodPaint = stroke(0xFF4E3208.toInt(), 0.75f)
        ballPaint = fill().apply {
            shader = RadialGradient(
                11.6f, 20.7f, 1.7f,
                intArrayOf(0xFFF2D27A.toInt(), 0xFFB07E22.toInt(), 0xFF5A3A0A.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        ballEdge = stroke(0xFF3E2604.toInt(), 0.4f)
        paints = arrayOf(
            bodyPaint, lipPaint, crownPaint, outline, groove, bandDark, bandLight, sheenPaint,
            loopDark, loopBrass, rodPaint, ballPaint, ballEdge
        )

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RING_MS
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = (a.animatedValue as Float) * RING_MS / 1000f
                angle = swing(t)
                val lagged = swing(Math.max(0f, t - LAG))
                clapperAngle = Math.max(angle - CLAPPER_MAX, Math.min(angle + CLAPPER_MAX, lagged))
                invalidateSelf()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    angle = 0f
                    clapperAngle = 0f
                    invalidateSelf()
                }
            })
        }
    }

    /** Rings the bell: a damped swing about the loop, with the clapper trailing. */
    fun ring() {
        if (reduced) return
        startAngle = angle
        direction = -direction
        amplitude = AMPLITUDE * direction
        animator.cancel()
        animator.start()
    }

    /** With reduced motion [ring] does nothing and a swing in progress stops at rest. */
    fun setReducedMotion(reducedMotion: Boolean) {
        reduced = reducedMotion
        if (reduced && animator.isRunning) animator.cancel()
    }

    /** Angle in degrees t seconds into a ring, carrying on from where the bell was when it was rung. */
    private fun swing(t: Float): Float {
        val wt = OMEGA * t
        var a = Math.exp((-t / TAU).toDouble()).toFloat() * (startAngle * Math.cos(wt.toDouble()).toFloat() + amplitude * Math.sin(wt.toDouble()).toFloat())
        if (t > 0.85f) {
            val f = Math.min(1f, (t - 0.85f) / 0.15f)
            a *= 1f - f * f * (3f - 2f * f)
        }
        return Math.max(-AMPLITUDE, Math.min(AMPLITUDE, a))
    }

    private fun buildPaths() {
        body.moveTo(9.8f, 5.0f)
        body.cubicTo(7.6f, 5.3f, 7.0f, 7.8f, 7.0f, 10.6f)
        body.cubicTo(7.0f, 13.6f, 6.6f, 15.6f, 5.3f, 16.9f)
        body.quadTo(5.0f, 17.5f, 5.5f, 18.0f)
        body.cubicTo(8.6f, 18.9f, 15.4f, 18.9f, 18.5f, 18.0f)
        body.quadTo(19.0f, 17.5f, 18.7f, 16.9f)
        body.cubicTo(17.4f, 15.6f, 17.0f, 13.6f, 17.0f, 10.6f)
        body.cubicTo(17.0f, 7.8f, 16.4f, 5.3f, 14.2f, 5.0f)
        body.quadTo(12.0f, 4.6f, 9.8f, 5.0f)
        body.close()

        lip.moveTo(5.3f, 16.9f)
        lip.quadTo(5.0f, 17.5f, 5.5f, 18.0f)
        lip.cubicTo(8.6f, 18.9f, 15.4f, 18.9f, 18.5f, 18.0f)
        lip.quadTo(19.0f, 17.5f, 18.7f, 16.9f)
        lip.cubicTo(15.4f, 17.7f, 8.6f, 17.7f, 5.3f, 16.9f)
        lip.close()

        lipTop.moveTo(5.3f, 16.9f)
        lipTop.cubicTo(8.6f, 17.7f, 15.4f, 17.7f, 18.7f, 16.9f)

        band1.moveTo(7.15f, 9.0f)
        band1.cubicTo(9.5f, 9.7f, 14.5f, 9.7f, 16.85f, 9.0f)

        band2.moveTo(6.8f, 14.9f)
        band2.cubicTo(9.2f, 15.7f, 14.8f, 15.7f, 17.2f, 14.9f)

        sheen.moveTo(9.2f, 6.2f)
        sheen.cubicTo(8.3f, 7.8f, 8.2f, 11.4f, 8.0f, 15.0f)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = Math.min(b.width(), b.height()) / BOX
        val save = if (alphaVal < 255) c.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), alphaVal) else c.save()
        c.clipRect(b)
        c.translate(b.exactCenterX() - 12f * s, b.exactCenterY() - 12f * s)
        c.scale(s, s)

        // clapper first: the body hides its top half
        c.save()
        c.rotate(clapperAngle, PX, PY)
        c.drawLine(12f, 8f, 12f, 20.1f, rodPaint)
        c.drawCircle(12f, 21.15f, 1.15f, ballPaint)
        c.drawCircle(12f, 21.15f, 1.15f, ballEdge)
        c.restore()

        c.save()
        c.rotate(angle, PX, PY)
        c.drawCircle(12f, 2.35f, 1.0f, loopDark)
        c.drawCircle(12f, 2.35f, 1.0f, loopBrass)
        c.drawRoundRect(crown, 1.2f, 1.2f, crownPaint)
        c.drawRoundRect(crown, 1.2f, 1.2f, outline)
        c.drawPath(body, bodyPaint)
        drawBand(c, band1)
        drawBand(c, band2)
        c.drawPath(lip, lipPaint)
        c.drawPath(lipTop, groove)
        // the light is fixed, so the highlight slides against the swing
        val shift = -angle * 0.035f
        c.translate(shift, 0f)
        c.drawPath(sheen, sheenPaint)
        c.drawLine(7.0f, 17.55f, 8.6f, 17.95f, sheenPaint)
        c.translate(-shift, 0f)
        c.drawPath(body, outline)
        c.restore()

        c.restoreToCount(save)
    }

    private fun drawBand(c: Canvas, band: Path) {
        c.drawPath(band, bandDark)
        c.translate(0f, 0.38f)
        c.drawPath(band, bandLight)
        c.translate(0f, -0.38f)
    }

    override fun getIntrinsicWidth(): Int = Math.round(BOX * density)

    override fun getIntrinsicHeight(): Int = Math.round(BOX * density)

    override fun setAlpha(alpha: Int) {
        if (this.alphaVal == alpha) return
        this.alphaVal = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alphaVal

    override fun setColorFilter(filter: ColorFilter?) {
        for (p in paints) p.colorFilter = filter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val BOX = 24f
        /** The pivot: the top of the loop. */
        private const val PX = 12f
        private const val PY = 1.3f
        private const val AMPLITUDE = 16f
        private val OMEGA = (2 * Math.PI * 3.4).toFloat()
        private const val TAU = 0.3f
        private const val RING_MS = 1000L
        /** The clapper follows the body this many seconds late, and never more than CLAPPER_MAX degrees apart. */
        private const val LAG = 0.055f
        private const val CLAPPER_MAX = 7f

        private fun fill(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun stroke(color: Int, width: Float): Paint {
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = width
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
    }
}
