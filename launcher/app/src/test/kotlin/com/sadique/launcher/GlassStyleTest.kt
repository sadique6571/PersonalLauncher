package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** glass_style from config.xml or a theme: rim or halo by name, glass for anything else. */
class GlassStyleTest {

    @Test
    fun rimByNameAnyCase() {
        assertEquals(Config.GLASS_RIM, Config.glassStyle("rim"))
        assertEquals(Config.GLASS_RIM, Config.glassStyle(" Rim "))
    }

    @Test
    fun haloByNameAnyCase() {
        assertEquals(Config.GLASS_HALO, Config.glassStyle("halo"))
        assertEquals(Config.GLASS_HALO, Config.glassStyle("HALO "))
    }

    @Test
    fun anythingElseIsGlass() {
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle("glass"))
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle(""))
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle(null))
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle("frosted"))
    }
}
