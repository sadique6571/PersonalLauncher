package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;

/** Remote-friendly local profile and saved-title pages. */
final class HomeLibraryUi {
    private HomeLibraryUi() { }
    static void profiles(Activity host, Runnable changed, Runnable editCurrent) {
        HomeLibraryStore store = new HomeLibraryStore(host);
        LinearLayout rows = rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_profiles), rows);
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_edit_profile), null, R.drawable.ic_person,
                GlassSettings.themeAccent(host), () -> { panel.dismiss(); editCurrent.run(); }));
        for (String id : store.profileIds()) {
            android.view.View row = GlassSettings.row(host, store.profileName(id), id.equals(store.activeId()) ? host.getString(R.string.h_active) : host.getString(R.string.h_switch),
                    R.drawable.ic_person, GlassSettings.themeAccent(host), () -> { store.switchTo(id); panel.dismiss(); changed.run(); });
            if (!id.equals("default")) row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(host).setMessage(R.string.h_delete_profile)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            store.deleteProfile(id);
                            for (String asset : new String[]{"personal-avatar.png", "personal-wallpaper.jpg", "favorite_wallpaper.jpg"})
                                new java.io.File(host.getFilesDir(), "profile_" + id + "_" + asset).delete();
                            panel.dismiss(); changed.run();
                        }).setNegativeButton(android.R.string.cancel, null).show(); return true;
            });
            rows.addView(row);
        }
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_add_profile), host.getString(R.string.h_profiles_hint), R.drawable.ic_person,
                GlassSettings.themeAccent(host), () -> input(host, R.string.h_add_profile, "", value -> {
                    if (store.createProfile(value)) { panel.dismiss(); changed.run(); }
                    else Toast.makeText(host, R.string.h_profile_limit, Toast.LENGTH_LONG).show();
                })));
        panel.show();
    }
    static void category(Activity host, String pkg, Runnable changed) {
        HomeLibraryStore store = new HomeLibraryStore(host);
        List<String> categories = store.categories();
        categories.add(0, host.getString(R.string.h_no_category)); categories.add(host.getString(R.string.h_new_category));
        new AlertDialog.Builder(host).setTitle(R.string.h_category).setItems(categories.toArray(new String[0]), (d, which) -> {
            if (which == categories.size() - 1) input(host, R.string.h_new_category, "", value -> { store.category(pkg, value); changed.run(); });
            else { store.category(pkg, which == 0 ? "" : categories.get(which)); changed.run(); }
        }).show();
    }
    static void watchlist(Activity host) {
        HomeLibraryStore store = new HomeLibraryStore(host);
        LinearLayout rows = rows(host);
        AlertDialog panel = GlassSettings.panel(host, host.getString(R.string.h_watchlist), rows);
        rows.addView(GlassSettings.row(host, host.getString(R.string.h_add_title), host.getString(R.string.h_watchlist_hint), R.drawable.ic_picture,
                GlassSettings.themeAccent(host), () -> {
                    LinearLayout form = rows(host);
                    EditText title = new EditText(host); title.setHint(R.string.h_title); title.setSingleLine(); form.addView(title);
                    EditText link = new EditText(host); link.setHint(R.string.h_link); link.setSingleLine(); link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); form.addView(link);
                    AlertDialog add = new AlertDialog.Builder(host).setTitle(R.string.h_add_title).setView(form)
                            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create();
                    add.setOnShowListener(d -> add.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        if (store.saveTitle(title.getText().toString(), link.getText().toString().trim(), "", false)) { add.dismiss(); panel.dismiss(); watchlist(host); }
                        else link.setError(host.getString(R.string.h_valid_link));
                    })); add.show();
                }));
        JSONArray list = store.watchlist();
        if (list.length() == 0) rows.addView(GlassSettings.label(host, host.getString(R.string.h_watchlist_empty), 14, 0xffb5cadd));
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i); if (item == null) continue;
            android.view.View row = GlassSettings.row(host, item.optString("title"), host.getString(R.string.h_hold_remove), R.drawable.ic_picture,
                    GlassSettings.themeAccent(host), () -> openSaved(host, item));
            row.setOnLongClickListener(v -> { store.removeTitle(item.optString("uri")); panel.dismiss(); watchlist(host); return true; }); rows.addView(row);
        }
        panel.show();
    }
    private static void openSaved(Activity host, JSONObject item) {
        try {
            String uri = item.optString("uri"), owner = item.optString("owner");
            Intent intent;
            if (item.optBoolean("shared") && !owner.isEmpty()) {
                intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME); intent.setSelector(null);
                if (intent.getComponent() != null && !owner.equals(intent.getComponent().getPackageName())) intent.setComponent(null);
                intent.setPackage(owner); intent.setAction(Intent.ACTION_VIEW);
            } else {
                if (!HomeLibraryModel.allowedLink(uri)) throw new IllegalArgumentException();
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); host.startActivity(intent);
        } catch (Exception unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
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
