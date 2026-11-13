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
    static void dismiss(Activity host) {
        java.lang.ref.WeakReference<Panel> ref=PANELS.get(host);
        Panel panel=ref==null?null:ref.get(); if(panel!=null) panel.dialog.dismiss();
    }
    private static void bindSymbol(ImageView view, Activity host, int resource) {
        view.setTag(R.id.theme_symbol_resource, resource);
        view.setImageDrawable(TvSymbols.icon(host, resource));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }
    static boolean showing(Activity host) {
        java.lang.ref.WeakReference<Panel> ref = PANELS.get(host);
        Panel panel = ref == null ? null : ref.get();
        return panel != null && panel.dialog.isShowing();
    }
    static int dp(Activity host, float n) { return Math.round(n * host.getResources().getDisplayMetrics().density); }
    /** Theme accent color, mirrored from the launcher's theme picker. */
    static int themeAccent(android.content.Context ctx) {
        android.content.SharedPreferences prefs = ThemePreferences.get(ctx);
        if(ThemeStyle.custom(prefs)||ThemeStyle.harmony(prefs)) return ThemeStyle.focusAccent(prefs);
        switch (prefs.getInt("theme_name", 0)) {
            case 1: return 0xffff9a5c; // Sunset Orange
            case 2: return 0xffb388ff; // Deep Purple
            case 3: return TvGlassStyle.readable(prefs.getInt("custom_accent", 0xff40d9ff));
            default: return 0xff40d9ff; // Midnight Cyan
        }
    }

    static GradientDrawable background(Activity host, boolean focused) {        java.util.Calendar now = java.util.Calendar.getInstance();
        android.content.SharedPreferences prefs = ThemePreferences.get(host);
        if(ThemeStyle.harmony(prefs))return HarmonyVisionSurface.control(host,focused);
        if (TvGlassStyle.enabled(prefs)) return TvGlassStyle.surface(dp(host, 18), dp(host, focused ? 2 : 1), focused, prefs);
        float day = HomeAppearance.daylight(prefs.getString("theme_mode", "dark"),
                now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE));
        boolean light = day >= .5f;
        // Theme-tinted glass (user 2026-11-01): the row background takes the theme's hue.
        int accent = themeAccent(host);
        int[] base = light ? new int[]{focused ? 0xf04c7b99 : 0xed3b607d, focused ? 0xf0375d7b : 0xed28475f}
                : new int[]{focused ? 0xe51a374b : 0xc5132539, focused ? 0xed0b2337 : 0xd009182a};
        int[] tinted = new int[]{tint(base[0], accent), tint(base[1], accent)};
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, tinted);
        shape.setCornerRadius(dp(host, 10));
        shape.setStroke(dp(host, focused ? 2 : 1), focused ? themeAccent(host) : 0x55415a72);
        // Glass intensity: 0=airy, 100=solid.
        int intensity = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)));
        shape.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(intensity,focused),prefs,false));
        return shape;
    }
    /** Mixes a glass base color 30% toward the theme accent. */
    private static int tint(int base, int accent) {
        return android.graphics.Color.rgb(
                (int) (android.graphics.Color.red(base) + (android.graphics.Color.red(accent) - android.graphics.Color.red(base)) * 0.3f),
                (int) (android.graphics.Color.green(base) + (android.graphics.Color.green(accent) - android.graphics.Color.green(base)) * 0.3f),
                (int) (android.graphics.Color.blue(base) + (android.graphics.Color.blue(accent) - android.graphics.Color.blue(base)) * 0.3f));
    }

    /** Runtime support can change under power-saving or compositor restrictions. */
    static boolean frostedAvailable(android.content.Context context) {
        if (android.os.Build.VERSION.SDK_INT < 31) return false;
        if (MemoryProfile.effectiveTier(context, ThemePreferences.get(context)) == MemoryProfile.TIER_LIGHT) return false;
        try {
            android.view.WindowManager manager = context.getSystemService(android.view.WindowManager.class);
            return manager != null && manager.isCrossWindowBlurEnabled();
        } catch (RuntimeException unavailable) { return false; }
    }
    static void applyBlur(android.app.Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        android.content.Context context = dialog.getContext();
        android.content.SharedPreferences prefs = ThemePreferences.get(context);
        boolean glass = TvGlassStyle.enabled(prefs);
        boolean frosted = PanelTransparency.enabled(prefs) && TvGlassStyle.finish(prefs) == 2;
        boolean blur = frostedAvailable(context) && frosted;
        android.view.Window window = dialog.getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.view.WindowManager.LayoutParams attrs = window.getAttributes();
            if (blur) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            attrs.setBlurBehindRadius(blur ? (frosted ? 28 : 40) : 0);
            // Preserve the updated flags when assigning the attributes again.
            attrs.flags = window.getAttributes().flags;
            window.setAttributes(attrs);
        }
        window.setDimAmount(glass ? (frosted && !blur ? .4f : (TvGlassStyle.finish(prefs) == 1 ? .22f : .32f)) : .12f);
    }
    private static void watchBlur(Activity host, LinearLayout container, AlertDialog dialog) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        android.view.WindowManager manager = host.getSystemService(android.view.WindowManager.class);
        if (manager == null) return;
        java.util.function.Consumer<Boolean> listener = available -> {
            if (dialog.isShowing()) refresh(host);
        };
        container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private boolean registered;
            @Override public void onViewAttachedToWindow(View view) {
                if (registered || android.os.Build.VERSION.SDK_INT < 31) return;
                try {
                    manager.addCrossWindowBlurEnabledListener(host.getMainExecutor(), listener);
                    registered = true;
                } catch (RuntimeException unavailable) { /* The opaque fallback is already applied. */ }
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (!registered || android.os.Build.VERSION.SDK_INT < 31) return;
                try { manager.removeCrossWindowBlurEnabledListener(listener); }
                catch (RuntimeException ignored) { }
                registered = false;
            }
        });
    }
    static TextView label(Activity host, String text, int size, int color) {
        TextView view = new TextView(host); view.setText(text); view.setTextSize(size); view.setTextColor(ThemeStyle.contentColor(ThemePreferences.get(host),color));
        applyAppFont(host, view);
        return view;
    }

    /** Launcher-wide font (Settings → Theme → Launcher font) for settings UI text. (User 2026-11-01.) */
    static void applyAppFont(android.content.Context ctx, TextView view) {
        android.content.SharedPreferences prefs = ThemePreferences.get(ctx);
        view.setTypeface(android.graphics.Typeface.create(
                ThemeStyle.fontFamily(prefs), android.graphics.Typeface.NORMAL));
        if(ThemeStyle.light(prefs)) {
            if(PanelTransparency.enabled(prefs) && TvGlassStyle.intensity(prefs)<50)
                view.setShadowLayer(2*ctx.getResources().getDisplayMetrics().density,0,0,0xeeffffff);
            else view.setShadowLayer(0,0,0,android.graphics.Color.TRANSPARENT);
        }
    }
    static View row(Activity host, String title, String subtitle, int icon, int tint, Runnable action) {
        LinearLayout row = new LinearLayout(host); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(host, 12), dp(host, 7), dp(host, 12), dp(host, 7));
        row.setMinimumHeight(dp(host, 49)); row.setFocusable(true); row.setFocusableInTouchMode(true); row.setClickable(true);
        row.setBackground(background(host, false));
        row.setOnFocusChangeListener((v, focus) -> row.setBackground(background(host, focus || row.isSelected())));
        ImageView symbol = new ImageView(host); bindSymbol(symbol, host, icon); symbol.setColorFilter(tint);
        if (tint == themeAccent(host)) symbol.setTag("theme_accent");
        row.addView(symbol, new LinearLayout.LayoutParams(dp(host, 24), dp(host, 24)));
        LinearLayout copy = new LinearLayout(host); copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(host, 13), 0, dp(host, 6), 0);
        TextView heading = label(host, title, 14, Color.WHITE); copy.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) copy.addView(label(host, subtitle, 11, 0xffa9bfd3));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label(host, "›", 26, 0xff9bb6cb); row.addView(arrow);
        row.setOnClickListener(v -> { v.requestFocus(); action.run(); });
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
        position.setMargins(0, dp(host, 3), 0, dp(host, 3)); row.setLayoutParams(position);
        return row;
    }
    private static GradientDrawable cardBackground(Activity host, boolean focused) {
        GradientDrawable shape = background(host, focused);
        shape.setCornerRadius(dp(host, TvGlassStyle.enabled(ThemePreferences.get(host)) ? 20 : 16));
        return shape;
    }
    /** Landscape tile: the icon and label share one row instead of stacking into a square. */
    static View wideCard(Activity host, String title, int icon, int tint, Runnable action) {
        LinearLayout card = new LinearLayout(host); card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(host, 11), dp(host, 10), dp(host, 11), dp(host, 10));
        card.setMinimumHeight(dp(host, 64)); card.setFocusable(true); card.setFocusableInTouchMode(true); card.setClickable(true);
        card.setTag("wide_card"); card.setClipToOutline(true); card.setElevation(dp(host, 2));
        card.setBackground(cardBackground(host, false));
        card.setOnFocusChangeListener((v, focus) -> {
            card.setBackground(cardBackground(host, focus));
            card.setTranslationZ(focus ? dp(host, 3) : 0);
        });
        ImageView symbol = new ImageView(host); bindSymbol(symbol, host, icon); symbol.setColorFilter(tint);
        if (tint == themeAccent(host)) symbol.setTag("theme_accent");
        LinearLayout.LayoutParams image = new LinearLayout.LayoutParams(dp(host, 26), dp(host, 26));
        image.setMarginEnd(dp(host, 9)); card.addView(symbol, image);
        TextView heading = label(host, title, 13, Color.WHITE); heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        heading.setMaxLines(2); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        card.setOnClickListener(v -> { v.requestFocus(); action.run(); });
        return card;
    }
    private static final java.util.WeakHashMap<Activity, java.lang.ref.WeakReference<Panel>> PANELS = new java.util.WeakHashMap<>();
    private static final class Panel {
        final AlertDialog dialog;
        final LinearLayout content;
        final java.util.ArrayDeque<Page> pages = new java.util.ArrayDeque<>();
        Panel(AlertDialog dialog, LinearLayout content) { this.dialog = dialog; this.content = content; }
    }
    private static final class Page {
        final View root, focus;
        final ScrollView scroll;
        final int scrollY;
        Page(View root) {
            this.root = root; this.focus = root.findFocus();
            this.scroll = (ScrollView) ((LinearLayout) root).getChildAt(1);
            this.scrollY = scroll.getScrollY();
        }
        void restore() {
            if (focus != null && focus.isShown() && focus.requestFocus()) {
                scroll.post(() -> scroll.scrollTo(0, scrollY));
            } else {
                focusRows((LinearLayout) scroll.getChildAt(0));
            }
        }
    }
    static AlertDialog panel(Activity host, String title, LinearLayout rows) {
        return panel(host, title, rows, false);
    }
    static AlertDialog panel(Activity host, String title, LinearLayout rows, boolean focusHeader) {
        java.lang.ref.WeakReference<Panel> reference = PANELS.get(host);
        Panel state = reference == null ? null : reference.get();
        if (state == null || !state.dialog.isShowing()) {
            LinearLayout container = new LinearLayout(host); container.setOrientation(LinearLayout.VERTICAL);
            int frame = dp(host, 8);
            container.setPadding(frame, frame, frame, frame);
            AlertDialog dialog = new ThemeDialogBuilder(host).setView(container).create();
            state = new Panel(dialog, container);
            PANELS.put(host, new java.lang.ref.WeakReference<>(state));
            Panel current = state;
            dialog.setOnKeyListener((d, key, event) -> {
                if (key != android.view.KeyEvent.KEYCODE_BACK || current.pages.isEmpty()) return false;
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) back(current);
                return true;
            });
            // Configure BEFORE show: no center-to-right reposition on the first visible frame.
            dialog.create();
            watchBlur(host, container, dialog);
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                boolean tv = (host.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
                int width = Math.min(dp(host, 350), host.getResources().getDisplayMetrics().widthPixels - dp(host, 24));
                window.setGravity(tv ? Gravity.END : Gravity.CENTER);
                window.setLayout(width, tv ? -1 : Math.round(host.getResources().getDisplayMetrics().heightPixels * .88f));
                window.setWindowAnimations(0);
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                boolean glass = TvGlassStyle.enabled(ThemePreferences.get(host));
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setDimAmount(glass ? .32f : .12f);
            }
        }
        Panel current = state;
        if (current.content.getChildCount() > 0) {
            View old = current.content.getChildAt(0); Page saved = new Page(old); current.content.removeView(old); current.pages.push(saved);
        }
        LinearLayout page = new LinearLayout(host); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(host, 16), dp(host, 16), dp(host, 16), dp(host, 16));
        page.setTag("settings_shell");
        styleShell(host, page);
        LinearLayout bar = new LinearLayout(host); bar.setGravity(Gravity.CENTER_VERTICAL);
        if (!current.pages.isEmpty()) {
            TextView back = label(host, "‹", 26, Color.WHITE); back.setGravity(Gravity.CENTER);
            back.setContentDescription("Back to settings"); back.setFocusable(true);
            back.setBackground(background(host, false)); back.setOnClickListener(v -> back(current));
            bar.addView(back, new LinearLayout.LayoutParams(dp(host, 32), dp(host, 36)));
        }
        TextView heading = label(host, title, 21, Color.WHITE); heading.setTypeface(heading.getTypeface(), Typeface.BOLD);
        bar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView settings = new ImageView(host);
        bindSymbol(settings, host, R.drawable.ic_settings); settings.setColorFilter(ThemeStyle.foreground(ThemePreferences.get(host)));
        settings.setPadding(dp(host, 7), dp(host, 7), dp(host, 7), dp(host, 7));
        settings.setContentDescription(host.getString(R.string.tv_settings));
        settings.setId(View.generateViewId()); settings.setFocusable(true); settings.setClickable(true);
        settings.setNextFocusUpId(settings.getId()); settings.setBackground(background(host, false));
        bar.addView(settings, new LinearLayout.LayoutParams(dp(host, 36), dp(host, 36)));
        page.addView(bar);
        ScrollView scroll = new ScrollView(host); scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(rows); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        current.content.addView(page, new LinearLayout.LayoutParams(-1, -1));
        settings.setOnFocusChangeListener((v, f) -> {
            settings.setBackground(background(host, f));
            if (f) scroll.scrollTo(0, 0);
        });
        settings.setOnClickListener(v -> {
            current.dialog.dismiss();
            try {
                host.startActivity(SystemSettings.intent(host, android.provider.Settings.ACTION_SETTINGS));
            } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                android.widget.Toast.makeText(host, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        Runnable initialFocus = () -> {
            if (focusHeader) { settings.requestFocus(); scroll.scrollTo(0, 0); }
            else focusRows(rows);
        };
        current.dialog.setOnShowListener(d -> initialFocus.run());
        if (current.dialog.isShowing()) rows.post(initialFocus);
        applyBlur(current.dialog);
        return current.dialog;
    }
    private static void focusRows(LinearLayout rows) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.getVisibility() == View.VISIBLE && row.isSelected() && row.requestFocus()) return;
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.getVisibility() == View.VISIBLE && row.requestFocus()) return;
        }
    }
    private static void back(Panel current) {
        if (current.pages.isEmpty()) { current.dialog.dismiss(); return; }
        current.content.removeAllViews();
        Page previous = current.pages.pop(); current.content.addView(previous.root, new LinearLayout.LayoutParams(-1, -1));
        previous.restore();
    }
    static void options(Activity host, String title, String[] names, String[] hints, java.util.function.IntConsumer selected) {
        options(host, title, names, hints, null, selected);
    }

    static void choices(Activity host, String title, String[] names, java.util.function.IntSupplier current,
            java.util.function.IntConsumer selected) {
        options(host, title, names, null, null, selected, current, null);
    }

    /** Retheme the visible page and saved parent pages without losing focus or scroll position. */
    static void refresh(Activity host) {
        java.lang.ref.WeakReference<Panel> reference = PANELS.get(host);
        Panel state = reference == null ? null : reference.get();
        if (state == null || !state.dialog.isShowing()) return;
        refreshViews(host, state.content);
        for (Page page : state.pages) refreshViews(host, page.root);
        applyBlur(state.dialog);
    }
    static void styleDialog(Activity host, AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        GradientDrawable shell = shellBackground(host);
        dialog.getWindow().setBackgroundDrawable(shell);
        styleDialogText(host, dialog.getWindow().getDecorView());
        applyBlur(dialog);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            TextView button = dialog.getButton(which);
            if (button != null) { applyAppFont(host, button); button.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host))); }
        }
    }
    private static void styleDialogText(Activity host, View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int weight = text.getTypeface() == null ? Typeface.NORMAL : text.getTypeface().getStyle();
            applyAppFont(host, text); text.setTypeface(text.getTypeface(), weight);
            text.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleDialogText(host, group.getChildAt(i));
        }
    }
    private static android.content.SharedPreferences prefsFor(Activity host) { return ThemePreferences.get(host); }
    private static void styleShell(Activity host, View page) {
        boolean glass = TvGlassStyle.enabled(ThemePreferences.get(host));
        page.setBackground(shellBackground(host));
        page.setClipToOutline(true);
        page.setElevation(dp(host, glass ? 6 : 0));
    }
    private static GradientDrawable shellBackground(Activity host) {
        if(ThemeStyle.harmony(ThemePreferences.get(host)))return HarmonyVisionSurface.sheet(host);
        if(ThemeStyle.id(ThemePreferences.get(host))==ThemeStyle.GLASS)return GlassOsUi.controls(host);
        boolean glass = TvGlassStyle.enabled(ThemePreferences.get(host));
        GradientDrawable shell = glass ? TvGlassStyle.surface(dp(host, 28), dp(host, 1), false, prefsFor(host))
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xff122338, 0xff09182a});
        shell.setCornerRadius(dp(host, glass ? 28 : 24));
        android.content.SharedPreferences prefs = ThemePreferences.get(host);
        if (!glass) {
            shell.setStroke(dp(host, 1), 0x55415a72);
            shell.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.panel(TvGlassStyle.intensity(prefs)),prefs,false));
        }
        return shell;
    }
    private static void refreshViews(Activity host, View view) {
        if (view instanceof ControlCenterGrid) ((ControlCenterGrid) view).refresh();
        if ("settings_shell".equals(view.getTag())) styleShell(host, view);
        if (view.isFocusable() && view.getBackground() instanceof GradientDrawable)
            view.setBackground("wide_card".equals(view.getTag())
                    ? cardBackground(host, view.isFocused())
                    : "control_center_tile".equals(view.getTag()) && view.getParent().getParent() instanceof ControlCenterGrid
                    ? ((ControlCenterGrid) view.getParent().getParent()).surface(view.isFocused())
                    : background(host, view.isFocused() || view.isSelected()));
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int style = text.getTypeface() == null ? Typeface.NORMAL : text.getTypeface().getStyle();
            applyAppFont(host, text); text.setTypeface(text.getTypeface(), style);
            if(ThemeStyle.light(ThemePreferences.get(host))) text.setTextColor(ThemeStyle.foreground(ThemePreferences.get(host)));
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            Object resource = image.getTag(R.id.theme_symbol_resource);
            if (resource instanceof Integer) bindSymbol(image, host, (Integer) resource);
            if ("theme_accent".equals(view.getTag())) image.setColorFilter(themeAccent(host));
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) refreshViews(host, group.getChildAt(i));
        }
        if(view instanceof ControlCenterGrid)((ControlCenterGrid)view).status();
    }

    static void options(Activity host, String title, String[] names, String[] hints, int[] icons, java.util.function.IntConsumer selected) {
        options(host, title, names, hints, icons, selected, null, null);
    }
    static void options(Activity host, String title, String[] names, String[] hints, int[] icons,
            java.util.function.IntConsumer selected, java.util.function.IntPredicate visible) {
        options(host, title, names, hints, icons, selected, null, visible);
    }

    private static void options(Activity host, String title, String[] names, String[] hints, int[] icons,
            java.util.function.IntConsumer selected, java.util.function.IntSupplier current,
            java.util.function.IntPredicate visible) {
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = panel(host, title, rows);
        boolean navigation = hints != null || title.equals("Online landscape screensaver");
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            String hint = (hints != null && i < hints.length) ? hints[i] : null;
            int icon = (icons != null && i < icons.length) ? icons[i] : R.drawable.ic_settings;
            int checked = current == null ? -1 : current.getAsInt();
            String caption = names[i].replace("  ✓", "") + (i == checked ? "  ✓" : "");
            View option = row(host, caption, hint, icon, themeAccent(host), () -> {});
            option.setSelected(i == checked);
            option.setOnClickListener(v -> {
                v.requestFocus();
                selected.accept(index);
                if (!navigation) {
                    int checkedNow = current == null ? index : current.getAsInt();
                    for (int j = 0; j < rows.getChildCount(); j++) {
                        LinearLayout copy = (LinearLayout) ((LinearLayout) rows.getChildAt(j)).getChildAt(1);
                        ((TextView) copy.getChildAt(0)).setText(names[j].replace("  ✓", "") + (j == checkedNow ? "  ✓" : ""));
                        View choice = rows.getChildAt(j); choice.setSelected(j == checkedNow);
                        choice.setBackground(background(host, choice.isFocused() || choice.isSelected()));
                    }
                }
            });
            rows.addView(option);
        }
        if (visible != null) {
            Runnable updateRows = () -> {
                for (int i = 0; i < rows.getChildCount(); i++) {
                    rows.getChildAt(i).setVisibility(visible.test(i) ? View.VISIBLE : View.GONE);
                }
            };
            updateRows.run();
            rows.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { updateRows.run(); }
                @Override public void onViewDetachedFromWindow(View view) { }
            });
        }
        applyBlur(dialog);
        dialog.show();
    }
}


