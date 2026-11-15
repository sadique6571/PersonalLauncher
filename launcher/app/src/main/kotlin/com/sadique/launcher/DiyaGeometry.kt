package com.sadique.launcher

import android.graphics.Path

internal object DiyaGeometry {
    fun buildLamp(body: Path, opening: Path, rimLip: Path, sheen: Path) {
        body.moveTo(1.6f, 14.2f)
        body.cubicTo(1.8f, 18.6f, 5.4f, 21.0f, 9.6f, 21.0f)
        body.cubicTo(13.2f, 21.0f, 15.6f, 19.4f, 16.8f, 16.6f)
        body.cubicTo(17.6f, 15.0f, 18.6f, 13.6f, 20.2f, 12.4f)
        body.quadTo(20.5f, 12.0f, 19.9f, 11.9f)
        body.cubicTo(18.2f, 12.1f, 16.8f, 12.6f, 15.4f, 13.0f)
        body.cubicTo(12.0f, 11.6f, 5.0f, 11.8f, 1.6f, 14.2f)
        body.close()

        opening.moveTo(2.6f, 14.2f)
        opening.cubicTo(5.6f, 12.6f, 11.8f, 12.5f, 15.2f, 13.4f)
        opening.cubicTo(16.6f, 13.0f, 17.8f, 12.7f, 19.2f, 12.4f)
        opening.cubicTo(17.6f, 13.4f, 16.6f, 14.0f, 15.4f, 14.6f)
        opening.cubicTo(11.6f, 16.4f, 5.0f, 16.2f, 2.6f, 14.2f)
        opening.close()

        rimLip.moveTo(2.7f, 14.65f)
        rimLip.cubicTo(5.1f, 16.65f, 11.6f, 16.85f, 15.5f, 15.0f)
        rimLip.cubicTo(16.7f, 14.4f, 17.7f, 13.8f, 19.4f, 12.75f)

        sheen.moveTo(3.2f, 15.4f)
        sheen.quadTo(4.0f, 18.4f, 6.9f, 19.7f)
    }

    /** A candle-flame teardrop standing on (bx, by): round bulb low down, pointed tip leaning by {@code lean}. */
    fun teardrop(p: Path, bx: Float, by: Float, h: Float, w: Float, lean: Float) {
        val tx = bx + lean
        val ty = by - h
        val ml = lean * 0.35f
        val wy = by - Math.max(w * 1.1f, h * 0.24f)
        val up = (wy - ty) * 0.35f
        val down = (by - wy) * 0.55f
        p.reset()
        p.moveTo(tx, ty)
        p.cubicTo(tx + w * 0.25f, ty + h * 0.3f, bx + ml + w, wy - up, bx + w, wy)
        p.cubicTo(bx + w, wy + down, bx + w * 0.55f, by, bx, by)
        p.cubicTo(bx - w * 0.55f, by, bx - w, wy + down, bx - w, wy)
        p.cubicTo(bx + ml - w, wy - up, tx - w * 0.25f, ty + h * 0.3f, tx, ty)
        p.close()
    }
}
