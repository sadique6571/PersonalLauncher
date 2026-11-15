package com.sadique.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The festive skin from the theme (see Festival): the bell, diya and marigold pill icons while it is
 * active today, and the petal shower that greets the first Home of each day.
 */
class MainFestiveSkin(
    private val context: Context,
    private val root: FrameLayout,
    private val density: Float,
    private val reducedMotion: Boolean,
    /** False once Home is finishing or destroyed; the petal shower is posted, so it checks before playing. */
    private val isAlive: () -> Boolean,
) {
    /** The diya on the network pill, whose flame follows the connection; started and stopped with Home. */
    var diya: DiyaDrawable? = null
        private set
    private var bell: BellDrawable? = null
    private var marigold: MarigoldDrawable? = null

    /**
     * Swaps the pill icons for the festival's: a bell for Inputs that swings when focused, a diya for the
     * network whose flame follows the connection, and a marigold for Settings that turns when focused.
     */
    fun apply(inputs: TextView, wifi: TextView, settings: TextView) {
        val b = BellDrawable(density)
        val m = MarigoldDrawable(density)
        val d = DiyaDrawable(density)
        bell = b
        marigold = m
        diya = d
        b.setReducedMotion(reducedMotion)
        m.setReducedMotion(reducedMotion)
        d.setReducedMotion(reducedMotion)
        setFestiveIcon(inputs, b)
        setFestiveIcon(wifi, d)
        setFestiveIcon(settings, m)
        addFocusAction(inputs, b::ring)
        addFocusAction(settings, m::turn)
    }

    fun playPetalShower() {
        if (!isAlive()) return
        val shower = PetalShowerView(context)
        root.addView(shower, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shower.play(60, 2200) { root.removeView(shower) }
    }

    private fun setFestiveIcon(pill: TextView, icon: Drawable) {
        // The pills tint their icons to follow focus; the festive ones keep their own colours.
        pill.compoundDrawableTintList = null
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }

    /** Runs [action] when the view gains focus, keeping any focus listener it already had. */
    private fun addFocusAction(view: View, action: Runnable) {
        val previous = view.onFocusChangeListener
        view.setOnFocusChangeListener { v, focused ->
            previous?.onFocusChange(v, focused)
            if (focused) action.run()
        }
    }
}
