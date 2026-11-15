package com.sadique.launcher

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

internal class AppTileHolder(
    v: View,
    listener: AppAdapter.TileListener?,
    animations: Boolean,
    advancedEffects: Boolean,
    reducedMotion: Boolean,
    restNameAlpha: Float,
    overshoot: Boolean
) : RecyclerView.ViewHolder(v) {
    val card: View = v.findViewById(R.id.card)

    /** item_app.xml's tile_bg: the dark card behind an icon tile, or behind a banner that has transparent parts. */
    val cardBackground: Drawable? = card.background
    val banner: ImageView = v.findViewById(R.id.banner)
    val icon: ImageView = v.findViewById(R.id.icon)
    val label: TextView = v.findViewById(R.id.label)
    val name: TextView = v.findViewById(R.id.name)
    val animations: Boolean = animations
    val advancedEffects: Boolean = advancedEffects
    val reducedMotion: Boolean = reducedMotion

    /** Alpha of the name while the tile is not focused (always_show_names). */
    val restNameAlpha: Float = restNameAlpha
    val overshoot: Boolean = overshoot

    /** A dock tile: it grows in place without advanced_effects' lift, which reads as off-centre in the band (#2135). */
    var inDock = false

    init {
        val density = v.resources.displayMetrics.density
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                outline.setRoundRect(0, 0, view.width, view.height, AppAdapter.cornerRadius(view.width, density))
            }
        }
        card.clipToOutline = true

        card.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) false
            else if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) false
            else {
                val pos = bindingAdapterPosition
                listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode)
            }
        }

        card.setOnFocusChangeListener { view, focused ->
            view.animate().cancel()
            banner.animate().cancel()
            name.animate().cancel()
            val scale = if (!animations) 1f else if (advancedEffects) 1.12f else 1.10f
            val s = if (focused) scale else 1f
            if (!animations) {
                view.scaleX = 1f
                view.scaleY = 1f
                view.translationY = 0f
                view.translationZ = 0f
                banner.scaleX = 1f
                banner.scaleY = 1f
                name.alpha = if (focused) 1f else restNameAlpha
                name.translationY = 0f
            } else if (reducedMotion) {
                // A short zoom with no hardware layer: building a layer for so short an animation costs more
                // frames than it saves. The name just appears instead of fading.
                view.animate().scaleX(s).scaleY(s)
                    .translationY(if (focused && advancedEffects && !inDock) -4f * density else 0f)
                    .translationZ(if (focused && advancedEffects) 8f * density else 0f)
                    .setDuration((if (focused) Motion.REDUCED_MS else 60).toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                name.alpha = if (focused) 1f else restNameAlpha
                // Same place as the full motion's slide ends: the layout's -6dp is only its starting point, and
                // left there the name overlaps the zoomed tile.
                // Only on focus: snapping it back to -6dp while it fades out would jump it up over the tile.
                if (focused) name.translationY = density * 4f
            } else {
                view.animate().scaleX(s).scaleY(s)
                    .translationY(if (focused && advancedEffects && !inDock) -4f * density else 0f)
                    .translationZ(if (focused && advancedEffects) 8f * density else 0f)
                    .setDuration((if (advancedEffects) (if (focused) 220 else 170) else (if (focused) 260 else 180)).toLong())
                    .setInterpolator(if (focused && !overshoot) DecelerateInterpolator(1.5f)
                        else OvershootInterpolator(if (focused) (if (advancedEffects) 0.7f else 1.6f) else 0f))
                    .withLayer().start()
                if (advancedEffects) {
                    banner.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f)
                        .setDuration(280).start()
                }
                name.animate().alpha(if (focused) 1f else restNameAlpha)
                    .translationY(if (focused) density * 4f else -density * 6f)
                    .setDuration(220).start()
            }
            val pos = bindingAdapterPosition
            if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                listener.onTileFocused(pos, view)
            }
        }
    }
}
