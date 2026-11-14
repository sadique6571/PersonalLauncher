package com.sadique.launcher

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue

/** The typeface behind one of the font_* strings in res/values/fonts.xml, for views built in code. */
object UiFont {

    @JvmStatic
    fun get(context: Context, fontString: Int): Typeface {
        val res = context.resources
        val value = TypedValue()
        res.getValue(fontString, value, true)
        val name = value.string.toString()
        if (name.startsWith("res/")) return res.getFont(value.resourceId)
        return Typeface.create(name, Typeface.NORMAL)
    }
}
