package com.sadique.launcher

import android.widget.ImageView

internal class LandscapePhotoTransition {
    private var photoEffect = 0

    fun prepare(incoming: ImageView, outgoing: ImageView, hasPhoto: Boolean, reducedMotion: Boolean, width: Int, height: Int) {
        if (hasPhoto && !reducedMotion) {
            val horizontal = width * .085f
            val vertical = height * .10f
            var scale = 1f
            when (photoEffect++ % 7) {
                1 -> outgoing.animate().scaleX(.87f).scaleY(.87f).setDuration(620).start()
                2 -> scale = .87f
                3 -> {
                    scale = .80f
                    incoming.translationX = horizontal
                }
                4 -> {
                    scale = .80f
                    incoming.translationX = -horizontal
                }
                5 -> {
                    scale = .76f
                    incoming.translationY = vertical
                }
                6 -> {
                    scale = .76f
                    incoming.translationY = -vertical
                }
                else -> {
                    // Crossfade.
                }
            }
            incoming.scaleX = scale
            incoming.scaleY = scale
        }
    }
}
