package com.sadique.launcher

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * The owner's own tile order, set with the reorder button and kept in the launcher's private files (order.txt).
 */
class Order @JvmOverloads constructor(
    @JvmField val dock: String,
    keys: List<String>,
    @JvmField val docked: Int = -1
) {
    @JvmField
    val keys: List<String> = Collections.unmodifiableList(ArrayList(keys))

    companion object {
        private const val TAG = "TvHome.Order"
        const val FILE_NAME: String = "order.txt"
        private const val MAGIC = "tvhome-order 1"
        private const val DOCK = "dock "
        private const val DOCKED = "docked "

        @JvmStatic
        fun dockKey(dockApps: List<String>): String {
            return java.lang.String.join(",", dockApps)
        }

        @JvmStatic
        fun encode(order: Order): String {
            val sb = StringBuilder(MAGIC).append('\n').append(DOCK).append(order.dock).append('\n')
            if (order.docked >= 0) sb.append(DOCKED).append(order.docked).append('\n')
            for (k in order.keys) sb.append(k).append('\n')
            return sb.toString()
        }

        @JvmStatic
        fun decode(text: String?): Order? {
            if (text == null) return null
            val lines = text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (lines.size < 2 || lines[0] != MAGIC || !lines[1].startsWith(DOCK)) return null
            var docked = -1
            var first = 2
            if (lines.size > 2 && lines[2].startsWith(DOCKED)) {
                try {
                    docked = lines[2].substring(DOCKED.length).toInt()
                } catch (_: NumberFormatException) {
                    return null
                }
                if (docked < 0) return null
                first = 3
            }
            val keys = ArrayList<String>()
            for (i in first until lines.size) {
                val k = lines[i]
                if (k.isEmpty()) {
                    if (i == lines.size - 1) break
                    return null
                }
                if (keys.contains(k)) return null
                keys.add(k)
            }
            if (docked > keys.size) return null
            return Order(lines[1].substring(DOCK.length), keys, docked)
        }

        @JvmStatic
        fun ranks(order: Order?, dockApps: List<String>): Map<String, Int> {
            val r = HashMap<String, Int>()
            if (order == null || order.dock != dockKey(dockApps)) return r
            for (i in order.keys.indices) r[order.keys[i]] = i
            return r
        }

        @JvmStatic
        fun docked(order: Order?, dockApps: List<String>): Int {
            return if (order == null || order.dock != dockKey(dockApps)) -1 else order.docked
        }

        @JvmStatic
        fun compare(ranks: Map<String, Int>, keyA: String, keyB: String): Int {
            val a = ranks[keyA]
            val b = ranks[keyB]
            if (a == null && b == null) return 0
            if (a == null) return 1
            if (b == null) return -1
            return a.compareTo(b)
        }

        @JvmStatic
        fun file(ctx: Context): File {
            return File(ctx.filesDir, FILE_NAME)
        }

        @JvmStatic
        fun load(file: File): Order? {
            if (!file.exists()) return null
            return try {
                val o = decode(String(AtomicFile(file).readFully(), StandardCharsets.UTF_8))
                if (o == null) Log.w(TAG, file.name + " is not valid; ignored")
                o
            } catch (e: IOException) {
                Log.w(TAG, "cannot read " + file.name + ": " + e)
                null
            }
        }

        @JvmStatic
        fun save(file: File, order: Order): Boolean {
            val af = AtomicFile(file)
            var out: FileOutputStream? = null
            return try {
                out = af.startWrite()
                out.write(encode(order).toByteArray(StandardCharsets.UTF_8))
                af.finishWrite(out)
                true
            } catch (e: IOException) {
                Log.w(TAG, "cannot write " + file.name + ": " + e)
                if (out != null) af.failWrite(out)
                false
            }
        }
    }
}
