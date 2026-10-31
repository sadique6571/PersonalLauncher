package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Responsive personal Home. Profile/search stay local; online scenery/weather use their own view. */
public final class PersonalHomeActivity extends Activity {
    private static final int CYAN = 0xff40d9ff, TEXT = 0xfff0f7ff, MUTED = 0xffa4bcd6;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout page, header, actions;
    private GridLayout grid;
    private TextView userName, time, date, networkStatus, appHeading;
    private ImageView avatar;
    private Bitmap avatarBitmap;
    private Bitmap customWallpaper;
    private ContinueWatchingView continueWatching;
    private SkyClock sky;
    private ConnectivityManager connectivity;
    private boolean networkRegistered, resumed, tv;
    private int pageWidth;
    private List<App> cachedApps;
    /** Move mode: the app being carried, and the visible package order while it moves. Null when off. */
    private App carriedApp;
    private List<String> moveOrder;
    /** Live tiles by package, for progressive icon fill-in. Rebuilt with the grid. */
    private final java.util.Map<String, View> tileViews = new java.util.HashMap<>();
    private AlertDialog panel, profile;
    private AlertDialog searchDialog;
    private EditText searchInput;
    private Runnable searchRefresh;
    private final Runnable idleScreensaver = () -> {
        if (resumed && hasWindowFocus()) launch(new Intent(this, LandscapeActivity.class));
        else armIdleScreensaver();
    };
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { handler.post(() -> updateNetwork()); }
        @Override public void onLost(Network network) { handler.post(() -> updateNetwork()); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            handler.post(() -> updateNetwork());
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            updateClock();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("personal_home", MODE_PRIVATE);
        if (!prefs.getBoolean("reference_layout_v42", false)) {
            prefs.edit().putBoolean("reference_layout_v42", true).putString("theme_mode", "dark")
                    .putInt("photo_seconds", 30).apply();
        }
        if (!prefs.getBoolean("reference_layout_v43", false)) {
            prefs.edit().putBoolean("reference_layout_v43", true).putInt("background_dim", 0).apply();
        }
        UiModeManager modes = getSystemService(UiModeManager.class);
        tv = modes != null && modes.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        connectivity = getSystemService(ConnectivityManager.class);
        buildHome();
        loadAvatar();
        loadWallpaper();
        if (!prefs.getBoolean("setup", false)) handler.post(() -> editProfile());
        else handleHomeAction(getIntent());
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private float appearanceDaylight() {
        Calendar now = Calendar.getInstance();
        return HomeAppearance.daylight(prefs.getString("theme_mode", "dark"), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
    }

    private void applyAppearance() {
        // Recreate surfaces as well as the wallpaper; changing only the canvas left cards unchanged.
        buildHome();
    }

    private GradientDrawable surface(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        float day = appearanceDaylight();
        shape.setColor(blend(focused ? 0xff153d5b : 0xff092038, focused ? 0xff407596 : 0xff335975, day));
        shape.setAlpha(focused ? 238 : 208);
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(focused ? 3 : 1), focused ? CYAN : 0xff28435e);
        return shape;
    }

    private GradientDrawable avatarSurface(boolean focused) {
        GradientDrawable shape = surface(focused);
        shape.setShape(GradientDrawable.OVAL);
        return shape;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(TEXT);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setMinimumWidth(0);
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        view.setBackground(surface(false));
        view.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void iconAction(String title, int resource, Runnable action) {
        android.widget.ImageButton icon = new android.widget.ImageButton(this);
        icon.setImageResource(resource);
        icon.setColorFilter(TEXT);
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setBackground(surface(false));
        icon.setFocusable(true);
        icon.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
        icon.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(tv ? 48 : 44), dp(tv ? 48 : 44));
        p.setMargins(dp(2), 0, dp(2), 0);
        actions.addView(icon, p);
    }

    private void buildHome() {
        if (continueWatching != null) continueWatching.stop();
        pageWidth = 0;
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xff031126);
        sky = new SkyClock();
        root.addView(sky, new ViewGroup.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(tv ? 32 : 16);
        page.setPadding(inset, dp(14), inset, dp(12));
        root.addView(page, new ViewGroup.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            page.setPadding(inset + insets.getSystemWindowInsetLeft(), dp(14) + insets.getSystemWindowInsetTop(),
                    inset + insets.getSystemWindowInsetRight(), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new ImageView(this);
        avatar.setContentDescription("Edit profile photo");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(avatarSurface(false));
        avatar.setClipToOutline(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> editProfile());
        avatar.setOnFocusChangeListener((v, focus) -> v.setBackground(avatarSurface(focus)));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout identity = new LinearLayout(this);
        identity.setPadding(dp(12), 0, dp(4), 0);
        identity.setOrientation(LinearLayout.VERTICAL);
        userName = text("", tv ? 24 : 20, TEXT);
        userName.setMaxLines(2);
        userName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(userName);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        networkStatus = text("", 13, MUTED);
        networkStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER);
        networkStatus.setCompoundDrawablePadding(dp(5));
        networkStatus.setFocusable(true);
        networkStatus.setOnFocusChangeListener((v, focus) -> {
            if (focus) v.setBackground(surface(true));
            else v.setBackgroundColor(Color.TRANSPARENT);
        });
        networkStatus.setPadding(dp(tv ? 13 : 11), dp(8), dp(tv ? 13 : 11), dp(8));
        networkStatus.setOnClickListener(v -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        page.addView(header);
        actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.addView(networkStatus, new LinearLayout.LayoutParams(-2, dp(44)));
        iconAction("Quick actions", R.drawable.ic_apps, this::showActionDrawer);
        iconAction("Settings", R.drawable.ic_settings, this::showSettings);
        if (tv) {
            header.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        } else {
            page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER);
        boolean compact = getResources().getConfiguration().screenHeightDp < 480;
        clock.setPadding(0, dp(compact ? 0 : tv ? 18 : 24), 0, dp(compact ? 8 : 16));
        int clockSize = prefs.getInt("clock_size", tv ? 66 : 56);
        if (compact) clockSize = Math.min(clockSize, 44);
        time = text("", clockSize, TEXT);
        time.setGravity(Gravity.CENTER);
        time.setSingleLine(true);
        time.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        time.setAutoSizeTextTypeUniformWithConfiguration(26, clockSize, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        date = text("", tv ? 14 : 13, MUTED);
        date.setGravity(Gravity.CENTER);
        clock.addView(time, new LinearLayout.LayoutParams(-1, dp(clockSize + (compact ? 10 : 24))));
        clock.addView(date);
        page.addView(clock);
        time.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int[] position = new int[2], origin = new int[2];
            time.getLocationInWindow(position);
            root.getLocationInWindow(origin);
            sky.clockY = position[1] - origin[1] + time.getHeight() / 2f;
            sky.invalidate();
        });
        appHeading = text("Your apps", prefs.getInt("focus_title_size", tv ? 32 : 26), TEXT);
        appHeading.setSingleLine(true);
        appHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appHeading.setPadding(dp(4), dp(8), 0, dp(12));
        page.addView(appHeading);
        grid = new GridLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout appBody = new LinearLayout(this);
        appBody.setOrientation(LinearLayout.VERTICAL);
        appBody.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        continueWatching = new ContinueWatchingView(this, label -> appHeading.setText(label));
        appBody.addView(continueWatching, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(appBody, new ViewGroup.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof Integer && prefs.getBoolean("focus_glow", true)) {
                int[] position = new int[2], origin = new int[2];
                focused.getLocationInWindow(position); sky.getLocationInWindow(origin);
                sky.focusX = position[0] - origin[0] + focused.getWidth() / 2f;
                sky.focusY = position[1] - origin[1] + focused.getHeight() / 2f;
                sky.invalidate();
            }
        });
        page.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = page.getWidth() - page.getPaddingLeft() - page.getPaddingRight();
            if (width > 0 && width != pageWidth) {
                pageWidth = width;
                if (cachedApps != null) renderApps(cachedApps);
                else loadApps();
            }
        });
        refreshProfile();
        updateClock();
        updateNetwork();
        WindowManager.LayoutParams brightness = getWindow().getAttributes();
        brightness.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(brightness);
        if (resumed) continueWatching.start();
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        String value = PersonalHomeModel.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        android.text.SpannableString clockText = new android.text.SpannableString(value);
        clockText.setSpan(new android.text.style.RelativeSizeSpan(.42f), value.length() - 2,
                value.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        time.setText(clockText);
        date.setText(new java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now.getTime()));
        sky.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        sky.invalidate();
    }

    private void updateNetwork() {
        if (networkStatus == null) return;
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        networkStatus.setText(online ? "Connected" : caps == null ? "Disconnected" : "No internet access");
        networkStatus.setTooltipText(online ? (wifi ? "Wi-Fi · Internet available" : "Internet available") : "No internet");
        int color = online ? 0xff59e493 : 0xffff6c78;
        networkStatus.setTextColor(color);
        int iconId = wifi || caps == null ? R.drawable.ic_wifi
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? R.drawable.ic_ethernet : R.drawable.ic_internet;
        android.graphics.drawable.Drawable icon = getDrawable(iconId).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(28), dp(24));
        networkStatus.setCompoundDrawables(icon, null, null, null);
        networkStatus.setContentDescription(online ? "Internet available. Open Wi-Fi settings" : "No validated internet access. Open Wi-Fi settings");
    }

    private int appGeneration;
    private void loadApps() {
        int generation = ++appGeneration;
        worker.execute(() -> {
            List<ResolveInfo> found = new ArrayList<>();
            if (tv) found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            Set<String> packages = new HashSet<>();
            List<App> apps = new ArrayList<>();
            java.util.Map<String, App> previous = new java.util.HashMap<>();
            if (cachedApps != null) for (App app : cachedApps) previous.put(app.info.activityInfo.packageName, app);
            // Phase 1: labels only — the grid renders immediately with letter placeholders.
            for (ResolveInfo info : found) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !packages.add(pkg)) continue;
                try {
                    String label = info.loadLabel(getPackageManager()).toString();
                    App old = previous.get(pkg);
                    if (old != null && old.iconReady && old.name.equals(label)
                            && java.util.Objects.equals(old.info.activityInfo.applicationInfo.sourceDir, info.activityInfo.applicationInfo.sourceDir)) {
                        apps.add(new App(label, info, old.icon, old.accent, true));
                    } else {
                        apps.add(new App(label, info, letterIcon(label), CYAN, false));
                    }
                } catch (RuntimeException removedDuringQuery) {
                    // A package can be replaced or removed between queryIntentActivities and loading its resources.
                }
            }
            Collections.sort(apps, (a, b) -> a.name.compareToIgnoreCase(b.name));
            handler.post(() -> {
                if (!isDestroyed() && generation == appGeneration && !sameApps(cachedApps, apps)) renderApps(apps);
            });
            // Phase 2: icons one by one — disk cache first, then compose and store.
            IconCache cache = new IconCache(this);
            android.content.pm.PackageManager pm = getPackageManager();
            for (App app : apps) {
                if (generation != appGeneration) return;
                if (app.iconReady) continue;
                String pkg = app.info.activityInfo.packageName;
                try {
                    long version = IconCache.version(pm, pkg);
                    Bitmap card = cache.load(pkg, version);
                    int accent;
                    if (card != null) {
                        accent = cache.accent(pkg, version, CYAN);
                    } else {
                        android.graphics.drawable.Drawable logo = app.info.loadIcon(pm);
                        if (pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube")) logo = getDrawable(R.drawable.logo_youtube);
                        if (pkg.equals("com.netflix.ninja") || pkg.equals("com.netflix.mediaclient")) logo = getDrawable(R.drawable.logo_netflix);
                        AppArtwork artwork = AppArtwork.create(getResources(), logo);
                        card = ((android.graphics.drawable.BitmapDrawable) artwork.image).getBitmap();
                        accent = artwork.accent;
                        cache.save(pkg, version, card, accent);
                    }
                    Bitmap finished = card;
                    int finishedAccent = accent;
                    handler.post(() -> {
                        if (!isDestroyed() && generation == appGeneration)
                            updateTileIcon(pkg, new android.graphics.drawable.BitmapDrawable(getResources(), finished), finishedAccent);
                    });
                } catch (RuntimeException removedDuringQuery) {
                    // The package vanished mid-load; the next loadApps pass drops it.
                }
            }
        });
    }

    /** Cheap letter placeholder so the grid is useful before icons finish. Runs on the worker thread. */
    private android.graphics.drawable.Drawable letterIcon(String name) {
        Bitmap bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff0d2036);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x6640d9ff);
        paint.setTextSize(44);
        paint.setTextAlign(Paint.Align.CENTER);
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        canvas.drawText(letter, 80, 60, paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    /** Swaps a finished icon into the live tile and the cached App. Runs on the UI thread. */
    private void updateTileIcon(String pkg, android.graphics.drawable.Drawable icon, int accent) {
        if (cachedApps != null) {
            for (App app : cachedApps) {
                if (app.info.activityInfo.packageName.equals(pkg)) {
                    app.icon = icon; app.accent = accent; app.iconReady = true;
                    break;
                }
            }
        }
        if (carriedApp != null) return;
        View tile = tileViews.get(pkg);
        if (tile instanceof android.widget.FrameLayout) {
            View iconView = ((android.widget.FrameLayout) tile).getChildAt(0);
            if (iconView instanceof ImageView) ((ImageView) iconView).setImageDrawable(icon);
            tile.setForeground(tileBorder(accent, tile.isFocused()));
            tile.setTag(accent);
        }
    }

    private static final class App {
        final String name;
        final ResolveInfo info;
        android.graphics.drawable.Drawable icon;
        int accent;
        boolean iconReady;
        App(String name, ResolveInfo info, android.graphics.drawable.Drawable icon, int accent, boolean iconReady) {
            this.name = name; this.info = info; this.icon = icon; this.accent = accent; this.iconReady = iconReady;
        }
    }

    private static boolean sameApps(List<App> before, List<App> after) {
        if (before == null || before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            App a = before.get(i), b = after.get(i);
            if (a.icon != b.icon || !a.name.equals(b.name) || !a.info.activityInfo.packageName.equals(b.info.activityInfo.packageName)
                    || !a.info.activityInfo.name.equals(b.info.activityInfo.name)) return false;
        }
        return true;
    }

    private void renderApps(List<App> apps) {
        cachedApps = apps;
        appHeading.setText("Your apps");
        grid.removeAllViews();
        tileViews.clear();
        int columns = PersonalHomeModel.columns((int) (pageWidth / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0), prefs.getBoolean("large_tiles", false));
        grid.setColumnCount(columns);
        int width = pageWidth / columns;
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        for (App app : orderedApps(apps)) {
            if (!hidden.contains(app.info.activityInfo.packageName)) addAppTile(grid, app, width);
        }
        if (grid.getChildCount() == 0) {
            appHeading.setText("No visible apps");
            Toast.makeText(this, "Unhide apps in Settings → Launcher settings", Toast.LENGTH_LONG).show();
        }
        if (searchRefresh != null) searchRefresh.run();
    }

    /** Direct move mode: long-press > Move, then arrows carry the tile, OK drops it, Back cancels. */
    private void startMoveMode(App app) {
        if (cachedApps == null) return;
        List<App> visible = orderedApps(cachedApps);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        visible.removeIf(a -> hidden.contains(a.info.activityInfo.packageName));
        moveOrder = new ArrayList<>();
        for (App a : visible) moveOrder.add(a.info.activityInfo.packageName);
        carriedApp = app;
        renderMoveGrid();
        Toast.makeText(this, "Arrows move · OK places · Back cancels", Toast.LENGTH_LONG).show();
    }

    private void renderMoveGrid() {
        java.util.Map<String, App> byPackage = new java.util.LinkedHashMap<>();
        for (App app : cachedApps) byPackage.put(app.info.activityInfo.packageName, app);
        appHeading.setText("Move " + carriedApp.name);
        grid.removeAllViews();
        tileViews.clear();
        int columns = PersonalHomeModel.columns((int) (pageWidth / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0), prefs.getBoolean("large_tiles", false));
        grid.setColumnCount(columns);
        int width = pageWidth / columns;
        String movingPkg = carriedApp.info.activityInfo.packageName;
        for (String pkg : moveOrder) {
            App app = byPackage.get(pkg);
            if (app != null) addAppTile(grid, app, width, pkg.equals(movingPkg));
        }
        View moving = grid.getChildAt(moveOrder.indexOf(movingPkg));
        if (moving != null) moving.requestFocus();
    }

    private void commitMove() {
        // Hidden apps keep their slots; visible ones take the new order.
        Set<String> visibleSet = new HashSet<>(moveOrder);
        java.util.Iterator<String> moved = moveOrder.iterator();
        List<String> result = new ArrayList<>();
        for (App app : orderedApps(cachedApps)) {
            String pkg = app.info.activityInfo.packageName;
            result.add(visibleSet.contains(pkg) ? moved.next() : pkg);
        }
        prefs.edit().putString("app_order", new org.json.JSONArray(result).toString()).apply();
        carriedApp = null; moveOrder = null;
        renderApps(cachedApps);
        focusFirstApp();
        Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show();
    }

    private void cancelMove() {
        carriedApp = null; moveOrder = null;
        renderApps(cachedApps);
        focusFirstApp();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (carriedApp != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            String pkg = carriedApp.info.activityInfo.packageName;
            int from = moveOrder.indexOf(pkg);
            int cols = Math.max(1, grid.getColumnCount());
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                commitMove(); return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) { cancelMove(); return true; }
            int to = -1;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { if (from % cols != 0) to = from - 1; }
            else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { if (from % cols != cols - 1 && from + 1 < moveOrder.size()) to = from + 1; }
            else if (code == KeyEvent.KEYCODE_DPAD_UP) { if (from - cols >= 0) to = from - cols; }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) { if (from + cols < moveOrder.size()) to = from + cols; }
            else return super.dispatchKeyEvent(event);
            if (to >= 0) {
                Collections.swap(moveOrder, from, to);
                renderMoveGrid();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void addAppTile(GridLayout parent, App app, int width) {
        addAppTile(parent, app, width, false);
    }

    private void addAppTile(GridLayout parent, App app, int width, boolean moving) {
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
        tile.setBackground(surface(false));
        tile.setClipToOutline(true);
        tile.setElevation(dp(3));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(app.name);
        tile.setTag(app.accent);
        tile.setForeground(tileBorder(app.accent, false));
        if (moving) {
            tile.setScaleX(1.07f);
            tile.setScaleY(1.07f);
            tile.setElevation(dp(10));
            tile.setForeground(tileBorder(CYAN, true));
        }
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        // Artwork is composed at the card's 16:9 ratio; no second inset or crop here.
        icon.setScaleType(ImageView.ScaleType.FIT_XY);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new android.widget.FrameLayout.LayoutParams(-1, -1));
        tile.setOnFocusChangeListener((v, focus) -> {
            tile.setBackground(surface(focus));
            if (!moving) tile.setForeground(tileBorder(app.accent, focus));
            tile.setTranslationZ(focus && prefs.getBoolean("focus_glow", true) ? dp(10) : 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                tile.setOutlineAmbientShadowColor(app.accent);
                tile.setOutlineSpotShadowColor(app.accent);
            }
            if (focus) {
                appHeading.setText(app.name);
                appHeading.setTextColor(TEXT);
                int[] position = new int[2], origin = new int[2];
                tile.getLocationInWindow(position); sky.getLocationInWindow(origin);
                sky.focusX = position[0] - origin[0] + tile.getWidth() / 2f;
                sky.focusY = position[1] - origin[1] + tile.getHeight() / 2f;
                sky.focusRadius = tile.getWidth(); sky.focusAccent = prefs.getBoolean("focus_glow", true) ? app.accent : 0;
            } else sky.focusAccent = 0;
            sky.invalidate();
        });
        tile.setOnClickListener(v -> launch(new Intent(Intent.ACTION_MAIN)
                .setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
        tile.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(app.name).setItems(new String[]{"App info", "Hide from launcher", "Move app"}, (dialog, which) -> {
                if (which == 0) launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.activityInfo.packageName)));
                else if (which == 2) startMoveMode(app);
                else {
                    Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", Collections.emptySet()));
                    hidden.add(app.info.activityInfo.packageName);
                    prefs.edit().putStringSet("hidden_apps", hidden).apply();
                    refreshApps(); focusFirstApp();
                }
            }).show();
            return true;
        });
        GridLayout.LayoutParams position = new GridLayout.LayoutParams();
        position.width = width - dp(16);
        position.height = Math.round(position.width * 9f / 16f);
        position.setMargins(dp(8), dp(10), dp(8), dp(10));
        tileViews.put(app.info.activityInfo.packageName, tile);
        parent.addView(tile, position);
    }

    private void searchApps() {
        if (searchDialog != null && searchDialog.isShowing()) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Search installed apps");
        searchInput = input;
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        form.addView(input);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        ScrollView matches = new ScrollView(this); matches.addView(results);
        form.addView(matches, new LinearLayout.LayoutParams(-1, dp(tv ? 250 : 300)));
        searchDialog = new AlertDialog.Builder(this).setTitle("Search apps").setView(form)
                .setPositiveButton("Find on Play Store", (dialog, which) -> openStore(input.getText().toString().trim()))
                .setNeutralButton("Voice search", (dialog, which) -> recognizeIntoSearch())
                .setNegativeButton("Close", null).create();
        searchRefresh = () -> {
            results.removeAllViews();
            String query = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
            int count = 0;
            if (cachedApps != null) for (App app : cachedApps) {
                if (hidden.contains(app.info.activityInfo.packageName) || !(app.name + " " + app.info.activityInfo.packageName).toLowerCase(Locale.ROOT).contains(query)) continue;
                Button result = button(app.name, () -> {
                    if (searchDialog != null) searchDialog.dismiss();
                    launch(new Intent(Intent.ACTION_MAIN).setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                });
                LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2); position.setMargins(0, dp(4), 0, dp(4));
                results.addView(result, position);
                if (++count == 16) break;
            }
            if (count == 0) results.addView(text(cachedApps == null ? "Loading apps…" : "No installed matches. Find new apps on Play Store.", 15, MUTED));
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { if (searchRefresh != null) searchRefresh.run(); }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
        searchDialog.setOnDismissListener(dialog -> { searchRefresh = null; searchDialog = null; searchInput = null; });
        searchDialog.show(); searchRefresh.run();
    }

    private boolean launchAssistant() {
        return tryLaunch(new Intent(Intent.ACTION_ASSIST)) || tryLaunch(new Intent(Intent.ACTION_VOICE_COMMAND))
                || tryLaunch(new Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH));
    }

    private void voiceSearch() { if (!launchAssistant()) recognizeIntoSearch(); }

    private void recognizeIntoSearch() {
        try {
            startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search launcher apps"), 9);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            if (!launchAssistant()) Toast.makeText(this, "Enable your TV's Assistant or speech service in system settings", Toast.LENGTH_LONG).show();
        }
    }

    private void openStore(String query) {
        if (query.isEmpty()) {
            Intent store = getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if (store != null && tryLaunch(store)) return;
        }
        String suffix = query.isEmpty() ? "" : "search?q=" + Uri.encode(query) + "&c=apps";
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse(query.isEmpty() ? "market://apps" : "market://" + suffix))
                .setPackage("com.android.vending");
        if (!tryLaunch(market)) launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/" + (suffix.isEmpty() ? "apps" : suffix))));
    }

    private boolean tryLaunch(Intent target) {
        try {
            startActivity(target);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }

    private void launch(Intent target) {
        if (!tryLaunch(target)) Toast.makeText(this, "This app or setting is unavailable on this device", Toast.LENGTH_LONG).show();
    }

    private void openSetting(String action) { launch(SystemSettings.intent(this, action)); }

    private void showInputs() {
        android.media.tv.TvInputManager manager = getSystemService(android.media.tv.TvInputManager.class);
        List<android.media.tv.TvInputInfo> sources = manager == null ? Collections.emptyList() : manager.getTvInputList();
        List<String> labels = new ArrayList<>();
        List<Intent> targets = new ArrayList<>();
        for (android.media.tv.TvInputInfo source : sources) {
            if (source.isHidden(this)) continue;
            labels.add(source.loadLabel(this).toString());
            targets.add(new Intent(Intent.ACTION_VIEW, source.isPassthroughInput()
                    ? android.media.tv.TvContract.buildChannelUriForPassthroughInput(source.getId())
                    : android.media.tv.TvContract.Channels.CONTENT_URI));
        }
        if (targets.isEmpty()) {
            launch(new Intent("com.android.tv.action.VIEW_INPUTS"));
            return;
        }
        chooseSetting("Inputs", labels.toArray(new String[0]), which -> launch(targets.get(which)));
    }

    private void showActionDrawer() {
        GlassSettings.options(this, "Quick actions", new String[]{"Search apps", "Play Store", "Screensaver"},
                new String[]{"Search installed apps", "Find and install new apps", "Preview or set up automatic landscapes"}, which -> {
                    if (which == 0) searchApps();
                    else if (which == 1) openStore("");
                    else screensaverSettings();
                });
    }

    private List<App> orderedApps(List<App> apps) {
        java.util.Map<String, App> byPackage = new java.util.LinkedHashMap<>();
        for (App app : apps) byPackage.put(app.info.activityInfo.packageName, app);
        List<String> saved = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(prefs.getString("app_order", "[]"));
            for (int i = 0; i < array.length(); i++) saved.add(array.getString(i));
        } catch (org.json.JSONException malformed) { /* Fall back to alphabetical order. */ }
        List<App> result = new ArrayList<>();
        for (String pkg : AppOrder.merge(new ArrayList<>(byPackage.keySet()), saved)) result.add(byPackage.get(pkg));
        return result;
    }

    private void rearrangeApps() {
        if (cachedApps == null) return;
        List<App> apps = orderedApps(cachedApps);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        apps.removeIf(app -> hidden.contains(app.info.activityInfo.packageName));
        if (apps.isEmpty()) { Toast.makeText(this, "Unhide an app first", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[apps.size()], hints = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) { names[i] = apps.get(i).name; hints[i] = "Select to move this app"; }
        GlassSettings.options(this, "Rearrange apps", names, hints, which -> moveApp(apps.get(which)));
    }

    private void moveApp(App moving) {
        if (cachedApps == null) return;
        List<App> apps = orderedApps(cachedApps);
        List<App> visible = new ArrayList<>(apps);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        visible.removeIf(app -> hidden.contains(app.info.activityInfo.packageName));
        String[] positions = new String[visible.size()];
        for (int i = 0; i < visible.size(); i++) positions[i] = (i + 1) + " · " + visible.get(i).name;
        chooseSetting("Move " + moving.name + " to position", positions, which -> {
            List<String> packages = new ArrayList<>();
            for (App app : orderedApps(cachedApps)) packages.add(app.info.activityInfo.packageName);
            int destination = packages.indexOf(visible.get(which).info.activityInfo.packageName);
            packages = AppOrder.move(packages, moving.info.activityInfo.packageName, destination);
            prefs.edit().putString("app_order", new org.json.JSONArray(packages).toString()).apply();
            refreshApps();
            Toast.makeText(this, "App order saved", Toast.LENGTH_SHORT).show();
        });
    }

    private void showSettings() {
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        panel = GlassSettings.panel(this, "Settings", rows);
        addPanelRow(rows, "Launcher settings", "Layout, wallpaper & apps", R.drawable.ic_settings, CYAN, this::launcherSettings);
        TextView quickLabel = GlassSettings.label(this, "Quick actions", 12, 0xff9bb6cb);
        quickLabel.setPadding(dp(4), dp(10), 0, dp(4));
        rows.addView(quickLabel);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        addQuickCard(strip, "Search apps", R.drawable.ic_search, CYAN, this::searchApps);
        addQuickCard(strip, "Play Store", R.drawable.ic_apps, 0xff59e493, () -> openStore(""));
        addQuickCard(strip, "Screensaver", R.drawable.ic_picture, 0xff67baff, this::screensaverSettings);
        rows.addView(strip, new LinearLayout.LayoutParams(-1, -2));
        boolean online = networkStatus.getCurrentTextColor() == 0xff59e493;
        addPanelRow(rows, "Wi-Fi", online ? "Internet connected" : "No internet / disconnected", R.drawable.ic_wifi,
                networkStatus.getCurrentTextColor(), () -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        android.bluetooth.BluetoothManager bluetooth = getSystemService(android.bluetooth.BluetoothManager.class);
        String bluetoothState = "Open Bluetooth settings";
        try { if (bluetooth != null && bluetooth.getAdapter() != null) bluetoothState = bluetooth.getAdapter().isEnabled() ? "On" : "Off"; }
        catch (SecurityException unavailable) { /* Android 12+ can restrict adapter state. */ }
        addPanelRow(rows, "Bluetooth", bluetoothState, android.R.drawable.stat_sys_data_bluetooth, 0xff67baff,
                () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS));
        Runnable release = QuickControls.attach(this, rows);
        if (tv) addPanelRow(rows, "Input", "HDMI / Live TV", R.drawable.ic_input, 0xffbdd5e5, this::showInputs);
        addPanelRow(rows, "TV system settings", "Display, sound & device", R.drawable.ic_settings, 0xffbdd5e5, () -> openSetting(Settings.ACTION_SETTINGS));
        panel.setOnDismissListener(d -> release.run()); panel.show();
    }

    private void addQuickCard(LinearLayout strip, String title, int icon, int tint, Runnable action) {
        android.view.View card = GlassSettings.wideCard(this, title, icon, tint, () -> { if (panel != null) panel.dismiss(); action.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        int margin = dp(4);
        params.setMargins(margin, 0, margin, 0);
        strip.addView(card, params);
    }

    private void addPanelRow(LinearLayout rows, String label, String hint, int icon, int color, Runnable action) {
        rows.addView(GlassSettings.row(this, label, hint, icon, color, () -> { if (!label.equals("Launcher settings") && panel != null) panel.dismiss(); action.run(); }));
    }

    private void launcherSettings() {
        String[] labels = {"Hide / unhide apps", "App columns", "Tile size", "Focused app title size", "Clock size", "Dark mode", "Wallpaper", "Wallpaper darkness", "App colour glow", "Screensaver & weather", "Continue watching", "Profile name & photo", "Choose default Home", "Reset layout & unhide apps", "Rearrange apps"};
        Runnable[] choices = {
            this::hiddenApps,
            () -> chooseSetting("App columns", new String[]{"Automatic", "2", "3", "4", "5", "6"}, which -> {
                prefs.edit().putInt(tv ? "tv_columns" : "phone_columns", which == 0 ? 0 : which + 1).apply(); refreshApps();
            }),
            () -> chooseSetting("Tile size", new String[]{"Compact", "Large"}, which -> {
                prefs.edit().putBoolean("large_tiles", which == 1).putInt(tv ? "tv_columns" : "phone_columns", 0).apply(); refreshApps();
            }),
            () -> chooseSetting("Focused app title size", new String[]{"Small", "Normal", "Large", "Extra large"}, which -> {
                int size = new int[]{24,32,40,48}[which]; prefs.edit().putInt("focus_title_size", size).apply(); appHeading.setTextSize(size);
            }),
            () -> chooseSetting("Clock size", new String[]{"Small", "Normal", "Large"}, which -> {
                prefs.edit().putInt("clock_size", (tv ? new int[]{54,66,80} : new int[]{44,56,66})[which]).apply(); buildHome();
            }),
            () -> chooseSetting("Dark mode", new String[]{"Dark (Midnight Cyan)", "Automatic day / night", "Light"}, which -> {
                prefs.edit().putString("theme_mode", new String[]{"dark","auto","light"}[which]).apply(); applyAppearance();
            }),
            this::wallpaperSettings,
            () -> chooseSetting("Wallpaper darkness", new String[]{"Light", "Medium", "Dark"}, which -> {
                prefs.edit().putInt("background_dim", which * 20).apply(); sky.invalidate();
            }),
            () -> chooseSetting("App colour glow", new String[]{"On", "Off"}, which -> {
                prefs.edit().putBoolean("focus_glow", which == 0).apply(); sky.focusAccent = 0; sky.invalidate();
            }),
            this::screensaverSettings,
            () -> chooseSetting("Continue watching", new String[]{"Show below apps", "Hide row", "Allow TV Watch Next access"}, which -> {
                if (which == 2) continueWatching.requestAccess();
                else { prefs.edit().putBoolean("continue_watching", which == 0).apply(); continueWatching.reload(); }
            }),
            this::editProfile,
            () -> launch(new Intent(this, HomeSettingsActivity.class)),
            () -> new AlertDialog.Builder(this).setTitle("Reset layout and show all apps?").setPositiveButton("Reset", (dialog, which) -> {
                prefs.edit().remove("app_order").remove("hidden_apps").remove("tv_columns").remove("phone_columns").remove("large_tiles")
                        .remove("focus_title_size").remove("clock_size").remove("background_dim").remove("theme_mode")
                        .remove("wallpaper_mode").remove("focus_glow").remove("continue_watching").apply(); buildHome();
            }).setNegativeButton(android.R.string.cancel, null).show(),
            this::rearrangeApps
        };
        String[] hints = {"Choose which apps appear", "Number of cards per row", "Compact or large cards", "Name above the app grid", "12-hour clock with date", "Midnight, automatic or light", "Mountains or your own picture", "Keep the landscape visible", "Match the focused app", "Slideshow, weather & idle timer", "Resume titles after your apps", "Name and local profile picture", "Set this launcher as Home", "Restore the default layout", "Choose the order of app cards"};
        GlassSettings.options(this, "Launcher settings", labels, hints, which -> choices[which].run());
    }

    private void wallpaperSettings() {
        chooseSetting("Home wallpaper", new String[]{"Midnight mountains", "Solid midnight", "Choose picture", "Saved custom picture"}, which -> {
            if (which == 2) {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 8); }
                catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(this, "No image picker available on this device", Toast.LENGTH_LONG).show(); }
            } else if (which == 3) {
                if (!new File(getFilesDir(), "personal-wallpaper.jpg").exists()) { Toast.makeText(this, "Choose a picture first", Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("wallpaper_mode", "custom").apply(); loadWallpaper();
            } else {
                prefs.edit().putString("wallpaper_mode", which == 0 ? "mountains" : "solid").apply(); customWallpaper = null; sky.invalidate();
            }
        });
    }

    private void loadWallpaper() {
        if (!"custom".equals(prefs.getString("wallpaper_mode", "mountains"))) return;
        worker.execute(() -> {
            Bitmap image = BitmapFactory.decodeFile(new File(getFilesDir(), "personal-wallpaper.jpg").getPath());
            handler.post(() -> { if (!isDestroyed()) { customWallpaper = image; if (sky != null) sky.invalidate(); } });
        });
    }

    private void chooseSetting(String title, String[] options, java.util.function.IntConsumer selected) {
        String[] shown = options.clone();
        int current = -1;
        if (title.equals("Dark mode")) {
            String mode = prefs.getString("theme_mode", "dark");
            current = mode.equals("auto") ? 1 : mode.equals("light") ? 2 : 0;
        } else if (title.equals("Wallpaper darkness")) current = Math.min(2, prefs.getInt("background_dim", 0) / 20);
        else if (title.equals("App colour glow")) current = prefs.getBoolean("focus_glow", true) ? 0 : 1;
        if (current >= 0 && current < shown.length) shown[current] += "  ✓";
        GlassSettings.options(this, title, shown, null, selected);
    }

    private void refreshApps() {
        appHeading.setText("Your apps");
        if (cachedApps != null) renderApps(cachedApps); else loadApps();
        if (continueWatching != null) continueWatching.reload();
    }

    private void hiddenApps() {
        if (cachedApps == null) { loadApps(); return; }
        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", Collections.emptySet()));
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = GlassSettings.panel(this, "Show / hide apps", rows);
        for (App app : cachedApps) {
            String pkg = app.info.activityInfo.packageName;
            View row = GlassSettings.row(this, app.name, hidden.contains(pkg) ? "Hidden · select to show" : "Visible · select to hide",
                    R.drawable.ic_apps, CYAN, () -> {});
            row.setOnClickListener(v -> {
                if (!hidden.add(pkg)) hidden.remove(pkg);
                prefs.edit().putStringSet("hidden_apps", new HashSet<>(hidden)).apply();
                LinearLayout copy = (LinearLayout) ((LinearLayout) row).getChildAt(1);
                ((TextView) copy.getChildAt(1)).setText(hidden.contains(pkg) ? "Hidden · select to show" : "Visible · select to hide");
                row.setContentDescription(app.name + (hidden.contains(pkg) ? ", hidden" : ", visible"));
                refreshApps();
            });
            rows.addView(row);
        }
        dialog.show();
    }

    private void screensaverSettings() {
        chooseSetting("Online landscape screensaver", new String[]{"Preview", "Weather city", "Temperature unit", "Photo change interval", "Start after idle", "Set automatic screensaver"}, which -> {
            if (which == 0) launch(new Intent(this, LandscapeActivity.class));
            else if (which == 1) WeatherSettings.show(this);
            else if (which == 2) chooseSetting("Temperature unit", new String[]{"Celsius", "Fahrenheit"}, value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply());
            else if (which == 3) chooseSetting("Photo change interval", new String[]{"30 seconds", "1 minute", "2 minutes", "5 minutes"}, value -> prefs.edit().putInt("photo_seconds", new int[]{30,60,120,300}[value]).apply());
            else if (which == 4) chooseSetting("Start screensaver after idle", new String[]{"Off", "2 minutes", "5 minutes", "10 minutes", "15 minutes"}, value -> {
                prefs.edit().putInt("idle_screensaver_minutes", new int[]{0,2,5,10,15}[value]).apply(); armIdleScreensaver();
            });
            else launch(new Intent(this, ScreensaverSetupActivity.class));
        });
    }

    private void armIdleScreensaver() {
        handler.removeCallbacks(idleScreensaver);
        int minutes = prefs.getInt("idle_screensaver_minutes", 0);
        if (resumed && minutes > 0) handler.postDelayed(idleScreensaver, minutes * 60_000L);
    }

    @Override public void onUserInteraction() { super.onUserInteraction(); armIdleScreensaver(); }

    private void editProfile() {
        if (profile != null && profile.isShowing()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(8));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Your name");
        name.setText(prefs.getString("name", "Sadique Sadiq"));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);
        form.addView(button("Choose profile photo", () -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7);
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "No photo picker available on this device", Toast.LENGTH_LONG).show();
            }
        }));
        form.addView(button("Remove photo", () -> {
            File photo = new File(getFilesDir(), "personal-avatar.png");
            if (photo.exists() && !photo.delete()) {
                Toast.makeText(this, "Could not remove photo", Toast.LENGTH_SHORT).show();
                return;
            }
            avatarBitmap = null;
            refreshProfile();
        }));
        form.addView(text("Profile stays on this device. Google login is not used.", 13, MUTED));
        profile = new AlertDialog.Builder(this).setTitle(prefs.getBoolean("setup", false) ? "Your profile" : "Welcome · set up your Home")
                .setView(form).setPositiveButton("Save", null).setNegativeButton("Later", null).create();
        profile.setOnShowListener(d -> profile.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = PersonalHomeModel.name(name.getText().toString());
            if (value.isEmpty()) { name.setError("Enter your name"); return; }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply();
            refreshProfile();
            profile.dismiss();
        }));
        profile.show();
    }

    private void refreshProfile() {
        String name = prefs.getString("name", "Sadique Sadiq");
        userName.setText(name.isEmpty() ? "Your name" : name);
        if (avatarBitmap != null) avatar.setImageBitmap(avatarBitmap);
        else avatar.setImageResource(R.drawable.ic_person);
    }

    private void loadAvatar() {
        worker.execute(() -> {
            Bitmap image = BitmapFactory.decodeFile(new File(getFilesDir(), "personal-avatar.png").getPath());
            handler.post(() -> { if (!isDestroyed()) { avatarBitmap = image; refreshProfile(); } });
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 9) {
            if (result == RESULT_OK && data != null) {
                ArrayList<String> words = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (words != null && !words.isEmpty()) {
                    if (searchDialog == null) searchApps();
                    if (searchInput != null) { searchInput.setText(words.get(0)); searchInput.setSelection(searchInput.length()); }
                }
            }
            return;
        }
        if ((request != 7 && request != 8) || result != RESULT_OK || data == null || data.getData() == null) return;
        boolean wallpaper = request == 8;
        Uri uri = data.getData();
        worker.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                if (options.outWidth <= 0 || options.outHeight <= 0) throw new java.io.IOException("Invalid image");
                options.inJustDecodeBounds = false;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > (wallpaper ? 1920 : 512)) options.inSampleSize *= 2;
                Bitmap decoded;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    decoded = BitmapFactory.decodeStream(input, null, options);
                }
                if (decoded == null) throw new java.io.IOException("Invalid image");
                android.util.AtomicFile destination = new android.util.AtomicFile(new File(getFilesDir(), wallpaper ? "personal-wallpaper.jpg" : "personal-avatar.png"));
                FileOutputStream out = destination.startWrite();
                try {
                    if (!decoded.compress(wallpaper ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, wallpaper ? 85 : 100, out)) throw new java.io.IOException("Cannot save image");
                    destination.finishWrite(out);
                } catch (Exception error) { destination.failWrite(out); throw error; }
                handler.post(() -> {
                    if (isDestroyed()) return;
                    if (wallpaper) { customWallpaper = decoded; prefs.edit().putString("wallpaper_mode", "custom").apply(); sky.invalidate(); }
                    else { avatarBitmap = decoded; refreshProfile(); }
                });
            } catch (Exception error) {
                handler.post(() -> { if (!isDestroyed()) Toast.makeText(this, "Could not read this photo. Choose another image.", Toast.LENGTH_LONG).show(); });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        armIdleScreensaver();
        handler.removeCallbacks(tick);
        tick.run();
        updateNetwork();
        continueWatching.start();
        if (pageWidth > 0) loadApps();
        if (connectivity != null && !networkRegistered) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback); networkRegistered = true; }
            catch (RuntimeException unavailable) { /* Snapshot above remains available. */ }
        }
    }

    @Override protected void onPause() {
        resumed = false;
        if (continueWatching != null) continueWatching.stop();
        handler.removeCallbacks(idleScreensaver);
        handler.removeCallbacks(tick);
        if (connectivity != null && networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (panel != null) panel.dismiss();
        if (searchDialog != null) searchDialog.dismiss();
        focusFirstApp();
        handleHomeAction(intent);
    }

    private void handleHomeAction(Intent intent) {
        if (intent == null) return;
        if (MainActivity.ACTION_VIEW_INPUTS.equals(intent.getAction())) handler.post(() -> showInputs());
        else if (MainActivity.ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) handler.post(() -> launcherSettings());
        else if (MainActivity.ACTION_SHOW_SETTINGS.equals(intent.getAction())) handler.post(this::showSettings);
    }

    @Override public void onBackPressed() {
        focusFirstApp();
    }

    private void focusFirstApp() {
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == ContinueWatchingView.PERMISSION_REQUEST && continueWatching != null) continueWatching.reload();
    }

    @Override protected void onDestroy() {
        if (panel != null) panel.dismiss();
        if (profile != null) profile.dismiss();
        if (searchDialog != null) searchDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        if (continueWatching != null) continueWatching.stop();
        worker.shutdownNow();
        super.onDestroy();
    }

    /** Static dark wallpaper and a single focus glow; no continuously running animation. */
    private final class SkyClock extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Never share the bitmap paint with translucent sun/moon or focus effects.
        private final Paint wallpaperPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Bitmap backdrop = BitmapFactory.decodeResource(getResources(), R.drawable.personal_wallpaper);
        int minute, focusAccent;
        float clockY = -1, focusX, focusY, focusRadius;
        SkyClock() { super(PersonalHomeActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(Color.WHITE);
            paint.setShader(null);
            String theme = prefs.getString("theme_mode", "dark");
            float day = HomeAppearance.daylight(theme, minute);
            int top = blend(0xff020817, 0xff145d85, day), bottom = blend(0xff07172b, 0xff092b4a, day);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint); paint.setShader(null);
            String mode = prefs.getString("wallpaper_mode", "mountains");
            Bitmap picture = mode.equals("custom") && customWallpaper != null ? customWallpaper : backdrop;
            float y = clockY >= 0 ? clockY : dp(150);
            if (!mode.equals("solid") && picture != null) {
                boolean mountains = picture == backdrop;
                float[] frame = HomeAppearance.frame(getWidth(), getHeight(), picture.getWidth(), picture.getHeight(), y, mountains);
                canvas.drawBitmap(picture, null, new android.graphics.RectF(frame[0], frame[1], frame[2], frame[3]), wallpaperPaint);
                int shade = HomeAppearance.shade(theme, minute, prefs.getInt("background_dim", 0));
                canvas.drawColor((shade << 24) | 0x00051327);
                if (mountains && getHeight() > getWidth()) {
                    // Blend the portrait hero into midnight below the lake, keeping the ridge visible.
                    int base = blend(0xff07172b, 0xff092b4a, day);
                    paint.setShader(new LinearGradient(0, frame[3] * .78f, 0, frame[3],
                            base & 0x00ffffff, base, Shader.TileMode.CLAMP));
                    canvas.drawRect(0, frame[3] * .78f, getWidth(), frame[3], paint);
                    paint.setShader(null);
                }
            }
            if (focusAccent != 0 && focusRadius > 0) {
                int glow = (focusAccent & 0x00ffffff) | 0x80000000;
                paint.setShader(new RadialGradient(focusX, focusY, focusRadius, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(focusX, focusY, focusRadius, paint); paint.setShader(null);
            }
        }
    }

    private android.graphics.drawable.Drawable tileBorder(int accent, boolean focused) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT); border.setCornerRadius(dp(16));
        border.setStroke(dp(focused ? 3 : 1), focused ? accent : 0x4028435e);
        GradientDrawable bevel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x20ffffff, 0x00ffffff, 0x28000000});
        bevel.setCornerRadius(dp(16));
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bevel, border});
    }

    private static int blend(int a, int b, float part) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * part),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * part),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * part));
    }
}
