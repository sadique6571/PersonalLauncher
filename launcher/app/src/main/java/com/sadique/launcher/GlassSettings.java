package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared remote-friendly glass cards for settings and every launcher option page. */
final class GlassSettings {
    static int dp(Activity host, float n) { return Math.round(n * host.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(Activity host, boolean focused) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{focused ? 0xe51a374b : 0xc5132539, focused ? 0xed0b2337 : 0xd009182a});
        shape.setCornerRadius(dp(host, 10));
        shape.setStroke(dp(host, focused ? 2 : 1), focused ? 0xff27d5ff : 0x55415a72);
        return shape;
    }
    static TextView label(Activity host, String text, int size, int color) {
        TextView view = new TextView(host); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    static View row(Activity host, String title, String subtitle, int icon, int tint, Runnable action) {
        LinearLayout row = new LinearLayout(host); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(host, 12), dp(host, 7), dp(host, 12), dp(host, 7));
        row.setMinimumHeight(dp(host, 49)); row.setFocusable(true); row.setClickable(true);
        row.setBackground(background(host, false));
        row.setOnFocusChangeListener((v, focus) -> row.setBackground(background(host, focus)));
        ImageView symbol = new ImageView(host); symbol.setImageResource(icon); symbol.setColorFilter(tint);
        row.addView(symbol, new LinearLayout.LayoutParams(dp(host, 24), dp(host, 24)));
        LinearLayout copy = new LinearLayout(host); copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(host, 13), 0, dp(host, 6), 0);
        TextView heading = label(host, title, 14, Color.WHITE); copy.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(label(host, subtitle, 11, 0xffa9bfd3));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label(host, "›", 26, 0xff9bb6cb); row.addView(arrow);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.setMargins(0, dp(host, 3), 0, dp(host, 3)); row.setLayoutParams(position);
        return row;
    }
    static AlertDialog panel(Activity host, String title, LinearLayout rows) {
        LinearLayout content = new LinearLayout(host); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(host, 12), dp(host, 12), dp(host, 12), dp(host, 12));
        LinearLayout bar = new LinearLayout(host); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(host, title, 21, Color.WHITE); heading.setTypeface(null, Typeface.BOLD);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = label(host, "×", 26, 0xffc9dfef); close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close settings"); close.setFocusable(true); close.setBackground(background(host, false));
        close.setOnFocusChangeListener((v, f) -> close.setBackground(background(host, f)));
        bar.addView(close, new LinearLayout.LayoutParams(dp(host, 36), dp(host, 36)));
        content.addView(bar);
        ScrollView scroll = new ScrollView(host); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        AlertDialog dialog = new AlertDialog.Builder(host).setView(content).create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() == null) return;
            boolean tv = (host.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
            dialog.getWindow().setGravity(tv ? Gravity.END : Gravity.CENTER);
            int width = Math.min(dp(host, 350), host.getResources().getDisplayMetrics().widthPixels - dp(host, 24));
            dialog.getWindow().setLayout(width, tv ? -1 : Math.round(host.getResources().getDisplayMetrics().heightPixels * .88f));
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(.12f);
            for (int i = 0; i < rows.getChildCount(); i++) if (rows.getChildAt(i).isFocusable()) { rows.getChildAt(i).requestFocus(); break; }
        });
        return dialog;
    }
    static void options(Activity host, String title, String[] names, String[] hints, java.util.function.IntConsumer selected) {
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = panel(host, title, rows);
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            rows.addView(row(host, names[i], hints == null ? null : hints[i], R.drawable.ic_settings, 0xff9ad6ed,
                    () -> { dialog.dismiss(); selected.accept(index); }));
        }
        dialog.show();
    }
}
