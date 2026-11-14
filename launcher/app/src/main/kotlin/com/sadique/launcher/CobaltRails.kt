package com.sadique.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Thin connected rails behind visible app rows; no animation or blur layer. */
internal class CobaltRails(density: Float) : RecyclerView.ItemDecoration() {
    private val ink = Paint(3)
    private val unit: Float = density

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var lastRow = -1
        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val columns = layoutManager.spanCount
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos < 0 || child.tag == null || pos / columns == lastRow) continue
            lastRow = pos / columns
            var y = child.bottom - unit * 12
            if (child is ViewGroup && child.childCount > 0) {
                val tile = child.getChildAt(0)
                y = child.top + tile.top + tile.height - DemoGeometry.footer(ThemeStyle.COBALT, unit)
            }
            ink.color = 0x553379ff.toInt()
            ink.strokeWidth = unit * 4
            c.drawLine(0f, y, parent.width.toFloat(), y, ink)
            ink.color = 0xff4b8aff.toInt()
            ink.strokeWidth = unit
            c.drawLine(0f, y, parent.width.toFloat(), y, ink)
        }
    }
}
