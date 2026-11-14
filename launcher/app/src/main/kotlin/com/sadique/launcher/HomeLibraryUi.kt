package com.sadique.launcher

import android.app.Activity
import android.text.InputFilter
import android.widget.EditText
import android.widget.LinearLayout

/** Shared panel helpers and app category editing. */
object HomeLibraryUi {
    fun interface TextAction {
        fun accept(text: String)
    }

    @JvmStatic
    fun category(host: Activity, pkg: String?, changed: Runnable) {
        val store = HomeLibraryStore(host)
        val categories = store.categories()
        categories.add(0, host.getString(R.string.h_no_category))
        categories.add(host.getString(R.string.h_new_category))
        ThemeDialogBuilder(host).setTitle(R.string.h_category).setItems(categories.toTypedArray()) { _, which ->
            if (which == categories.lastIndex) {
                input(host, R.string.h_new_category, "") { value ->
                    store.category(pkg, value)
                    changed.run()
                }
            } else {
                store.category(pkg, if (which == 0) "" else categories[which])
                changed.run()
            }
        }.show()
    }

    @JvmStatic
    fun rows(host: Activity): LinearLayout {
        return LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            val pad = GlassSettings.dp(host, 6f)
            setPadding(pad, pad, pad, pad)
        }
    }

    @JvmStatic
    fun input(host: Activity, title: Int, value: String?, action: TextAction) {
        val edit = EditText(host).apply {
            setSingleLine()
            setText(value)
            setSelectAllOnFocus(true)
            filters = arrayOf(InputFilter.LengthFilter(80))
        }
        ThemeDialogBuilder(host).setTitle(title).setView(edit)
            .setPositiveButton(android.R.string.ok) { _, _ -> action.accept(edit.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
