package com.sadique.launcher

import android.content.Intent
import android.net.Uri
import java.util.function.BiFunction

/** Keeps the ordinary launch when a package-scoped override cannot resolve. */
object LaunchActions {
    const val VIEW_PREFIX = "view:"
    private const val INPUT_PREFIX = "input:"

    @JvmStatic
    fun <T> select(pkg: String, actions: Map<String, String>, fallback: T, resolver: BiFunction<String, String, T>): T {
        val action = actions[pkg] ?: return fallback
        return resolver.apply(pkg, action) ?: fallback
    }

    @JvmStatic
    fun inputId(action: String?): String? = valueWithPrefix(action, INPUT_PREFIX)

    @JvmStatic
    fun viewUri(value: String?): String? = valueWithPrefix(value, VIEW_PREFIX)

    private fun valueWithPrefix(value: String?, prefix: String): String? {
        if (value == null || !value.startsWith(prefix)) return null
        return value.substring(prefix.length).trim { it <= ' ' }.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun intent(pkg: String, value: String?): Intent {
        val uri = viewUri(value)
        val intent = if (uri != null) Intent(Intent.ACTION_VIEW, Uri.parse(uri)) else Intent(value)
        return intent.setPackage(pkg)
    }
}
