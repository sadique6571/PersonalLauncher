package com.sadique.launcher

import android.graphics.Path

internal object DemoPaths {
    @JvmStatic
    fun set(path: Path, style: Int, w: Float, h: Float, radius: Float) {
        path.reset()
        for (cmd in DemoGeometry.outline(style, w, h, radius)) {
            when (cmd[0].toInt()) {
                0 -> path.moveTo(cmd[1], cmd[2])
                1 -> path.lineTo(cmd[1], cmd[2])
                2 -> path.quadTo(cmd[1], cmd[2], cmd[3], cmd[4])
                else -> path.close()
            }
        }
    }
}
