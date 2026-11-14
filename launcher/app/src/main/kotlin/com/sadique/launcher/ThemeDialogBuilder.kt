package com.sadique.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

/** Applies the selected theme to launcher-owned confirmations. */
class ThemeDialogBuilder : AlertDialog.Builder {
    private val owner: Context

    constructor(context: Context) : super(context) {
        owner = context
    }

    constructor(context: Context, theme: Int) : super(context, theme) {
        owner = context
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        if (owner is Activity && ThemeStyle.custom(ThemePreferences.get(owner))) {
            GlassSettings.styleDialog(owner, dialog)
        }
        return dialog
    }
}
