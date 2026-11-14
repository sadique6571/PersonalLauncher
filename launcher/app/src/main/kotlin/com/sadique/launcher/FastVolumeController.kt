package com.sadique.launcher

/** Timer/state logic shared by the shell injector and plain JVM regression tests. */
internal class FastVolumeController @JvmOverloads constructor(
    private val clock: Clock,
    private val timer: Timer,
    private val injector: Injector,
    private val errors: Errors,
    private val repeatMs: Long = REPEAT_MS,
    private val pressed: Runnable = Runnable {}
) {
    private var pending: Cancel? = null
    private var generation = 0L
    private var deadline = 0L
    private var held = 0

    init {
        require(!(repeatMs < 50 || repeatMs > 1000)) { "Repeat wait must be 50..1000 ms" }
    }

    fun interface Clock {
        fun now(): Long
    }

    fun interface Cancel {
        fun cancel()
    }

    fun interface Timer {
        fun post(task: Runnable, delay: Long): Cancel
    }

    fun interface Injector {
        @Throws(Exception::class)
        fun pair(keyCode: Int)
    }

    fun interface Errors {
        fun failed(error: Exception)
    }

    @Synchronized
    fun down(key: Int) {
        if ((key != 24 && key != 25 && key != 164) || held == key) return
        stop()
        held = key
        deadline = clock.now() + MAX_HOLD_MS
        pressed.run() // Show the panel on physical DOWN, before the hold timer or volume broadcast.
        if (key == 164) return // Native mute tap passes through; never synthesize held mute toggles.
        val press = generation
        pending = timer.post({ tick(press) }, HOLD_MS)
    }

    @Synchronized
    fun up(key: Int) {
        if (held == key) stop()
    }

    @Synchronized
    fun unknownUp() {
        stop()
    }

    @Synchronized
    fun stop() {
        held = 0
        generation++
        pending?.cancel()
        pending = null
    }

    @Synchronized
    private fun tick(press: Long) {
        if (held == 0 || generation != press) return
        if (clock.now() >= deadline) {
            stop()
            return
        }
        try {
            injector.pair(held)
        } catch (failure: Exception) {
            stop()
            errors.failed(failure)
            return
        }
        if (held != 0 && generation == press) {
            pending = timer.post({ tick(press) }, repeatMs)
        }
    }

    /** Single-device getevent frames: an MSC usage is valid only until the next SYN_REPORT. */
    class Parser(private val sink: Sink) {
        interface Sink {
            fun down(key: Int)
            fun up(key: Int)
            fun unknownUp()
            fun stop()
        }

        private var scan = 0 // 0=no usage, -1=unrelated usage, otherwise mapped volume key.

        constructor(controller: FastVolumeController) : this(object : Sink {
            override fun down(key: Int) { controller.down(key) }
            override fun up(key: Int) { controller.up(key) }
            override fun unknownUp() { controller.unknownUp() }
            override fun stop() { controller.stop() }
        })

        fun line(line: String) {
            val words = line.trim { it <= ' ' }.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var i = 0
            while (i + 2 < words.size) {
                val type = words[i]
                val code = words[i + 1]
                val value = words[i + 2]
                if ("EV_MSC" == type && "MSC_SCAN" == code) {
                    scan = when {
                        "000c0080".equals(value, ignoreCase = true) -> 24
                        "000c0081".equals(value, ignoreCase = true) -> 25
                        "000c00e2".equals(value, ignoreCase = true) -> 164
                        else -> -1
                    }
                    return
                }
                if ("EV_SYN" == type && "SYN_REPORT" == code) {
                    scan = 0
                    return
                }
                if ("EV_KEY" != type) {
                    i++
                    continue
                }
                val key = when (code) {
                    "KEY_VOLUMEUP" -> 24
                    "KEY_VOLUMEDOWN" -> 25
                    "KEY_MUTE", "KEY_VOLUMEMUTE" -> 164
                    "KEY_UNKNOWN" -> scan
                    else -> -1
                }
                if (key == 24 || key == 25 || key == 164) {
                    when (value) {
                        "DOWN" -> sink.down(key)
                        "UP" -> sink.up(key)
                        "REPEAT" -> sink.stop()
                    }
                } else if (key == 0 && "UP" == value) {
                    sink.unknownUp()
                } else if (key == 0 && "REPEAT" == value) {
                    sink.stop()
                }
                scan = 0
                return
            }
        }
    }

    companion object {
        const val HOLD_MS = 300L
        const val REPEAT_MS = 50L
        const val MAX_HOLD_MS = 30_000L
    }
}
