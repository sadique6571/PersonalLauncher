package androidx.dynamicanimation.animation

/**
 * Test helper in the library's own package, for the package-private stepping SpringAnimation does on each frame:
 * counts the frames a spring needs to come to rest after a move, with the threshold SpringAnimation derives from
 * setMinimumVisibleChange.
 */
object SpringFrames {

    @JvmStatic
    fun toSettle(stiffness: Float, dampingRatio: Float, minimumVisibleChange: Float, distancePx: Float): Int {
        val spring = SpringForce(0f).setStiffness(stiffness).setDampingRatio(dampingRatio)
        spring.setValueThreshold(minimumVisibleChange * 0.75)   // DynamicAnimation.THRESHOLD_MULTIPLIER
        var value = -distancePx.toDouble()
        var velocity = 0.0
        for (frame in 1..120) {
            val s = spring.updateValues(value, velocity, if (frame % 3 == 0) 16 else 17)   // 60 Hz
            value = s.mValue.toDouble()
            velocity = s.mVelocity.toDouble()
            if (spring.isAtEquilibrium(value.toFloat(), velocity.toFloat())) return frame
        }
        return Int.MAX_VALUE
    }
}
