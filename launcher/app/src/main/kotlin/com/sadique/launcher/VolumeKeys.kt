package com.sadique.launcher

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.util.function.IntSupplier

/** Shared held-volume controller for the remote helpers and launcher slider. */
object VolumeKeys {
    const val REPEAT_DELAY_MS = 500L
    const val REPEAT_INTERVAL_MS = 60L
    const val MAX_HOLD_MS = 30000L
    private var repeater: Repeater? = null

    @JvmStatic
    fun direction(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        else -> 0
    }

    @JvmStatic
    fun changesPress(action: Int, repeatCount: Int): Boolean = action != KeyEvent.ACTION_DOWN || repeatCount == 0

    @JvmStatic
    fun handle(context: Context, event: KeyEvent): Boolean {
        if (!canHandle(event)) return false
        process(context, event, false)
        return true
    }

    @JvmStatic
    fun control(context: Context, event: KeyEvent): Boolean {
        if (!canHandle(event)) return false
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio == null || audio.isVolumeFixed) return false
        process(context, event, true)
        return true
    }

    private fun canHandle(event: KeyEvent): Boolean {
        return direction(event.keyCode) != 0 && !VolumePanelBinder.ownsMediaKeys() &&
            !(event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD && event.scanCode == 0)
    }

    private fun process(context: Context, event: KeyEvent, initialStep: Boolean) {
        Log.d("PersonalVolume", "${context.javaClass.simpleName} key=${event.keyCode} scan=${event.scanCode}" +
            " action=${event.action} repeat=${event.repeatCount} initialStep=$initialStep")
        val controller = repeater ?: run {
            val handler = Handler(Looper.getMainLooper())
            Repeater(object : Timer {
                override fun post(task: Runnable, delay: Long) { handler.postDelayed(task, delay) }
                override fun cancel(task: Runnable) { handler.removeCallbacks(task) }
            }).also { repeater = it }
        }
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio == null || audio.isVolumeFixed) {
            stop()
            return
        }
        controller.event(event.deviceId, event.keyCode, event.downTime, event.eventTime, event.action,
            event.repeatCount, event.isCanceled, true, initialStep, {
                val flags = if (initialStep) 0 else AudioManager.FLAG_SHOW_UI
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction(event.keyCode), flags)
                if (initialStep && !VolumeSlider.show(context, audio)) {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                }
            }, { audio.getStreamVolume(AudioManager.STREAM_MUSIC) })
    }

    @JvmStatic fun stop() { repeater?.stop() }

    interface Timer {
        fun post(task: Runnable, delay: Long)
        fun cancel(task: Runnable)
    }

    /** Tracks one press across the two accessibility services. */
    class Repeater(private val timer: Timer) {
        private var device = 0
        private var key = 0
        private var down = 0L
        private var active = false
        private var lastVolume = 0
        private var tick: Runnable? = null
        private var limit: Runnable? = null

        fun event(device: Int, key: Int, down: Long, time: Long, action: Int, repeat: Int,
            canceled: Boolean, consume: Boolean, adjust: Runnable, readVolume: IntSupplier) {
            event(device, key, down, time, action, repeat, canceled, consume, false, adjust, readVolume)
        }

        fun event(device: Int, key: Int, down: Long, time: Long, action: Int, repeat: Int,
            canceled: Boolean, consume: Boolean, initialStep: Boolean, adjust: Runnable, readVolume: IntSupplier) {
            val same = active && this.device == device && this.key == key && this.down == down
            if (action == KeyEvent.ACTION_UP || canceled) {
                if (same) stop()
                return
            }
            if (action != KeyEvent.ACTION_DOWN || repeat > 0 || same) return
            stop()
            active = true
            if (initialStep) adjust.run()
            lastVolume = readVolume.asInt
            this.device = device
            this.key = key
            this.down = down
            val next = object : Runnable {
                override fun run() {
                    if (!active || tick !== this) return
                    try {
                        val currentVolume = readVolume.asInt
                        if (consume || currentVolume == lastVolume) adjust.run()
                        lastVolume = readVolume.asInt
                    } catch (_: RuntimeException) {
                        stop()
                        return
                    }
                    timer.post(this, REPEAT_INTERVAL_MS)
                }
            }
            val timeout = Runnable(::stop)
            tick = next
            limit = timeout
            timer.post(next, maxOf(0, REPEAT_DELAY_MS - maxOf(0, time - down)))
            timer.post(timeout, MAX_HOLD_MS)
        }

        fun stop() {
            active = false
            tick?.let(timer::cancel)
            limit?.let(timer::cancel)
            tick = null
            limit = null
        }
    }
}
