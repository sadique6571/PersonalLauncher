package com.sadique.launcher

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.ArrayList
import java.util.Collections

/**
 * Inputs on Coocaa OS (Skyworth) TVs, which register nothing with TvInputManager. com.skyworth.tv lists
 * the sources its own Source panel shows (HDMI 1, HDMI 2, RF, AV), and switches to one and brings up its
 * player, as picking it in that panel does.
 */
object CoocaaSources {
    private val LIST = Uri.parse("content://com.skyworth.tv.provider/system/source_list")
    private val METHOD = Uri.parse("content://com.skyworth.tv.sdk.provider/method")
    private val COLUMNS = arrayOf("sourceName", "sourceIndex", "displayName")

    class Source(
        /** HDMI, AV, VOD (the RF tuner), ... */
        @JvmField val name: String,
        @JvmField val index: Int,
        @JvmField val label: String
    )

    /** HDMI ports in port order, then AV, then the tuner; empty where the provider is missing. */
    @JvmStatic
    fun list(ctx: Context): List<Source> {
        val out = ArrayList<Source>()
        try {
            ctx.contentResolver.query(LIST, COLUMNS, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    val label = c.getString(2)
                    if (name == null) continue
                    out.add(Source(name, c.getInt(1), if (label.isNullOrEmpty()) name else label))
                }
            }
        } catch (e: RuntimeException) {
            return Collections.emptyList()
        }
        out.sortWith(Comparator { a, b ->
            if (a.name == b.name) a.index.compareTo(b.index)
            else rank(a.name).compareTo(rank(b.name))
        })
        return out
    }

    private fun rank(name: String): Int {
        return when (name) {
            "HDMI" -> 0
            "AV" -> 1
            "VOD" -> 3
            else -> 2
        }
    }

    @JvmStatic
    fun open(ctx: Context, source: Source): Boolean {
        val extras = Bundle().apply {
            putString("name", source.name)
            putInt("index", source.index)
        }
        return try {
            val ret = ctx.contentResolver.call(METHOD, "METHOD_GOTO_SOURCE", null, extras)
            ret != null && ret.getBoolean("ret")
        } catch (e: RuntimeException) {
            false
        }
    }
}
