package com.sadique.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native, scalable Harmony Vision analog watch face. No WebView, bitmap frame allocation,
 * network request or animation service. Repaints only while Home is resumed and visible.
 */
internal class HarmonyVisionClockView(context: Context) : View(context) {
    companion object {
        const val HERITAGE = 0
        const val CHRONOGRAPH = 1
        const val SKELETON = 2
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val arc = RectF()
    private val hand = Path()
    private val now = Calendar.getInstance()
    private val date = SimpleDateFormat("EEE, MMM d", Locale.ENGLISH)
    private val clockFrame = Runnable {
        if (!running || !isAttachedToWindow || windowVisibility != VISIBLE || !isShown) return@Runnable
        invalidate()
        schedule()
    }
    private var faceGradient: Shader? = null
    private var bezelGradient: Shader? = null
    private var face = 0
    private var finish = 0
    private var accentIndex = 0
    private var ink = 0
    private var muted = 0
    private var accent = 0
    private var faceCenter = 0
    private var faceEdge = 0
    private var smooth = true
    private var hour24 = false
    private var running = false
    private var light = false
    private var digitalTime = ""
    private var lastDisplayMinute = -1
    private var lastHour = -1L
    private var shortDate = ""

    init {
        isFocusable = true
        isClickable = true
        contentDescription = "Harmony Vision analog clock. Open clock customization."
        configure(ThemePreferences.get(context))
    }

    fun configure(prefs: SharedPreferences) {
        face = HarmonyVisionClockMath.clampFace(prefs.getInt("harmony_clock_face", HERITAGE))
        finish = Math.max(0, Math.min(3, prefs.getInt("harmony_clock_finish", 0)))
        accentIndex = Math.max(0, Math.min(2, prefs.getInt("harmony_clock_accent", 0)))
        smooth = prefs.getBoolean("harmony_clock_sweep", true)
        hour24 = prefs.getBoolean("time_24h", false)
        lastDisplayMinute = -1
        light = finish == 3
        ink = if (light) 0xff1c3046.toInt() else 0xfff6fbff.toInt()
        muted = if (light) 0xff6a7d90.toInt() else 0xff97b1c6.toInt()
        accent = intArrayOf(0xff8cddff.toInt(), 0xffe8c278.toInt(), 0xffef98ae.toInt())[accentIndex]
        val center = intArrayOf(0xff173d5d.toInt(), 0xff0b0d14.toInt(), 0xff242c37.toInt(), 0xfff8fbff.toInt())
        val edge = intArrayOf(0xff051426.toInt(), 0xff020305.toInt(), 0xff080c15.toInt(), 0xffd9e5ef.toInt())
        faceCenter = center[finish]
        faceEdge = edge[finish]
        val bezelTop = intArrayOf(0xff7cadca.toInt(), 0xff777b83.toInt(), 0xffe0e5ec.toInt(), 0xffffffff.toInt())[finish]
        val bezelEnd = intArrayOf(0xff122f4c.toInt(), 0xff11151e.toInt(), 0xff4c596b.toInt(), 0xff9db0c0.toInt())[finish]
        bezelGradient = LinearGradient(-180f, -180f, 160f, 180f, bezelTop, bezelEnd, Shader.TileMode.CLAMP)
        faceGradient = RadialGradient(-58f, -72f, 290f, faceCenter, faceEdge, Shader.TileMode.CLAMP)
        invalidate()
        schedule()
    }

    fun active(active: Boolean) {
        running = active
        removeCallbacks(clockFrame)
        if (active) {
            invalidate()
            schedule()
        }
    }

    private fun schedule() {
        removeCallbacks(clockFrame)
        if (!running || !isAttachedToWindow || windowVisibility != VISIBLE || !isShown) return
        // 25 fps looks smooth on low-memory TVs without an always-on 60 fps renderer.
        val delay = if (smooth) 40L else Math.max(30L, 1000 - System.currentTimeMillis() % 1000)
        postDelayed(clockFrame, delay)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        schedule()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clockFrame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        schedule()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat() - resources.displayMetrics.density * 2
        if (side <= 0) return
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(side / 400f, side / 400f)
        disk(canvas)
        marks(canvas)
        now.timeInMillis = System.currentTimeMillis()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        val ms = now.get(Calendar.MILLISECOND)
        if (lastHour != now.get(Calendar.DAY_OF_YEAR).toLong()) {
            lastHour = now.get(Calendar.DAY_OF_YEAR).toLong()
            shortDate = date.format(now.time).uppercase(Locale.ENGLISH)
        }
        if (face == HERITAGE) heritage(canvas)
        else if (face == CHRONOGRAPH) chronograph(canvas)
        else skeleton(canvas)
        val displayMinute = h * 60 + m
        if (lastDisplayMinute != displayMinute) {
            lastDisplayMinute = displayMinute
            val displayHour = if (hour24) h else ((h + 11) % 12 + 1)
            digitalTime = String.format(Locale.ENGLISH, "%02d:%02d", displayHour, m)
            if (!hour24) digitalTime += if (h < 12) " AM" else " PM"
        }
        label(canvas, digitalTime, 0f, 120f, 10f, accent, true)
        @Suppress("UNUSED_VARIABLE")
        val minutes = m.toDouble() + (s.toDouble() + ms.toDouble() / 1000.0) / 60.0
        drawHand(canvas, HarmonyVisionClockMath.hourAngle(h, m, s, ms).toFloat(), 104f, 8f, if (light) 0xff243a50.toInt() else 0xffe5f2ff.toInt())
        drawHand(canvas, HarmonyVisionClockMath.minuteAngle(m, s, ms).toFloat(), 145f, 5f, if (light) 0xff233c53.toInt() else 0xfff7fbff.toInt())
        drawSecond(canvas, HarmonyVisionClockMath.secondAngle(s, ms, smooth).toFloat())
        fill(canvas, 0xff061523.toInt())
        canvas.drawCircle(0f, 0f, 10f, p)
        fill(canvas, accent)
        canvas.drawCircle(0f, 0f, 6.3f, p)
        fill(canvas, ink)
        canvas.drawCircle(0f, 0f, 2.3f, p)
        canvas.restore()
    }

    private fun fill(c: Canvas, color: Int) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeWidth = 1f
        p.alpha = 255
    }

    private fun stroke(color: Int, width: Float) {
        p.shader = null
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
    }

    private fun disk(c: Canvas) {
        p.shader = null
        fill(c, 0x90000000.toInt())
        c.drawCircle(0f, 5f, 198f, p)
        p.shader = bezelGradient
        c.drawCircle(0f, 0f, 196f, p)
        p.shader = null
        stroke(if (light) 0x66ffffff else 0xff78b9d5.toInt(), 1.8f)
        c.drawCircle(0f, 0f, 194f, p)
        fill(c, if (light) 0xffe2e9ef.toInt() else 0xff07101b.toInt())
        c.drawCircle(0f, 0f, 187f, p)
        p.shader = faceGradient
        p.style = Paint.Style.FILL
        c.drawCircle(0f, 0f, 181f, p)
        p.shader = null
        stroke(if (light) 0x708199a8 else 0x888dbed7.toInt(), 1.25f)
        c.drawCircle(0f, 0f, 175f, p)
        stroke(if (light) 0x33647f95 else 0x335a91b4, 1f)
        c.drawCircle(0f, 0f, 139f, p)
        if (isFocused) {
            stroke(accent, 3f)
            c.drawCircle(0f, 0f, 196f, p)
        }
    }

    private fun marks(c: Canvas) {
        for (i in 0 until 60) {
            val a = Math.toRadians((i * 6 - 90).toDouble()).toFloat()
            val hour = i % 5 == 0
            val quarter = i % 15 == 0
            val inner = if (quarter) 158f else if (hour) 163f else 170f
            val outer = 176f
            stroke(if (quarter) accent else if (hour) ink else muted, if (quarter) 3.1f else if (hour) 1.8f else 0.75f)
            c.drawLine(
                Math.cos(a.toDouble()).toFloat() * inner, Math.sin(a.toDouble()).toFloat() * inner,
                Math.cos(a.toDouble()).toFloat() * outer, Math.sin(a.toDouble()).toFloat() * outer, p
            )
        }
        for (i in 1..12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble()).toFloat()
            label(
                c, i.toString(), Math.round(Math.cos(angle.toDouble()) * 146).toFloat(),
                Math.round(Math.sin(angle.toDouble()) * 146 + 6).toFloat(), if (i % 3 == 0) 21f else 17f, ink, true
            )
        }
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        fill(c, color)
        p.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
        c.drawText(text, x, y, p)
    }

    private fun heritage(c: Canvas) {
        label(c, "H A R M O N Y", 0f, -76f, 12f, ink, true)
        label(c, "V I S I O N   C H R O N O M E T E R", 0f, -55f, 6.2f, muted, false)
        stroke(accent, 1.6f)
        c.drawLine(-36f, -45f, 36f, -45f, p)
        label(c, shortDate, 0f, 89f, 10f, muted, true)
        label(c, "AUTOMATIC  ·  24 JEWELS", 0f, 106f, 7f, muted, false)
    }

    private fun chronograph(c: Canvas) {
        label(c, "HARMONY VISION", 0f, -80f, 11f, ink, true)
        subDial(c, -65f, 53f, "24 H", now.get(Calendar.HOUR_OF_DAY), 24)
        subDial(c, 65f, 53f, "SEC", now.get(Calendar.SECOND), 60)
        label(c, "CHRONOGRAPH", 0f, 101f, 8f, accent, true)
    }

    private fun subDial(c: Canvas, x: Float, y: Float, title: String, value: Int, total: Int) {
        stroke(if (light) 0x77476887 else 0x997198b4.toInt(), 1.3f)
        c.drawCircle(x, y, 37f, p)
        stroke(accent, 2.2f)
        arc.set(x - 33, y - 33, x + 33, y + 33)
        c.drawArc(arc, -90f, Math.max(0, value).toFloat() * 360f / total.toFloat(), false, p)
        label(c, title, x, y - 12, 8f, muted, true)
        label(c, String.format(Locale.ENGLISH, "%02d", value), x, y + 13, 20f, ink, true)
    }

    private fun skeleton(c: Canvas) {
        // Stationary mechanical-inspired geometry. No fictional active mechanism or sensor data.
        for (j in 0 until 3) {
            val x = if (j == 0) -66f else if (j == 1) 68f else 0f
            val y = if (j == 0) 45f else if (j == 1) 45f else -83f
            val teeth = if (j == 2) 12 else 16
            val radius = if (j == 2) 32f else 43f
            stroke(if (light) 0x5560798e else 0x8869b8ce.toInt(), 1.3f)
            c.drawCircle(x, y, radius, p)
            for (i in 0 until teeth) {
                val a = i.toDouble() * Math.PI * 2 / teeth.toDouble()
                stroke(if (i % 2 == 0) accent else muted, 2f)
                c.drawLine(
                    x + Math.cos(a).toFloat() * (radius - 6), y + Math.sin(a).toFloat() * (radius - 6),
                    x + Math.cos(a).toFloat() * (radius + 3), y + Math.sin(a).toFloat() * (radius + 3), p
                )
            }
            for (i in 0 until 4) {
                val a = i.toDouble() * Math.PI / 2 + j.toDouble() * 0.3
                stroke(if (light) 0x66708798 else 0x8865c1dd.toInt(), 3f)
                c.drawLine(x, y, x + Math.cos(a).toFloat() * (radius - 8), y + Math.sin(a).toFloat() * (radius - 8), p)
            }
            fill(c, accent)
            c.drawCircle(x, y, 5f, p)
        }
        label(c, "SKELETON", 0f, -121f, 11f, ink, true)
        label(c, shortDate, 0f, 103f, 10f, muted, true)
    }

    private fun drawHand(c: Canvas, angle: Float, length: Float, width: Float, color: Int) {
        c.save()
        c.rotate(angle)
        // Tail and pointed tip remain proportional at every widget size.
        hand.reset()
        hand.moveTo(-width / 2f, 13f)
        hand.lineTo(-width / 2f, -length + 14)
        hand.lineTo(0f, -length)
        hand.lineTo(width / 2f, -length + 14)
        hand.lineTo(width / 2f, 13f)
        hand.close()
        fill(c, 0x77000000)
        c.save()
        c.translate(1.5f, 2f)
        c.drawPath(hand, p)
        c.restore()
        fill(c, color)
        c.drawPath(hand, p)
        stroke(if (light) 0x5599a7b6 else 0x99ffffff.toInt(), 1f)
        c.drawLine(0f, 7f, 0f, -length + 17, p)
        c.restore()
    }

    private fun drawSecond(c: Canvas, angle: Float) {
        c.save()
        c.rotate(angle)
        stroke(accent, 2.5f)
        c.drawLine(0f, 29f, 0f, -165f, p)
        fill(c, accent)
        c.drawCircle(0f, -165f, 3.8f, p)
        c.restore()
    }
}
