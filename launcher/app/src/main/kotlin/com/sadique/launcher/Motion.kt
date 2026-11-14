package com.sadique.launcher

import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
object Motion {

    /** The longest any reduced-motion animation runs, in milliseconds. */
    const val REDUCED_MS: Int = 90
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    const val REDUCED_STIFFNESS: Float = 15000f
    /** The spring stops once it is within this many pixels of its target. */
    const val REDUCED_MIN_CHANGE: Float = 2f

    @JvmStatic
    fun reduce(spring: SpringAnimation) {
        spring.spring = SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE)
    }
}
