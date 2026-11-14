package com.sadique.launcher

/** Keeps one pending UI update and displays the newest signal. */
class VolumePanelUpdates(private val queue: Queue, private val display: Display) {
    fun interface Queue {
        fun post(task: Runnable)
    }

    fun interface Display {
        fun show(sentAt: Long)
    }

    private var queued = false
    private var closed = false
    private var latest = 0L

    @Synchronized
    fun offer(sentAt: Long) {
        if (closed) return
        latest = sentAt
        if (queued) return
        queued = true
        queue.post {
            val value = synchronized(this) {
                if (closed) return@post
                queued = false
                latest
            }
            display.show(value)
        }
    }

    @Synchronized
    fun close() {
        closed = true
    }

    companion object {
        @JvmStatic
        fun fresh(sentAt: Long, now: Long): Boolean = sentAt > 0 && now >= sentAt && now - sentAt <= 1000
    }
}
