package com.sadique.launcher

/** Tracks ownership of the optional shell volume-controller relay. */
class VolumePanelSession<T : Any>(
    private val endpoint: Endpoint<T>,
    private val original: T,
    private val replacement: T
) : AutoCloseable {
    interface Endpoint<T> {
        @Throws(Exception::class)
        fun get(): T?

        @Throws(Exception::class)
        fun set(controller: T)
    }

    private var installed = false
    private var closed = false

    @Synchronized
    @Throws(Exception::class)
    fun install() {
        check(!closed) { "Volume panel session already closed" }
        if (installed) return
        check(original == endpoint.get()) { "Volume controller changed before registration" }
        endpoint.set(replacement)
        installed = true
    }

    @Synchronized
    @Throws(Exception::class)
    override fun close() {
        if (installed && replacement == endpoint.get()) endpoint.set(original)
        installed = false
        closed = true
    }

    companion object {
        /** Strip FLAG_SHOW_UI from media callbacks while keeping every other flag and stream. */
        @JvmStatic
        fun flags(stream: Int, flags: Int): Int = if (stream == 3) flags and 1.inv() else flags
    }
}
