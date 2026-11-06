package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

/** Applies the selected material to launcher-owned confirmations without changing their callbacks. */
final class ThemeDialogBuilder extends AlertDialog.Builder {
    private final Context owner;
    ThemeDialogBuilder(Context context) { super(context); owner=context; }
    ThemeDialogBuilder(Context context,int theme) { super(context,theme); owner=context; }
    @Override public AlertDialog show() {
        AlertDialog dialog=super.show();
        if(owner instanceof Activity && ThemeStyle.custom(ThemePreferences.get(owner)))
            GlassSettings.styleDialog((Activity)owner,dialog);
        return dialog;
    }
}
