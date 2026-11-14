package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherLocaleTest {
    @Test
    fun plainTagsBecomeLocales() {
        assertEquals("vi", LauncherLocale.parse("vi")!!.language)
        assertEquals("BR", LauncherLocale.parse("pt-BR")!!.country)
        assertEquals("fil", LauncherLocale.parse(" fil ")!!.language)
    }

    @Test
    fun tagsWithScriptsAndExtensionsBecomeLocales() {
        assertEquals("Latn", LauncherLocale.parse("sr-Latn")!!.script)
        assertEquals("ar", LauncherLocale.parse("ar-EG-u-nu-latn")!!.language)
        assertEquals("latn", LauncherLocale.parse("ar-EG-u-nu-latn")!!.getUnicodeLocaleType("nu"))
    }

    @Test
    fun emptyOrMalformedTagsFollowTheTv() {
        assertNull(LauncherLocale.parse(""))
        assertNull(LauncherLocale.parse(null))
        assertNull(LauncherLocale.parse("Vietnamese"))
        assertNull(LauncherLocale.parse("vi_VN"))
        assertNull(LauncherLocale.parse("../vi"))
        assertNull(LauncherLocale.parse("vi-"))
        assertNull(LauncherLocale.parse("vi--VN"))
    }
}
