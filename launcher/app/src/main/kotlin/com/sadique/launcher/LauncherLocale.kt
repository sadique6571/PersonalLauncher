package com.sadique.launcher

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the configured launcher language to activity contexts. */
object LauncherLocale {
    private val tagPattern = Regex("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*")

    @JvmStatic
    fun parse(tag: String?): Locale? {
        val value = tag?.trim { it <= ' ' } ?: return null
        return if (tagPattern.matches(value)) Locale.forLanguageTag(value) else null
    }

    @JvmStatic
    fun wrap(base: Context): Context {
        val locale = parse(Config.load(base).locale) ?: return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }
}
