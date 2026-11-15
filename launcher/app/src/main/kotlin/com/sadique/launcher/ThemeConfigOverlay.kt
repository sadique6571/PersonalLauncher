package com.sadique.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Reads a theme file and retains a local copy for startup before external storage mounts. */
internal object ThemeConfigOverlay {
    private const val TAG = "TvHome.Config"

    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay.
     */
    fun readOverlay(ctx: Context, fileName: String, file: File?, mounted: Boolean): JSONObject? {
        val copy = File(ctx.filesDir, fileName)
        if (!mounted) return read(copy)
        val json = read(file)
        if (json == null) {
            copy.delete()
        } else {
            try {
                FileOutputStream(copy).use { out ->
                    out.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                }
            } catch (e: IOException) {
                Log.w(TAG, "could not keep a copy of $file: ${e.message}")
            }
        }
        return json
    }

    private fun read(file: File?): JSONObject? {
        if (file == null || !file.isFile) return null
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring $file: larger than 64 KB")
            return null
        }
        try {
            FileInputStream(file).use { input ->
                val buf = ByteArray(file.length().toInt())
                var n = 0
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r <= 0) break
                    n += r
                }
                var text = String(buf, 0, n, StandardCharsets.UTF_8)
                if (text.startsWith("\uFEFF")) text = text.substring(1)   // Windows editors add a BOM
                return JSONObject(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ignoring $file: ${e.message}")
            return null
        }
    }
}
