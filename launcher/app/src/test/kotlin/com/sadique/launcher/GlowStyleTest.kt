package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** glow_style from config.xml or a theme: horizon by name, radial for anything else. */
class GlowStyleTest {

    @Test
    fun horizonByNameAnyCase() {
        assertEquals(Config.GLOW_HORIZON, Config.glowStyle("horizon"))
        assertEquals(Config.GLOW_HORIZON, Config.glowStyle(" Horizon "))
    }

    @Test
    fun anythingElseIsRadial() {
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle("radial"))
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle(""))
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle(null))
        assertEquals(Config.GLOW_RADIAL, Config.glowStyle("aurora"))
    }
}
