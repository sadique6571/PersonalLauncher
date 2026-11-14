package com.sadique.launcher

import android.app.Activity
import android.app.ActivityManager
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale

/** Samples system statistics while Home is visible. */
class GlassTelemetry(private val host: Activity) : LinearLayout(host) {
    private val ui = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    @Volatile private var sampling: Handler? = null
    private val labels = ArrayList<TextView>(3)
    private val values = ArrayList<TextView>(3)
    private val bars = ArrayList<ProgressBar>(3)

    init {
        orientation = VERTICAL
        id = R.id.statsCard
        val pad = dp(14)
        setPadding(pad, pad, pad, pad)
        background = GlassOsUi.panel(host, 22)
        addView(LinearLayout(host).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("SYSTEM TELEMETRY", 0xffa5b4cf.toInt()), LayoutParams(0, -2, 1f))
            addView(label("● Live", 0xff59e493.toInt()))
        })
        addView(View(host).apply { setBackgroundColor(0x18ffffff) },
            LayoutParams(-1, dp(1)).apply { topMargin = dp(8) })
        val names = arrayOf("CPU", "RAM", "MEMORY")
        val ids = intArrayOf(R.id.cpuVal, R.id.ramVal, R.id.memVal)
        val progressIds = intArrayOf(R.id.cpuProgress, R.id.ramProgress, R.id.memProgress)
        val colors = intArrayOf(0xff38bdf8.toInt(), 0xff34d399.toInt(), 0xfff59e0b.toInt())
        val ends = intArrayOf(0xff818cf8.toInt(), 0xff10b981.toInt(), 0xffec4899.toInt())
        for (i in names.indices) {
            val name = label(names[i], 0xffc5cede.toInt())
            val value = label("—", 0xfff0f7ff.toInt()).apply { id = ids[i] }
            labels.add(name)
            values.add(value)
            addView(LinearLayout(host).apply {
                setPadding(0, dp(10), 0, 0)
                addView(name, LayoutParams(0, -2, 1f))
                addView(value)
            })
            val bar = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
                id = progressIds[i]
                max = 100
                progressDrawable = layers(colors[i], ends[i])
            }
            bars.add(bar)
            addView(bar, LayoutParams(-1, dp(7)))
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun layers(start: Int, end: Int): LayerDrawable {
        val radius = dp(999).toFloat()
        val track = GradientDrawable().apply {
            setColor(0x18ffffff)
            cornerRadius = radius
        }
        val gradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = radius
        }
        return LayerDrawable(arrayOf(track, ClipDrawable(gradient, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    private fun dp(value: Int): Int = GlassSettings.dp(host, value.toFloat())
    private fun label(text: String, color: Int) = GlassSettings.label(host, text, 10, color)

    fun start() {
        if (thread != null) return
        val sampler = CpuUsageSampler(AndroidCpuSource())
        val background = HandlerThread("glass-stats").apply { start() }
        thread = background
        val handler = Handler(background.looper)
        sampling = handler
        handler.post(object : Runnable {
            override fun run() {
                if (sampling !== handler) return
                val cpu = sampler.sample()
                val memory = ActivityManager.MemoryInfo()
                context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
                val data = Environment.getDataDirectory()
                val total = data.totalSpace
                val free = data.freeSpace
                ui.post { if (sampling === handler) display(cpu, memory, total, free) }
                if (sampling === handler) handler.postDelayed(this, 3000)
            }
        })
    }

    private fun display(cpu: CpuUsageSampler.Sample, memory: ActivityManager.MemoryInfo, total: Long, free: Long) {
        val locale = Locale.getDefault()
        labels[0].text = if (cpu.system) "CPU" else "Launcher CPU"
        values[0].text = if (cpu.ready()) String.format(locale, "%.1f%%", cpu.percent) else "Sampling…"
        values[0].contentDescription = if (cpu.system) "Total CPU usage" else "Launcher process usage as a share of CPU capacity"
        bars[0].progress = if (cpu.ready()) Math.round(cpu.percent).toInt() else 0
        val usedMemory = memory.totalMem - memory.availMem
        val ram = GlassOsMetrics.percent(usedMemory, memory.totalMem)
        values[1].text = if (memory.totalMem <= 0) "Unavailable"
            else String.format(locale, "%d%% • %.1f / %.1f GB", ram, gb(usedMemory), gb(memory.totalMem))
        bars[1].progress = ram
        val usedDisk = GlassOsMetrics.percent(total - free, total)
        values[2].text = if (total <= 0) "Unavailable" else String.format(locale, "%d%% • %.1f GB free", usedDisk, gb(free))
        bars[2].progress = usedDisk
    }

    fun stop() {
        val handler = sampling
        sampling = null
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        ui.removeCallbacksAndMessages(null)
    }

    private fun gb(bytes: Long): Double = bytes / 1073741824.0
}
