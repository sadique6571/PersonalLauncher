package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.List;

/** Shared panel helpers and app category editing. */
final class HomeLibraryUi {
    private HomeLibraryUi() { }
    static void category(Activity host, String pkg, Runnable changed) {
        HomeLibraryStore store = new HomeLibraryStore(host);
        List<String> categories = store.categories();
        categories.add(0, host.getString(R.string.h_no_category)); categories.add(host.getString(R.string.h_new_category));
        new AlertDialog.Builder(host).setTitle(R.string.h_category).setItems(categories.toArray(new String[0]), (d, which) -> {
            if (which == categories.size() - 1) input(host, R.string.h_new_category, "", value -> { store.category(pkg, value); changed.run(); });
            else { store.category(pkg, which == 0 ? "" : categories.get(which)); changed.run(); }
        }).show();
    }
    static LinearLayout rows(Activity host) {
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        int pad = GlassSettings.dp(host, 6); rows.setPadding(pad, pad, pad, pad); return rows;
    }
    interface TextAction { void accept(String text); }
    static void input(Activity host, int title, String value, TextAction action) {
        EditText edit = new EditText(host); edit.setSingleLine(); edit.setText(value); edit.setSelectAllOnFocus(true);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new AlertDialog.Builder(host).setTitle(title).setView(edit).setPositiveButton(android.R.string.ok,
                (d, which) -> action.accept(edit.getText().toString())).setNegativeButton(android.R.string.cancel, null).show();
    }
}
