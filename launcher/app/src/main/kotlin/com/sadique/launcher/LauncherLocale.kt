package com.sadique.launcher

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Uses English for launcher UI and its date and number formatting. */
object LauncherLocale {
    @JvmStatic
    fun wrap(base: Context): Context {
        val english = Locale.ENGLISH
        Locale.setDefault(english)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(english)
        return base.createConfigurationContext(configuration)
    }
}
