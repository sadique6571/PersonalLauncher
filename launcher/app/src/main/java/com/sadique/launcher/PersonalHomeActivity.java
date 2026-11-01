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
import android.graphics.Outline;
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
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
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
    /** Dark-on-light text scheme, used when the wallpaper behind the text is bright. */
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** Dedicated app-loading worker: icon scans never block avatar/wallpaper/import, and a new scan cancels the old. */
    private final ExecutorService appWorker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private LinearLayout page, header, actions, clockLayout, appBody;    private GridLayout grid;
    private TextView userName, time, date, ampm, networkStatus, appHeading, greeting, homeWeather;
    private ImageView avatar;
    private Bitmap avatarBitmap;
    private Bitmap customWallpaper;
    private ContinueWatchingView continueWatching;
    private SkyClock sky;
    private ConnectivityManager connectivity;
    private boolean networkRegistered, resumed, tv;
    private int pageWidth;
    /** Wallpaper-adaptive text: true while the region behind the text is bright (dark text in use). */
    /** Effective clock text size in sp (after the compact-screen cap), for fitClockText(). */
    private int clockTextSize = 68;
    /** Entrance animation plays only on the very first grid render, never on rebuilds. */
    private boolean firstGridRender = true;
    /** Debounced rebuild: coalesces rapid settings changes into one buildHome(). */
    private boolean buildHomePending = false;
    /** elapsedRealtime at onCreate entry, for first-frame timing (BootLog). */
    private long onCreateStartMs;
    /** First buildHome() also logs the first drawn frame; rebuilds only log their own duration. */
    private boolean firstBuild = true;
    private List<App> cachedApps;
    /** Move mode: the app being carried, and the visible package order while it moves. Null when off. */
    private App carriedApp;
    private List<String> moveOrder;
    /** Live tiles by package, for progressive icon fill-in. Rebuilt with the grid. */
    private final java.util.Map<String, View> tileViews = new java.util.HashMap<>();
    /** Package of the focused tile, restored after grid rebuilds. */
    private String focusedPkg;
    /** Last day/night state; when it flips, tile surfaces are refreshed to match. */
    private boolean lastLight = false;
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
            boolean light = appearanceDaylight() >= .5f;
            if (light != lastLight) {
                lastLight = light;
                refreshTileSurfaces();
                updateOnlineWallpaper();
            }
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    /** Re-applies backgrounds so tiles match the current day/night theme without a rebuild. */
    private void refreshTileSurfaces() {
        for (java.util.Map.Entry<String, View> entry : tileViews.entrySet()) {
            View tile = entry.getValue();
            boolean focused = tile.isFocused();
            tile.setBackground(surface(focused));
            Object tag = tile.getTag();
            int accent = tag instanceof Integer ? (Integer) tag : CYAN;
            tile.setForeground(tileBorder(accent, focused));
        }
        if (sky != null) sky.invalidate();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        onCreateStartMs = android.os.SystemClock.elapsedRealtime();
        BootLog.mark("home onCreate");
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
        lastLight = appearanceDaylight() >= .5f;
        buildHome();
        loadAvatar();
        loadWallpaper();
        if (!prefs.getBoolean("setup", false)) handler.post(() -> editProfile());
        else { handleHomeAction(getIntent()); handler.post(() -> maybeOfferDefaultHome()); }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private float appearanceDaylight() {
        Calendar now = Calendar.getInstance();
        return HomeAppearance.daylight(prefs.getString("theme_mode", "dark"), now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
    }

    private void applyAppearance() {
        // Recreate surfaces as well as the wallpaper; changing only the canvas left cards unchanged.
        buildHomeSoon();
    }

    private final Runnable buildHomeTask = new Runnable() {
        @Override public void run() {
            buildHomePending = false;
            buildHome();
        }
    };

    /**
     * Coalesces rapid settings changes into a single rebuild (250 ms window).
     * A direct buildHome() call cancels any pending debounced one.
     */
    private void buildHomeSoon() {
        if (buildHomePending) return;
        buildHomePending = true;
        handler.postDelayed(buildHomeTask, 250);
    }

    /**
     * Width the app grid can actually use: the page content width minus the
     * appBody panel's side padding. Sizing tiles from pageWidth alone overflowed
     * the panel and clipped the rightmost column (portrait and landscape).
     */
    private int gridWidth() {
        int pad = appBody == null ? dp(16) : appBody.getPaddingLeft() + appBody.getPaddingRight();
        return Math.max(dp(120), pageWidth - pad);
    }

    /** Logo size inside tiles from settings: small / normal / large. */
    private float logoScale() {
        return new float[]{.8f, 1f, 1.2f}[Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)))];
    }

    /** Current theme accent color: Midnight Cyan, Sunset Orange, Deep Purple, or Custom. */
    private int accentColor() {
        switch (prefs.getInt("theme_name", 0)) {
            case 1: return 0xffff9a5c; // Sunset Orange
            case 2: return 0xffb388ff; // Deep Purple
            case 3: return prefs.getInt("custom_accent", CYAN);
            default: return CYAN; // Midnight Cyan
        }
    }

    /** Tile corner radius from settings: sharp / rounded / round. */
    private int tileRadius() {
        return dp(new int[]{4, 16, 28}[Math.max(0, Math.min(2, prefs.getInt("tile_corners", 1)))]);
    }

    private GradientDrawable surface(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        float day = appearanceDaylight();
        // Theme-tinted glass (user 2026-11-01): the dark base takes the theme's hue.
        int accent = accentColor();
        float tint = 0.3f;
        int night = blend(focused ? 0xff153d5b : 0xff092038, accent, tint);
        int dayC = blend(focused ? 0xff407596 : 0xff335975, accent, tint);
        shape.setColor(blend(night, dayC, day));
        // Glass intensity (user 2026-11-01): 0=airy, 100=solid. Focused stays a touch brighter.
        int intensity = Math.max(0, Math.min(100, prefs.getInt("glass_alpha", 70)));
        int alpha = 100 + intensity * 155 / 100;
        shape.setAlpha(focused ? Math.min(255, alpha + 30) : alpha);
        shape.setCornerRadius(tileRadius());
        shape.setStroke(dp(focused ? 3 : 1), focused ? accent : 0xff28435e);
        return shape;
    }

    private GradientDrawable avatarSurface(boolean focused) {
        GradientDrawable shape = surface(focused);
        if (prefs.getInt("avatar_shape", 0) == 0) shape.setShape(GradientDrawable.OVAL);
        else shape.setCornerRadius(dp(16));
        return shape;
    }

    private TextView text(String value, int size, int color) {
        // Outline removed 2026-11-01 (user: "buri lag rahi hai") — plain text with drop shadow only.
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
        long buildMs = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(buildHomeTask);
        buildHomePending = false;
        if (continueWatching != null) continueWatching.stop();
        pageWidth = 0;
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xff031126);
        SkyClock oldSky = sky;
        if (oldSky != null) handler.removeCallbacks(oldSky.crossfadeStep);
        sky = new SkyClock();
        // Keep the already-loaded wallpaper across rebuilds: a settings change
        // must never flash the default wallpaper before the online photo
        // re-fetches. (User 2026-11-01.)
        if (oldSky != null) sky.adoptWallpapers(oldSky);
        // Slight overscan so parallax shifts never reveal edges.
        sky.setScaleX(1.06f);
        sky.setScaleY(1.06f);
        root.addView(sky, new ViewGroup.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(tv ? 32 : 16);
        page.setPadding(inset, dp(14), inset, dp(12));
        root.addView(page, new ViewGroup.LayoutParams(-1, -1));
        // Cinematic shade: subtle dark gradients top and bottom for a film-like frame.
        GradientDrawable topShade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x00000000});
        View topBar = new View(this);
        topBar.setBackground(topShade);
        topBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(topBar, new android.widget.FrameLayout.LayoutParams(-1, dp(110), Gravity.TOP));
        GradientDrawable bottomShade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x66000000, 0x00000000});
        View bottomBar = new View(this);
        bottomBar.setBackground(bottomShade);
        bottomBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(bottomBar, new android.widget.FrameLayout.LayoutParams(-1, dp(110), Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            page.setPadding(inset + insets.getSystemWindowInsetLeft(), dp(14) + insets.getSystemWindowInsetTop(),
                    inset + insets.getSystemWindowInsetRight(), dp(12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        applyHeaderBackground();
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
        applyAppFont(userName);
        identity.addView(userName);
        greeting = text(greetingText(), 13, MUTED);
        applyAppFont(greeting);
        if (prefs.getBoolean("hide_greeting", false)) greeting.setVisibility(View.GONE);
        identity.addView(greeting);
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
        homeWeather = text("", 13, MUTED);
        homeWeather.setGravity(Gravity.CENTER);
        homeWeather.setPadding(dp(10), dp(8), dp(10), dp(8));
        actions.addView(homeWeather, 0, new LinearLayout.LayoutParams(-2, dp(44)));
        iconAction("Search", R.drawable.ic_search, this::searchApps);
        iconAction("Settings", R.drawable.ic_settings, this::showSettings);
        if (tv) {
            header.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        } else {
            page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }
        clockLayout = new LinearLayout(this);
        LinearLayout clock = clockLayout;
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER);
        boolean compact = getResources().getConfiguration().screenHeightDp < 480;
        clock.setPadding(0, dp(compact ? 0 : tv ? 10 : 16), 0, dp(compact ? 8 : 12));
        int clockSize = prefs.getInt("clock_size", tv ? 52 : 44);
        // One-time migration (2026-10-31): old sizes were too big, map to new scale.
        int[] oldSizes = tv ? new int[]{66, 80, 96} : new int[]{56, 68, 80};
        int[] newSizes = tv ? new int[]{42, 52, 62} : new int[]{36, 44, 52};
        for (int i = 0; i < oldSizes.length; i++) {
            if (clockSize == oldSizes[i]) {
                clockSize = newSizes[i];
                prefs.edit().putInt("clock_size", clockSize).apply();
                break;
            }
        }
        if (compact) clockSize = Math.min(clockSize, 32);
        time = text("", clockSize, TEXT);
        time.setGravity(Gravity.CENTER);
        time.setSingleLine(true);
        // Bold like the demo.
        time.setTypeface(time.getTypeface(), android.graphics.Typeface.BOLD);
        // Clock shadow for readability on bright wallpapers (toggle).
        if (prefs.getBoolean("clock_shadow", true)) {
            time.setShadowLayer(dp(8), 0, dp(3), 0xcc000000);
        }
        String[] clockFonts = {"sans-serif-light", "serif", "monospace"};
        time.setTypeface(android.graphics.Typeface.create(
                clockFonts[Math.max(0, Math.min(2, prefs.getInt("clock_font", 0)))], android.graphics.Typeface.NORMAL));
        // Analog mode: hide the digital time (keeps layout for clockY), SkyClock draws the face.
        boolean analogClock = prefs.getInt("clock_style", 0) == 1;
        // AM/PM is a separate view, not a span: it can never be clipped when
        // seconds make the string longest. (User 2026-11-01.)
        ampm = text("", Math.max(14, (int) (clockSize * 0.42f)), TEXT);
        ampm.setSingleLine(true);
        if (prefs.getBoolean("clock_shadow", true)) {
            ampm.setShadowLayer(dp(4), 0, dp(2), 0xcc000000);
        }
        if (analogClock) {
            time.setVisibility(View.INVISIBLE);
            ampm.setVisibility(View.INVISIBLE);
        }
        // No auto-size: fitClockText() sizes precisely.
        time.setAutoSizeTextTypeWithDefaults(android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        // Horizontal padding prevents clipping from the shadow layer.
        time.setPadding(dp(12), dp(2), dp(4), dp(10));
        clockTextSize = clockSize;
        // Analog face radius follows the clock size (diameter ~= digital height).
        if (sky != null) sky.clockRadius = dp(clockSize * 0.85f);
        date = text("", tv ? 14 : 13, MUTED);
        if (prefs.getBoolean("clock_shadow", true)) {
            date.setShadowLayer(dp(6), 0, dp(2), 0xcc000000);
        }
        date.setGravity(Gravity.CENTER);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER);
        timeRow.addView(time, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams ampmParams = new LinearLayout.LayoutParams(-2, -2);
        ampmParams.gravity = Gravity.BOTTOM;
        ampmParams.bottomMargin = dp(6);
        timeRow.addView(ampm, ampmParams);
        clock.addView(timeRow, new LinearLayout.LayoutParams(-1, dp(clockSize + (compact ? 10 : 36))));
        clock.addView(date);
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
        grid = new GridLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        appBody = new LinearLayout(this);
        appBody.setOrientation(LinearLayout.VERTICAL);
        // Grid backdrop: soft panel behind the apps so tiles stand out from the wallpaper.
        GradientDrawable gridBackdrop = new GradientDrawable();
        gridBackdrop.setColor(0x33000000);
        gridBackdrop.setCornerRadius(dp(20));
        appBody.setBackground(gridBackdrop);
        appBody.setPadding(dp(8), dp(4), dp(8), dp(8));
        // The drawer panel stays fixed on screen; only the tiles scroll inside it,
        // clipped to the rounded corners so scrolling stops under the header.
        final int drawerRadius = dp(20);
        appBody.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), drawerRadius);
            }
        });
        appBody.setClipToOutline(true);
        ScrollView drawerScroll = new ScrollView(this);
        drawerScroll.setFillViewport(true);
        drawerScroll.setVerticalScrollBarEnabled(false);
        LinearLayout drawerContent = new LinearLayout(this);
        drawerContent.setOrientation(LinearLayout.VERTICAL);
        drawerContent.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        continueWatching = new ContinueWatchingView(this, label -> appHeading.setText(label));
        drawerContent.addView(continueWatching, new LinearLayout.LayoutParams(-1, -2));
        drawerScroll.addView(drawerContent, new ViewGroup.LayoutParams(-1, -2));
        appBody.addView(drawerScroll, new LinearLayout.LayoutParams(-1, -1));
        // Scrollable content: clock only. The "Your apps" header and the
        // drawer panel stay fixed below; tiles scroll inside the panel.
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        if (prefs.getBoolean("hide_clock", false)) clock.setVisibility(View.GONE);
        int align = prefs.getInt("clock_align", 1);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-2, -2);
        clockParams.gravity = new int[]{Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END}[align];
        scrollContent.addView(clock, clockParams);
        scroll.addView(scrollContent, new ViewGroup.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        page.addView(appHeading);
        page.addView(appBody, new LinearLayout.LayoutParams(-1, 0, 1));
        // Focus-glow tracker: keep the wallpaper glow on the focused tile no matter
        // which scroller moved.
        View.OnScrollChangeListener glowTracker = (v, x, y, oldX, oldY) -> {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof Integer && prefs.getBoolean("focus_glow", true)) {
                int[] position = new int[2], origin = new int[2];
                focused.getLocationInWindow(position); sky.getLocationInWindow(origin);
                sky.focusX = position[0] - origin[0] + focused.getWidth() / 2f;
                sky.focusY = position[1] - origin[1] + focused.getHeight() / 2f;
                sky.invalidate();
            }
        };
        // Per-view adaptive text: update each label's dark/light scheme as it moves
        // over the wallpaper during scroll. (User request 2026-10-31.)
        drawerScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            adaptVisibleTextColors();
            glowTracker.onScrollChange(v, scrollX, scrollY, oldScrollX, oldScrollY);
        });
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            adaptVisibleTextColors();
            glowTracker.onScrollChange(v, scrollX, scrollY, oldScrollX, oldScrollY);
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
        adaptTextColors();
        // buildHome() replaces the SkyClock instance, which drops in-memory wallpapers.
        // Re-fetch online + reload custom from disk so a settings change never
        // resets the wallpaper to default. (User 2026-10-31.)
        if ("custom".equals(prefs.getString("wallpaper_mode", "mountains")) && customWallpaper == null) {
            loadWallpaper();
        }
        updateOnlineWallpaper();
        WindowManager.LayoutParams brightness = getWindow().getAttributes();
        brightness.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(brightness);
        if (resumed) continueWatching.start();
        BootLog.mark("home buildHome", buildMs);
        if (firstBuild) {
            firstBuild = false;
            // First actually-drawn frame, once: onCreate duration alone is not visible startup time.
            root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                    BootLog.mark("home first frame", onCreateStartMs);
                    return true;
                }
            });
        }
    }

    /** Time-based greeting, e.g. "Subah Bakhair". */
    private String greetingText() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return "Subah Bakhair";
        if (hour >= 12 && hour < 17) return "Dopahar Bakhair";
        if (hour >= 17 && hour < 22) return "Shaam Bakhair";
        return "Shab Bakhair";
    }

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        boolean seconds = prefs.getBoolean("clock_seconds", false);
        boolean is24 = prefs.getBoolean("time_24h", false);
        String value;
        String ampmText;
        if (is24) {
            value = String.format(Locale.getDefault(), "%02d:%02d",
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND));
            ampmText = "";
        } else {
            int h12 = now.get(Calendar.HOUR) == 0 ? 12 : now.get(Calendar.HOUR);
            value = String.format(Locale.getDefault(), "%02d:%02d", h12, now.get(Calendar.MINUTE));
            if (seconds) value += String.format(Locale.getDefault(), ":%02d", now.get(Calendar.SECOND));
            ampmText = now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";
        }
        fitClockText(value);
        if (ampm != null) {
            ampm.setText(ampmText);
            boolean analog = prefs.getInt("clock_style", 0) == 1;
            ampm.setVisibility(analog ? View.INVISIBLE : (ampmText.isEmpty() ? View.GONE : View.VISIBLE));
        }
        date.setText(new java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(now.getTime()));
        sky.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        sky.clockStyle = prefs.getInt("clock_style", 0);
        sky.invalidate();
        if (greeting != null) greeting.setText(greetingText());
    }

    /**
     * Sizes the clock text so the whole string always fits. AM/PM lives in its
     * own view now, so there is no span left to mismeasure. Measured precisely
     * with a StaticLayout, then scaled down only if needed; never below 26sp.
     */
    private void fitClockText(CharSequence text) {
        if (time == null) return;
        float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = (pageWidth > 0 ? pageWidth : screenW) - dp(24);
        if (maxWidth <= 0) maxWidth = screenW - dp(24);
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setTypeface(time.getTypeface());
        float sizePx = clockTextSize * density;
        paint.setTextSize(sizePx);
        android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(
                text, 0, text.length(), paint, Integer.MAX_VALUE)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
        float w = 0;
        for (int i = 0; i < layout.getLineCount(); i++) w = Math.max(w, layout.getLineWidth(i));
        if (w > maxWidth && w > 0) sizePx = Math.max(sizePx * maxWidth / w, 26 * density);
        time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sizePx);
        time.setText(text);
    }

    /** Per-second ticker, only active when clock seconds are enabled. */
    private final Runnable secondTick = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (!resumed || !prefs.getBoolean("clock_seconds", false)) return;
            updateClock();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    /** Picks and loads the right online wallpaper (day/night + slideshow aware). */
    private int slideshowIndex = 0;
    private void updateOnlineWallpaper() {
        if (sky == null || prefs.getInt("wallpaper_source", 0) != 1) return;
        // Bing daily: always today's wallpaper (idx=0). No slideshow cycling —
        // it changes automatically every day. (User 2026-10-31.)
        sky.loadOnlinePhoto(0);
    }

    private final Runnable slideshowStep = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (!resumed) return;
            int minutes = prefs.getInt("wallpaper_slideshow", 0);
            if (minutes > 0 && prefs.getInt("wallpaper_source", 0) == 1) {
                slideshowIndex++;
                updateOnlineWallpaper();
                handler.postDelayed(this, minutes * 60_000L);
            }
        }
    };

    private void armSlideshow() {
        handler.removeCallbacks(slideshowStep);
        int minutes = prefs.getInt("wallpaper_slideshow", 0);
        if (minutes > 0 && resumed && prefs.getInt("wallpaper_source", 0) == 1) {
            handler.postDelayed(slideshowStep, minutes * 60_000L);
        }
    }
    /** Refreshes the Continue Watching row (banner rows were removed 2026-11-01). */
    private void updateContentRows() {
        if (continueWatching != null) continueWatching.reload();
    }

    /** Weather cache validity: 15 minutes. */
    private static final long WEATHER_CACHE_MS = 15 * 60_000L;

    /**
     * Small weather pill in the header. Shows cached weather immediately;
     * refreshes only when stale, or on city/unit change.
     */
    private void refreshHomeWeather() {
        if (homeWeather == null) return;
        // Show cached value immediately.
        String cached = prefs.getString("weather_cached", null);
        long cachedAt = prefs.getLong("weather_cached_at", 0);
        String cacheKey = weatherCacheKey();
        boolean cacheValid = cached != null
                && System.currentTimeMillis() - cachedAt < WEATHER_CACHE_MS
                && cacheKey.equals(prefs.getString("weather_cache_key", ""));
        if (cacheValid) {
            homeWeather.setText(cached);
            homeWeather.setVisibility(View.VISIBLE);
            // Schedule next refresh for when the cache expires.
            handler.removeCallbacks(weatherRefresh);
            long delay = WEATHER_CACHE_MS - (System.currentTimeMillis() - cachedAt);
            handler.postDelayed(weatherRefresh, Math.max(delay, 60_000L));
            return;
        }
        // Show stale cache while fetching (with reduced opacity to indicate staleness).
        if (cached != null) {
            homeWeather.setText(cached);
            homeWeather.setAlpha(0.6f);
            homeWeather.setVisibility(View.VISIBLE);
        }
        worker.execute(() -> {
            String value;
            try {
                boolean fahrenheit = prefs.getBoolean("weather_fahrenheit", false);
                double lat = Double.parseDouble(prefs.getString("weather_lat", Double.toString(LandscapeModel.LATITUDE)));
                double lon = Double.parseDouble(prefs.getString("weather_lon", Double.toString(LandscapeModel.LONGITUDE)));
                value = LandscapeModel.parseWeather(OnlineContent.json(LandscapeModel.weatherUrl(lat, lon, fahrenheit)), fahrenheit);
            } catch (Exception unavailable) {
                value = null;
            }
            String text = value;
            handler.post(() -> {
                if (isDestroyed() || homeWeather == null) return;
                if (text != null) {
                    // Cache the fresh value.
                    prefs.edit()
                            .putString("weather_cached", text)
                            .putLong("weather_cached_at", System.currentTimeMillis())
                            .putString("weather_cache_key", weatherCacheKey())
                            .apply();
                    homeWeather.setText(text);
                    homeWeather.setAlpha(1f);
                    homeWeather.setVisibility(View.VISIBLE);
                } else if (cached == null) {
                    // No cache and fetch failed: hide.
                    homeWeather.setVisibility(View.GONE);
                }
                // If fetch failed but we have stale cache, keep showing it (dimmed).
                handler.removeCallbacks(weatherRefresh);
                handler.postDelayed(weatherRefresh, WEATHER_CACHE_MS);
            });
        });
    }

    /** Cache key: changes when city or units change, invalidating the cache. */
    private String weatherCacheKey() {
        return prefs.getString("weather_lat", "") + ","
                + prefs.getString("weather_lon", "") + ","
                + prefs.getBoolean("weather_fahrenheit", false);
    }

    private final Runnable weatherRefresh = this::refreshHomeWeather;

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
        appWorker.execute(() -> {
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
                if (generation != appGeneration) return;
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
                        AppArtwork artwork = AppArtwork.create(getResources(), logo, logoScale());
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
        int avail = gridWidth();
        int columns = PersonalHomeModel.columns((int) (avail / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0), prefs.getBoolean("large_tiles", false));
        grid.setColumnCount(columns);
        int width = avail / columns;
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        for (App app : orderedApps(apps)) {
            if (!hidden.contains(app.info.activityInfo.packageName)) addAppTile(grid, app, width);
        }
        // Staggered entrance: tiles slide in one by one — only on the very first
        // render. Replaying it on every rebuild was the visible "launcher keeps
        // refreshing" flash. Stagger capped so large libraries don't animate for seconds.
        if (prefs.getBoolean("app_entrance", true) && firstGridRender) {
            int count = grid.getChildCount();
            for (int i = 0; i < count; i++) {
                View tile = grid.getChildAt(i);
                tile.setTranslationX(-dp(60));
                tile.setAlpha(0f);
                tile.animate().translationX(0).alpha(1f)
                        .setDuration(400).setStartDelay(Math.min(i * 70, 600))
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        }
        firstGridRender = false;
        if (grid.getChildCount() == 0) {
            appHeading.setText("No visible apps");
            Toast.makeText(this, "Unhide apps in Settings → Launcher settings", Toast.LENGTH_LONG).show();
        }
        // Restore D-pad focus to the previously focused app when it is still visible.
        View restore = focusedPkg == null ? null : tileViews.get(focusedPkg);
        if (restore != null) restore.requestFocus();
        else if (grid.getChildCount() > 0 && grid.findFocus() == null) focusFirstApp();
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
        int avail = gridWidth();
        int columns = PersonalHomeModel.columns((int) (avail / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0), prefs.getBoolean("large_tiles", false));
        grid.setColumnCount(columns);
        int width = avail / columns;
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

    /** System navigation sounds: tick on arrows, click on OK. */
    private void playNavSound(int keyCode) {
        android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
        if (audio == null) return;
        int fx = -1;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: fx = android.media.AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: fx = android.media.AudioManager.FX_KEY_CLICK; break;
        }
        if (fx != -1) audio.playSoundEffect(fx);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) playNavSound(event.getKeyCode());
        // Press effect: tile dips slightly the moment OK is pressed.
        if (event.getAction() == KeyEvent.ACTION_DOWN && carriedApp == null
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            View focused = getCurrentFocus();
            if (focused != null && tileViews.containsValue(focused)) {
                focused.animate().scaleX(.94f).scaleY(.94f).setDuration(80).start();
            }
        }
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
            tile.setForeground(tileBorder(accentColor(), true));
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
            if (focus) focusedPkg = app.info.activityInfo.packageName;
            if (!moving) {
                // Focus zoom: gentle scale animation.
                v.animate().scaleX(focus ? 1.05f : 1f).scaleY(focus ? 1.05f : 1f).setDuration(150).start();
            }
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
                // Parallax: wallpaper drifts slightly opposite to the focused tile,
                // vertical only — never sideways. (User 2026-11-01.)
                float cy = position[1] + tile.getHeight() / 2f;
                android.graphics.Point size = new android.graphics.Point();
                getWindowManager().getDefaultDisplay().getSize(size);
                sky.animate().translationX(0)
                        .translationY((size.y / 2f - cy) / size.y * dp(36))
                        .setDuration(350).start();
            } else sky.focusAccent = 0;
            sky.invalidate();
        });
        tile.setOnClickListener(v -> {
            Runnable open = () -> {
                launch(new Intent(Intent.ACTION_MAIN)
                        .setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            };
            if (carriedApp != null) { open.run(); return; }
            v.animate().scaleX(1.08f).scaleY(1.08f).alpha(.7f).setDuration(120).withEndAction(() -> {
                v.setScaleX(1.05f); v.setScaleY(1.05f); v.setAlpha(1f);
                open.run();
            }).start();
        });
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
        int spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)));
        int hm = new int[]{dp(4), dp(8), dp(14)}[spacing];
        int vm = new int[]{dp(5), dp(10), dp(16)}[spacing];
        position.width = width - hm * 2;
        position.height = Math.round(position.width * 9f / 16f);
        position.setMargins(hm, vm, hm, vm);
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

    /** YouTube search: tries the YouTube TV app first, falls back to browser. */
    private void youtubeSearch(String query) {
        String encoded = Uri.encode(query);
        // Try YouTube TV app.
        Intent appSearch = new Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube.tv")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryLaunch(appSearch)) return;
        // Try regular YouTube app.
        Intent mobileSearch = new Intent(Intent.ACTION_SEARCH)
                .setPackage("com.google.android.youtube")
                .putExtra("query", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryLaunch(mobileSearch)) return;
        // Fall back to browser.
        launch(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + encoded))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Returns true if any installed (non-hidden) app matches the query. */
    private boolean appMatches(String query) {
        if (cachedApps == null) return true; // Apps not loaded yet; show search.
        String q = query.trim().toLowerCase(Locale.ROOT);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        for (App app : cachedApps) {
            if (hidden.contains(app.info.activityInfo.packageName)) continue;
            if ((app.name + " " + app.info.activityInfo.packageName).toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
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
        addPanelRow(rows, "Launcher settings", "Layout, wallpaper & apps", R.drawable.ic_settings, accentColor(), this::launcherSettings);
        TextView quickLabel = GlassSettings.label(this, "Quick actions", 12, 0xff9bb6cb);
        quickLabel.setPadding(dp(4), dp(10), 0, dp(4));
        rows.addView(quickLabel);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        addQuickCard(strip, "Search apps", R.drawable.ic_search, accentColor(), this::searchApps);
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
                () -> launch(SystemSettings.bluetoothAccessoriesIntent(this)));
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

    /** Custom accent color picker (8 colors). Applies the Custom theme. */
    private void chooseAccentColor() {
        String[] names = {"Cyan", "Orange", "Purple", "Green", "Red", "Yellow", "Pink", "White"};
        int[] colors = {0xff40d9ff, 0xffff9a5c, 0xffb388ff, 0xff69f0ae, 0xffff5252, 0xffffea00, 0xffff80ab, 0xffffffff};
        chooseSetting("Accent color", names, which -> {
            prefs.edit().putInt("theme_name", 3).putInt("custom_accent", colors[which]).apply();
            applyAppearance(); refreshApps();
        });
    }

    private void launcherSettings() {
        String[] cats = {"Tiles", "Clock", "Theme", "Wallpaper", "Apps", "System"};
        String[] catHints = {"App cards layout", "Time display", "Colors & appearance", "Background", "Your apps", "Launcher system"};
        int[] catIcons = {R.drawable.ic_apps, R.drawable.ic_clock, R.drawable.ic_palette,
                R.drawable.ic_picture, R.drawable.ic_apps, R.drawable.ic_settings};
        GlassSettings.options(this, "Launcher settings", cats, catHints, catIcons, which -> {
            switch (which) {
                case 0: tileSettings(); break;
                case 1: clockSettings(); break;
                case 2: themeSettings(); break;
                case 3: wallpaperMenu(); break;
                case 4: appMenu(); break;
                default: systemMenu(); break;
            }
        });
    }

    /** Subtle background behind the header (toggle in settings). */
    /**
     * Applies the performance profile. In Light mode, disables expensive effects:
     * crossfade, parallax, entrance cascade, clock seconds, auto-scroll, and shadows.
     */
    private void applyPerformanceProfile() {
        int tier = MemoryProfile.effectiveTier(this, prefs);
        if (tier == MemoryProfile.TIER_LIGHT) {
            // Light mode defaults: static, minimal.
            // Note: we don't force-overwrite user prefs here; the drawing code
            // checks MemoryProfile.allow*() at draw time. This just ensures
            // the crossfade buffer isn't allocated.
            if (sky != null) sky.releaseTransient();
        }
    }

    /** Returns true if the named effect is allowed in the current performance tier. */
    private boolean perfAllows(String effect) {
        int tier = MemoryProfile.effectiveTier(this, prefs);
        switch (effect) {
            case "crossfade": return MemoryProfile.allowCrossfade(tier);
            case "parallax": return MemoryProfile.allowParallax(tier);
            case "entrance": return MemoryProfile.allowEntrance(tier);
            case "autoscroll": return MemoryProfile.allowAutoScroll(tier);
            default: return true;
        }
    }

    /** Header background mode: 0=Off, 1=Panel, 2=Frosted. Migrates the old header_bg/header_blur toggles once. */
    private int headerBgMode() {
        int mode = prefs.getInt("header_bg_mode", -1);
        if (mode < 0) {
            mode = prefs.getBoolean("header_blur", false) ? 2 : (prefs.getBoolean("header_bg", false) ? 1 : 0);
            prefs.edit().putInt("header_bg_mode", mode).apply();
        }
        return mode;
    }

    private void applyHeaderBackground() {
        if (header == null) return;
        int mode = headerBgMode();
        GradientDrawable bg = null;
        if (mode == 1) {
            bg = new GradientDrawable();
            bg.setColor(0x40000000);
        } else if (mode == 2) {
            // Frosted glass: semi-transparent gradient simulating blur.
            bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x60000000, 0x30000000});
        }
        if (bg != null) {
            bg.setCornerRadius(dp(18));
            header.setPadding(dp(12), dp(8), dp(12), dp(8));
        } else {
            header.setPadding(0, 0, 0, 0);
        }
        header.setBackground(bg);
    }

    /** Applies the launcher-wide font to a TextView. */
    private void applyAppFont(TextView view) {
        String[] fonts = {"sans-serif", "serif", "monospace", "sans-serif-light"};
        view.setTypeface(android.graphics.Typeface.create(
                fonts[Math.max(0, Math.min(3, prefs.getInt("app_font", 0)))], android.graphics.Typeface.NORMAL));
    }

    /** Submenu helper with per-row icons. */
    private void subMenu(String title, String[] labels, String[] hints, int[] icons, Runnable[] choices) {
        GlassSettings.options(this, title, labels, hints, icons, which -> choices[which].run());
    }

    private void tileSettings() {
        String[] labels = {"App columns", "Tile size", "Tile spacing", "Tile corners", "Logo size", "Focused app title size", "App colour glow", "Focus border", "App entrance"};
        String[] hints = {"Number of cards per row", "Compact or large cards", "Gap between cards", "Sharp, rounded or round", "App logo size in cards", "Name above the app grid", "Match the focused app", "Thin, normal or thick", "Staggered slide-in"};
        int[] icons = {R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_apps, R.drawable.ic_settings, R.drawable.ic_brightness, R.drawable.ic_apps, R.drawable.ic_apps};
        Runnable[] choices = {
            () -> chooseSetting("App columns", new String[]{"Automatic", "2", "3", "4", "5", "6"}, which -> {
                prefs.edit().putInt(tv ? "tv_columns" : "phone_columns", which == 0 ? 0 : which + 1).apply(); refreshApps();
            }),
            () -> chooseSetting("Tile size", new String[]{"Compact", "Large"}, which -> {
                prefs.edit().putBoolean("large_tiles", which == 1).putInt(tv ? "tv_columns" : "phone_columns", 0).apply(); refreshApps();
            }),
            () -> chooseSetting("Tile spacing", new String[]{"Compact", "Normal", "Wide"}, which -> {
                prefs.edit().putInt("tile_spacing", which).apply(); refreshApps();
            }),
            () -> chooseSetting("Tile corners", new String[]{"Sharp", "Rounded", "Round"}, which -> {
                prefs.edit().putInt("tile_corners", which).apply(); refreshApps();
            }),
            () -> chooseSetting("Logo size", new String[]{"Small", "Normal", "Large"}, which -> {
                prefs.edit().putInt("logo_size", which).apply();
                appWorker.execute(() -> new IconCache(this).clear());
                cachedApps = null;
                loadApps();
            }),
            () -> chooseSetting("Focused app title size", new String[]{"Small", "Normal", "Large", "Extra large"}, which -> {
                int size = new int[]{24,32,40,48}[which]; prefs.edit().putInt("focus_title_size", size).apply(); appHeading.setTextSize(size);
            }),
            () -> chooseSetting("App colour glow", new String[]{"On", "Off"}, which -> {
                prefs.edit().putBoolean("focus_glow", which == 0).apply(); sky.focusAccent = 0; sky.invalidate();
            }),
            () -> chooseSetting("Focus border", new String[]{"Thin", "Normal", "Thick"}, which -> {
                prefs.edit().putInt("focus_border", which).apply(); refreshApps();
            }),
            () -> chooseSetting("App entrance", new String[]{"Off", "On"}, which -> {
                prefs.edit().putBoolean("app_entrance", which == 1).apply(); refreshApps();
            }),
        };
        subMenu("Tiles", labels, hints, icons, choices);
    }

    private void clockSettings() {
        String[] labels = {"Clock size", "Clock style", "Clock font", "Time format", "Clock alignment", "Clock seconds", "Clock glow", "Clock shadow", "Hide clock", "Hide greeting"};
        String[] hints = {"Small, normal or large", "Digital or analog", "Light, serif or mono", "12-hour or 24-hour", "Left, center or right", "Show seconds", "Halo behind the clock", "Shadow for readability", "Hide the clock", "Hide the greeting"};
        int[] icons = {R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock};
        Runnable[] choices = {
            () -> chooseSetting("Clock size", new String[]{"Small", "Normal", "Large"}, which -> {
                prefs.edit().putInt("clock_size", (tv ? new int[]{42,52,62} : new int[]{36,44,52})[which]).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Clock style", new String[]{"Digital", "Analog"}, which -> {
                prefs.edit().putInt("clock_style", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Clock font", new String[]{"Light", "Serif", "Mono"}, which -> {
                prefs.edit().putInt("clock_font", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Time format", new String[]{"12-hour", "24-hour"}, which -> {
                prefs.edit().putBoolean("time_24h", which == 1).apply(); updateClock();
            }),
            () -> chooseSetting("Clock alignment", new String[]{"Left", "Center", "Right"}, which -> {
                prefs.edit().putInt("clock_align", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Clock seconds", new String[]{"Off", "On"}, which -> {
                prefs.edit().putBoolean("clock_seconds", which == 1).apply();
                handler.removeCallbacks(secondTick);
                if (which == 1 && resumed) secondTick.run();
                else updateClock();
            }),
            () -> chooseSetting("Clock glow", new String[]{"Off", "On"}, which -> {
                prefs.edit().putBoolean("clock_glow", which == 1).apply();
                sky.invalidate();
            }),
            () -> chooseSetting("Clock shadow", new String[]{"Off", "On"}, which -> {
                prefs.edit().putBoolean("clock_shadow", which == 1).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Hide clock", new String[]{"Show", "Hide"}, which -> {
                prefs.edit().putBoolean("hide_clock", which == 1).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Hide greeting", new String[]{"Show", "Hide"}, which -> {
                prefs.edit().putBoolean("hide_greeting", which == 1).apply(); buildHomeSoon();
            }),
        };
        subMenu("Clock", labels, hints, icons, choices);
    }

    private void themeSettings() {
        // "Dark mode" removed 2026-11-01 (user): the theme glass color covers it.
        String[] labels = {"Theme", "Accent color", "Launcher font", "Glass intensity"};
        String[] hints = {"Cyan, Orange, Purple or Custom", "Custom theme color", "Font for the launcher", "Transparency of panels"};
        int[] icons = {R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_brightness};
        Runnable[] choices = {
            () -> chooseSetting("Theme", new String[]{"Midnight Cyan", "Sunset Orange", "Deep Purple", "Custom"}, which -> {
                prefs.edit().putInt("theme_name", which).apply();
                if (which == 3) chooseAccentColor();
                else { applyAppearance(); refreshApps(); }
            }),
            () -> chooseAccentColor(),
            () -> chooseSetting("Launcher font", new String[]{"Default", "Serif", "Mono", "Light"}, which -> {
                prefs.edit().putInt("app_font", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Glass intensity", new String[]{"Airy", "Normal", "Solid"}, which -> {
                prefs.edit().putInt("glass_alpha", new int[]{30, 70, 100}[which]).apply();
                buildHomeSoon();
            }),
        };
        subMenu("Theme", labels, hints, icons, choices);
    }

    private void wallpaperMenu() {
        // Removed 2026-11-01 (user): "Save as favorite", "Day / night wallpaper",
        // "Smooth day/night", and the built-in Midnight mountains / Solid midnight options.
        String[] labels = {"Wallpaper", "Wallpaper source", "Wallpaper slideshow", "Wallpaper darkness", "Bottom shade"};
        String[] hints = {"Your own picture", "Built-in, online or favorite", "Change wallpaper over time", "Keep the landscape visible", "Dark gradient behind tiles"};
        int[] icons = {R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness};
        Runnable[] choices = {
            this::wallpaperSettings,
            () -> chooseSetting("Wallpaper source", new String[]{"Built-in", "Online", "Favorite"}, which -> {
                prefs.edit().putInt("wallpaper_source", which).apply();
                updateOnlineWallpaper();
                armSlideshow();
                sky.invalidate();
            }),
            () -> chooseSetting("Wallpaper slideshow", new String[]{"Off", "15 minutes", "30 minutes", "1 hour"}, which -> {
                prefs.edit().putInt("wallpaper_slideshow", new int[]{0, 15, 30, 60}[which]).apply();
                armSlideshow();
            }),
            () -> chooseSetting("Wallpaper darkness", new String[]{"Light", "Medium", "Dark"}, which -> {
                prefs.edit().putInt("background_dim", which * 20).apply(); sky.invalidate();
            }),
            () -> chooseSetting("Bottom shade", new String[]{"Off", "On"}, which -> {
                prefs.edit().putBoolean("bottom_shade", which == 1).apply(); sky.invalidate();
            }),
        };
        subMenu("Wallpaper", labels, hints, icons, choices);
    }

    private void appMenu() {
        String[] labels = {"Hide / unhide apps", "Rearrange apps", "Continue watching"};
        String[] hints = {"Choose which apps appear", "Choose the order of app cards", "Resume titles after your apps"};
        int[] icons = {R.drawable.ic_apps, R.drawable.ic_swap_vert, R.drawable.ic_livetv};
        Runnable[] choices = {
            this::hiddenApps,
            this::rearrangeApps,
            () -> chooseSetting("Continue watching", new String[]{"Show below apps", "Hide row", "Allow TV Watch Next access"}, which -> {
                if (which == 2) continueWatching.requestAccess();
                else { prefs.edit().putBoolean("continue_watching", which == 0).apply(); continueWatching.reload(); }
            }),
        };
        subMenu("Apps", labels, hints, icons, choices);
    }

    private void systemMenu() {
        String[] labels = {"Screensaver & weather", "Profile name & photo", "Avatar shape", "Header background", "Performance", "Export settings", "Import settings", "Choose default Home", "Reset layout & unhide apps"};
        String[] hints = {"Slideshow, weather & idle timer", "Name and local profile picture", "Circle or rounded square", "Subtle panel behind header", "Light, Balanced or Full", "Save to a file", "Restore from a file", "Set this launcher as Home", "Restore the default layout"};
        int[] icons = {R.drawable.ic_picture, R.drawable.ic_person, R.drawable.ic_person, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_home_settings, R.drawable.ic_settings};
        Runnable[] choices = {
            this::screensaverSettings,
            this::editProfile,
            () -> chooseSetting("Avatar shape", new String[]{"Circle", "Rounded square"}, which -> {
                prefs.edit().putInt("avatar_shape", which).apply();
                avatar.setBackground(avatarSurface(false));
            }),
            () -> chooseSetting("Header background", new String[]{"Off", "Panel", "Frosted"}, which -> {
                prefs.edit().putInt("header_bg_mode", which).apply();
                applyHeaderBackground();
            }),
            () -> chooseSetting("Performance", new String[]{"Auto", "Light", "Balanced", "Full"}, which -> {
                prefs.edit().putInt("perf_mode", which).apply();
                applyPerformanceProfile();
                buildHomeSoon();
            }),
            this::exportSettings,
            this::importSettings,
            () -> launch(new Intent(this, HomeSettingsActivity.class)),
            () -> new AlertDialog.Builder(this).setTitle("Reset layout and show all apps?").setPositiveButton("Reset", (dialog, which) -> {
                prefs.edit().remove("app_order").remove("hidden_apps").remove("tv_columns").remove("phone_columns").remove("large_tiles")
                        .remove("focus_title_size").remove("clock_size").remove("background_dim").remove("theme_mode")
                        .remove("wallpaper_mode").remove("focus_glow").remove("continue_watching")
                        .remove("header_bg_mode").remove("header_bg").remove("header_blur").apply(); buildHomeSoon();
            }).setNegativeButton(android.R.string.cancel, null).show(),
        };
        subMenu("System", labels, hints, icons, choices);
    }

    private void wallpaperSettings() {
        // Built-in Midnight mountains / Solid midnight removed 2026-11-01 (user).
        chooseSetting("Home wallpaper", new String[]{"Choose picture", "Saved custom picture"}, which -> {
            if (which == 0) {
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 8); }
                catch (ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(this, "No image picker available on this device", Toast.LENGTH_LONG).show(); }
            } else {
                if (!new File(getFilesDir(), "personal-wallpaper.jpg").exists()) { Toast.makeText(this, "Choose a picture first", Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("wallpaper_mode", "custom").apply(); loadWallpaper();
            }
        });
    }

    private void loadWallpaper() {
        if (!"custom".equals(prefs.getString("wallpaper_mode", "mountains"))) return;
        worker.execute(() -> {
            Bitmap image = BitmapFactory.decodeFile(new File(getFilesDir(), "personal-wallpaper.jpg").getPath());
            handler.post(() -> { if (!isDestroyed()) { customWallpaper = image; if (sky != null) sky.invalidate(); adaptTextColors(); } });
        });
    }

    /** Fixed label style for all wallpaper-backed text (user 2026-11-01). */
    private void adaptVisibleTextColors() {
        // Fixed style for every label (user 2026-11-01): white + black outline + drop shadow.
        applyLabelScheme(userName, false, false);
        applyLabelScheme(greeting, false, true);
        applyLabelScheme(homeWeather, false, true);
        applyLabelScheme(networkStatus, false, true);
        applyLabelScheme(time, false, false);
        applyLabelScheme(ampm, false, false);
        applyLabelScheme(date, false, true);
        applyLabelScheme(appHeading, false, false);
    }

    /** Fixed label style (2026-11-01): white text with a black outline halo and drop shadow,
     *  readable on any wallpaper. Replaces the old per-label adaptive colors. */
    private void applyLabelScheme(android.widget.TextView label, boolean dark, boolean muted) {
        if (label == null) return;
        label.setTextColor(muted ? 0xE6FFFFFF : 0xFFFFFFFF);
        label.setShadowLayer(dp(5), 0, dp(2), 0xE6000000);
    }

    private void adaptTextColors() {
        // Initial pass: per-label wallpaper sampling (no theme shade factor).
        adaptVisibleTextColors();
    }

    /** Applies the current light/dark text scheme to every wallpaper-backed label. */
    private void chooseSetting(String title, String[] options, java.util.function.IntConsumer selected) {
        String[] shown = options.clone();
        int current = settingCurrentIndex(title);
        if (current >= 0 && current < shown.length) shown[current] += "  ✓";
        GlassSettings.options(this, title, shown, null, selected);
    }

    /** Index of the currently applied option for a settings chooser (-1 when none applies). (User 2026-11-01.) */
    private int settingCurrentIndex(String title) {
        switch (title) {
            case "Wallpaper darkness": return Math.min(2, prefs.getInt("background_dim", 0) / 20);
            case "App colour glow": return prefs.getBoolean("focus_glow", true) ? 0 : 1;
            case "Header background": return headerBgMode();
            case "App columns": {
                int v = prefs.getInt(tv ? "tv_columns" : "phone_columns", 0);
                return v == 0 ? 0 : v - 1;
            }
            case "Tile size": return prefs.getBoolean("large_tiles", false) ? 1 : 0;
            case "Tile spacing": return prefs.getInt("tile_spacing", 0);
            case "Tile corners": return prefs.getInt("tile_corners", 0);
            case "Logo size": return Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)));
            case "Focused app title size": return indexOf(new int[]{24, 32, 40, 48}, prefs.getInt("focus_title_size", tv ? 32 : 26));
            case "Focus border": return prefs.getInt("focus_border", 0);
            case "App entrance": return prefs.getBoolean("app_entrance", true) ? 1 : 0;
            case "Clock size": return indexOf(tv ? new int[]{42, 52, 62} : new int[]{36, 44, 52}, prefs.getInt("clock_size", tv ? 52 : 44));
            case "Clock style": return prefs.getInt("clock_style", 0);
            case "Clock font": return prefs.getInt("clock_font", 0);
            case "Time format": return prefs.getBoolean("time_24h", false) ? 1 : 0;
            case "Clock alignment": return prefs.getInt("clock_align", 0);
            case "Clock seconds": return prefs.getBoolean("clock_seconds", false) ? 1 : 0;
            case "Clock glow": return prefs.getBoolean("clock_glow", false) ? 1 : 0;
            case "Clock shadow": return prefs.getBoolean("clock_shadow", true) ? 1 : 0;
            case "Hide clock": return prefs.getBoolean("hide_clock", false) ? 1 : 0;
            case "Hide greeting": return prefs.getBoolean("hide_greeting", false) ? 1 : 0;
            case "Theme": return prefs.getInt("theme_name", 0);
            case "Launcher font": return prefs.getInt("app_font", 0);
            case "Glass intensity": return indexOf(new int[]{30, 70, 100}, prefs.getInt("glass_alpha", 70));
            case "Wallpaper source": return prefs.getInt("wallpaper_source", 0);
            case "Wallpaper slideshow": return indexOf(new int[]{0, 15, 30, 60}, prefs.getInt("wallpaper_slideshow", 0));
            case "Bottom shade": return prefs.getBoolean("bottom_shade", true) ? 1 : 0;
            case "Continue watching": return prefs.getBoolean("continue_watching", true) ? 0 : 1;
            case "Avatar shape": return prefs.getInt("avatar_shape", 0);
            case "Performance": return prefs.getInt("perf_mode", 0);
            case "Home wallpaper": return "custom".equals(prefs.getString("wallpaper_mode", "mountains")) ? 1 : -1;
            case "Temperature unit": return prefs.getBoolean("weather_fahrenheit", false) ? 1 : 0;
            case "Photo change interval": return indexOf(new int[]{30, 60, 120, 300}, prefs.getInt("photo_seconds", 30));
            case "Clock position": return prefs.getInt("ss_clock_pos", 1);
            case "Start screensaver after idle": return indexOf(new int[]{0, 2, 5, 10, 15}, prefs.getInt("idle_screensaver_minutes", 0));
            default: return -1;
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
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
                    R.drawable.ic_apps, accentColor(), () -> {});
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
        chooseSetting("Online landscape screensaver", new String[]{"Preview", "Weather city", "Temperature unit", "Photo change interval", "Clock position", "Start after idle", "Set automatic screensaver"}, which -> {
            if (which == 0) launch(new Intent(this, LandscapeActivity.class));
            else if (which == 1) WeatherSettings.show(this);
            else if (which == 2) chooseSetting("Temperature unit", new String[]{"Celsius", "Fahrenheit"}, value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply());
            else if (which == 3) chooseSetting("Photo change interval", new String[]{"30 seconds", "1 minute", "2 minutes", "5 minutes"}, value -> prefs.edit().putInt("photo_seconds", new int[]{30,60,120,300}[value]).apply());
            else if (which == 4) chooseSetting("Clock position", new String[]{"Top", "Center", "Bottom"}, value -> prefs.edit().putInt("ss_clock_pos", value).apply());
            else if (which == 5) chooseSetting("Start screensaver after idle", new String[]{"Off", "2 minutes", "5 minutes", "10 minutes", "15 minutes"}, value -> {
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

    private ImageView profileAvatarPreview;

    /** Refreshes the avatar preview inside the profile dialog, if open. */
    private void refreshProfileAvatarPreview() {
        if (profileAvatarPreview == null) return;
        if (avatarBitmap != null) profileAvatarPreview.setImageBitmap(avatarBitmap);
        else profileAvatarPreview.setImageResource(R.drawable.ic_person);
    }

    private void editProfile() {
        if (profile != null && profile.isShowing()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(8));
        // Avatar preview inside the dialog (user 2026-11-01).
        profileAvatarPreview = new ImageView(this);
        profileAvatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profileAvatarPreview.setBackground(avatarSurface(false));
        profileAvatarPreview.setClipToOutline(true);
        profileAvatarPreview.setContentDescription("Profile photo");
        refreshProfileAvatarPreview();
        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setGravity(Gravity.CENTER);
        previewRow.setPadding(0, 0, 0, dp(8));
        previewRow.addView(profileAvatarPreview, new LinearLayout.LayoutParams(dp(96), dp(96)));
        form.addView(previewRow);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Your name");
        name.setText(prefs.getString("name", "Sadique Sadiq"));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(name);
        // Choose / Remove side by side (user 2026-11-01).
        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button("Choose photo", () -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 7);
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "No photo picker available on this device", Toast.LENGTH_LONG).show();
            }
        });
        Button remove = button("Remove photo", () -> {
            File photo = new File(getFilesDir(), "personal-avatar.png");
            if (photo.exists() && !photo.delete()) {
                Toast.makeText(this, "Could not remove photo", Toast.LENGTH_SHORT).show();
                return;
            }
            avatarBitmap = null;
            refreshProfile();
            refreshProfileAvatarPreview();
        });
        LinearLayout.LayoutParams photoBtn = new LinearLayout.LayoutParams(0, -2, 1);
        photoBtn.setMargins(dp(4), dp(8), dp(4), dp(4));
        photoRow.addView(choose, photoBtn);
        LinearLayout.LayoutParams photoBtn2 = new LinearLayout.LayoutParams(0, -2, 1);
        photoBtn2.setMargins(dp(4), dp(8), dp(4), dp(4));
        photoRow.addView(remove, photoBtn2);
        form.addView(photoRow);
        form.addView(text("Profile stays on this device. Google login is not used.", 13, MUTED));
        // Save on the left, Later on the right (user 2026-11-01).
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("Save", () -> {
            String value = PersonalHomeModel.name(name.getText().toString());
            if (value.isEmpty()) { name.setError("Enter your name"); return; }
            prefs.edit().putString("name", value).putBoolean("setup", true).apply();
            refreshProfile();
            if (profile != null) profile.dismiss();
            handler.post(() -> maybeOfferDefaultHome());
        });
        Button later = button("Later", () -> { if (profile != null) profile.dismiss(); });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
        saveParams.setMargins(dp(4), dp(8), dp(4), 0);
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, -2, 1);
        laterParams.setMargins(dp(4), dp(8), dp(4), 0);
        actionRow.addView(save, saveParams);
        actionRow.addView(later, laterParams);
        form.addView(actionRow);
        profile = new AlertDialog.Builder(this).setTitle(prefs.getBoolean("setup", false) ? "Your profile" : "Welcome · set up your Home")
                .setView(form).create();
        profile.setOnDismissListener(d -> profileAvatarPreview = null);
        profile.show();
    }

    /**
     * Offers to make Personal Launcher the default Home. The old logic set a permanent
     * "asked once" flag, so after an update/reinstall that reset the default-home setting
     * the prompt never came back. Now the offer reappears whenever we are not the default
     * (with a short snooze after "Later" so it doesn't nag every launch).
     */
    private void maybeOfferDefaultHome() {
        if (isDestroyed() || isFinishing()) return;
        boolean alreadyDefault = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                alreadyDefault = rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
        }
        if (alreadyDefault || !HomeSettingsActivity.available(this)) return;
        // One-time migration: drop the old permanent flag so installs that lost the
        // default-home status on update get the offer again.
        if (prefs.contains("home_offer_done")) prefs.edit().remove("home_offer_done").apply();
        if (System.currentTimeMillis() < prefs.getLong("home_offer_snooze", 0)) return;
        new AlertDialog.Builder(this)
            .setTitle("Use as Home screen?")
            .setMessage("Set Personal Launcher as your default launcher so the Home button opens it.")
            .setPositiveButton("Set as default", (d, w) -> launch(new Intent(this, HomeSettingsActivity.class)))
            .setNegativeButton("Later", (d, w) -> prefs.edit().putLong("home_offer_snooze",
                    System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000).apply())
            .show();
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

    /** Export settings to JSON via document picker. Excludes secrets. */
    private void exportSettings() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("version", 1);
            json.put("app", "com.sadique.launcher");
            // Export safe prefs only (no secrets, no cache).
            java.util.Map<String, ?> all = prefs.getAll();
            org.json.JSONObject data = new org.json.JSONObject();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey();
                // Skip secrets, caches, and transient state.
                if (k.contains("key") || k.contains("secret") || k.contains("token")
                        || k.contains("password") || k.startsWith("weather_cached")) continue;
                Object v = e.getValue();
                if (v instanceof String || v instanceof Integer || v instanceof Boolean
                        || v instanceof Long || v instanceof Float) {
                    data.put(k, v);
                } else if (v instanceof java.util.Set) {
                    org.json.JSONArray arr = new org.json.JSONArray();
                    for (Object item : (java.util.Set<?>) v) arr.put(item.toString());
                    data.put(k, arr);
                }
            }
            json.put("settings", data);
            String content = json.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "launcher-settings.json");
            // Store content for onActivityResult.
            pendingExport = content;
            startActivityForResult(intent, 10);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Export failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private String pendingExport;

    /** Import settings from JSON via document picker. Validates format version. */
    private void importSettings() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json");
            startActivityForResult(intent, 11);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Import failed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && result == RESULT_OK && data != null && data.getData() != null) {
            // Export: write JSON to the picked document.
            String content = pendingExport;
            pendingExport = null;
            if (content != null) {
                worker.execute(() -> {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                        out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        handler.post(() -> android.widget.Toast.makeText(this,
                                "Settings exported", android.widget.Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        handler.post(() -> android.widget.Toast.makeText(this,
                                "Export failed", android.widget.Toast.LENGTH_SHORT).show());
                    }
                });
            }
            return;
        }
        if (request == 11 && result == RESULT_OK && data != null && data.getData() != null) {
            // Import: read and validate JSON, apply settings.
            worker.execute(() -> {
                try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                    java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\A");
                    String content = scanner.hasNext() ? scanner.next() : "";
                    org.json.JSONObject json = new org.json.JSONObject(content);
                    int version = json.optInt("version", 0);
                    if (version != 1) throw new Exception("Unsupported version: " + version);
                    if (!"com.sadique.launcher".equals(json.optString("app"))) {
                        throw new Exception("Not a Personal Launcher export");
                    }
                    org.json.JSONObject settings = json.getJSONObject("settings");
                    SharedPreferences.Editor ed = prefs.edit();
                    java.util.Iterator<String> keys = settings.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        Object v = settings.get(k);
                        if (v instanceof String) ed.putString(k, (String) v);
                        else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                        else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                        else if (v instanceof Long) ed.putLong(k, (Long) v);
                        else if (v instanceof org.json.JSONArray) {
                            java.util.Set<String> set = new java.util.HashSet<>();
                            org.json.JSONArray arr = (org.json.JSONArray) v;
                            for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                            ed.putStringSet(k, set);
                        }
                    }
                    ed.apply();
                    handler.post(() -> {
                        android.widget.Toast.makeText(this,
                                "Settings imported — restarting Home", android.widget.Toast.LENGTH_SHORT).show();
                        buildHome();
                    });
                } catch (Exception e) {
                    handler.post(() -> android.widget.Toast.makeText(this,
                            "Import failed: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        if (request == 9) {
            if (result == RESULT_OK && data != null) {
                ArrayList<String> words = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (words != null && !words.isEmpty()) {
                    String query = words.get(0);
                    // Google Home behavior: if an installed app matches, show it;
                    // otherwise fall back to YouTube search.
                    if (appMatches(query)) {
                        if (searchDialog == null) searchApps();
                        if (searchInput != null) { searchInput.setText(query); searchInput.setSelection(searchInput.length()); }
                    } else {
                        youtubeSearch(query);
                    }
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
                    else { avatarBitmap = decoded; refreshProfile(); refreshProfileAvatarPreview(); }
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
        updateContentRows();
        refreshHomeWeather();
        updateOnlineWallpaper();
        armSlideshow();
        handler.removeCallbacks(secondTick);
        if (prefs.getBoolean("clock_seconds", false)) secondTick.run();
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
        handler.removeCallbacks(secondTick);
        handler.removeCallbacks(weatherRefresh);
        handler.removeCallbacks(slideshowStep);
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
        launch(new Intent(this, LandscapeActivity.class));
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
        appWorker.shutdownNow();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Release rebuildable resources under memory pressure.
        // TRIM_MEMORY_UI_HIDDEN and above: we're in the background.
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            // Clear online wallpaper (reloadable from cache/network).
            if (sky != null) sky.releaseTransient();
            // Trim icon memory cache.
            System.gc(); // Hint; actual collection is up to the runtime.
        }
        // TRIM_MEMORY_RUNNING_CRITICAL: also drop the crossfade buffer.
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            if (sky != null) sky.releaseTransient();
        }
    }

    /** Static dark wallpaper and a single focus glow; no continuously running animation. */
    private final class SkyClock extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Never share the bitmap paint with translucent sun/moon or focus effects.
        private final Paint wallpaperPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        /** Decoded off the main thread; onDraw draws the gradient alone until it arrives. */
        private Bitmap backdrop;
        private float smoothDay = -1f;

        /** Releases transient bitmaps (online photos, crossfade buffer) under memory pressure. */
        void releaseTransient() {            // Don't recycle backdrop — it's the built-in wallpaper, still used in onDraw.
            // Only clear the reloadable online variants.
            onlineNext = null;
            // Keep onlineWallpaper if it's currently displayed; it will be re-fetched on demand.
            // Null it only if we have a fallback.
            if (backdrop != null) {
                onlineWallpaper = null;
            }
            System.gc();
        }
        /**
         * Carry the already-loaded wallpapers into a fresh SkyClock created by
         * buildHome(), so a settings change never flashes the default wallpaper.
         * The old instance is detached: its fields are nulled so a stale posted
         * runnable can never recycle a bitmap this instance is drawing.
         * (User 2026-11-01.)
         */
        void adoptWallpapers(SkyClock old) {
            backdrop = old.backdrop;
            onlineWallpaper = old.onlineWallpaper;
            onlineNext = old.onlineNext;
            onlineIndex = old.onlineIndex;
            crossfade = old.crossfade;
            old.backdrop = null;
            old.onlineWallpaper = null;
            old.onlineNext = null;
            old.onlineIndex = -1;
            // A crossfade interrupted mid-flight continues on this instance.
            if (crossfade < 1f) {
                handler.removeCallbacks(crossfadeStep);
                handler.post(crossfadeStep);
            }
        }
        int minute, focusAccent;
        float clockY = -1, focusX, focusY, focusRadius;
        /** Analog face radius in px, set from clock_size pref. */
        float clockRadius = -1;
        /** Analog clock mode: 0=digital, 1=analog. Set from prefs. */
        int clockStyle;
        /** Online wallpaper with crossfade. */
        private Bitmap onlineWallpaper, onlineNext;
        private float crossfade = 1f;
        private int onlineIndex = -1;
        /** Favorite wallpaper (saved by user). */
        private Bitmap favoriteWallpaper;
        SkyClock() {
            super(PersonalHomeActivity.this);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            worker.execute(() -> {
                Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.personal_wallpaper);
                Bitmap fav = null;
                try {
                    java.io.File f = new java.io.File(getFilesDir(), "favorite_wallpaper.jpg");
                    if (f.exists()) fav = BitmapFactory.decodeFile(f.getAbsolutePath());
                } catch (Exception ignored) {}
                Bitmap bg = bmp, fg = fav;
                handler.post(() -> { backdrop = bg; favoriteWallpaper = fg; invalidate(); adaptTextColors(); });
            });
        }

        void reloadFavorite() {
            worker.execute(() -> {
                Bitmap fav = null;
                try {
                    java.io.File f = new java.io.File(getFilesDir(), "favorite_wallpaper.jpg");
                    if (f.exists()) fav = BitmapFactory.decodeFile(f.getAbsolutePath());
                } catch (Exception ignored) {}
                Bitmap loaded = fav;
                handler.post(() -> { favoriteWallpaper = loaded; invalidate(); });
            });
        }

        /** The wallpaper bitmap currently drawn (or null if none yet). */
        Bitmap currentPicture() {
            String mode = prefs.getString("wallpaper_mode", "mountains");
            int source = prefs.getInt("wallpaper_source", 0);
            Bitmap picture = mode.equals("custom") && customWallpaper != null ? customWallpaper : backdrop;
            if (source == 1 && onlineWallpaper != null) picture = onlineWallpaper;
            if (source == 2 && favoriteWallpaper != null) picture = favoriteWallpaper;
            if (mode.equals("solid")) picture = null;
            return picture;
        }

        /** Saves the current wallpaper as the favorite. */
        void saveFavorite() {
            worker.execute(() -> {
                try {
                    Bitmap current = onlineWallpaper != null ? onlineWallpaper : backdrop;
                    if (current == null) return;
                    java.io.File out = new java.io.File(getFilesDir(), "favorite_wallpaper.jpg");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                        current.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    }
                    handler.post(() -> android.widget.Toast.makeText(PersonalHomeActivity.this,
                            "Saved as favorite", android.widget.Toast.LENGTH_SHORT).show());
                } catch (Exception ignored) {}
            });
        }

        /** Loads a fresh online wallpaper photo with crossfade (picsum seed never repeats). */
        void loadOnlinePhoto(int index) {
            if (index == onlineIndex && onlineWallpaper != null) return;
            onlineIndex = index;
            // Bing daily: use the index directly (0=today). No time-based seed —
            // the launcher must always show today's wallpaper. (User 2026-10-31.)
            final int bingIdx = index;
            worker.execute(() -> {
                Bitmap bmp = null;
                try {
                    // Retire the old 6-photo disk cache: it is no longer populated.
                    java.io.File[] stale = new java.io.File(getCacheDir(), "screensaver_photos").listFiles();
                    if (stale != null) for (java.io.File f : stale) f.delete();
                    // Bing daily wallpaper: API returns JSON, parse to get image URL.
                    String apiUrl = LandscapeModel.photoUrl(bingIdx);
                    byte[] jsonBytes = OnlineContent.get(apiUrl, 64 * 1024);
                    String json = new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8);
                    String imageUrl = LandscapeModel.bingImageUrl(json);
                    byte[] bytes = OnlineContent.get(imageUrl, 6 * 1024 * 1024);
                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                } catch (Exception ignored) {}
                Bitmap loaded = bmp;
                handler.post(() -> {
                    if (loaded == null) return;
                    if (onlineNext != null && onlineNext != onlineWallpaper) onlineNext.recycle();
                    onlineNext = loaded;
                    crossfade = 0f;
                    handler.removeCallbacks(crossfadeStep);
                    handler.post(crossfadeStep);
                });
            });
        }

        private final Runnable crossfadeStep = new Runnable() {
            @Override public void run() {
                crossfade += 0.06f;
                if (crossfade >= 1f) {
                    crossfade = 1f;
                    if (onlineWallpaper != null) onlineWallpaper.recycle();
                    onlineWallpaper = onlineNext;
                    onlineNext = null;
                    adaptTextColors();
                } else {
                    handler.postDelayed(this, 50);
                }
                invalidate();
            }
        };
        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(Color.WHITE);
            paint.setShader(null);
            String theme = prefs.getString("theme_mode", "dark");
            float targetDay = HomeAppearance.daylight(theme, minute);
            if (smoothDay < 0) smoothDay = targetDay; // First draw: snap.
            // Smooth day/night transition: ease towards the target instead of jumping.
            if (prefs.getBoolean("daynight_smooth", true)) {
                if (Math.abs(targetDay - smoothDay) > 0.001f) {
                    smoothDay += (targetDay - smoothDay) * 0.08f;
                    // Keep redrawing until settled.
                    postInvalidateOnAnimation();
                } else {
                    smoothDay = targetDay;
                }
            } else {
                smoothDay = targetDay;
            }
            float day = smoothDay;
            int top = blend(0xff020817, 0xff145d85, day), bottom = blend(0xff07172b, 0xff092b4a, day);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint); paint.setShader(null);
            String mode = prefs.getString("wallpaper_mode", "mountains");
            int source = prefs.getInt("wallpaper_source", 0);
            boolean onlineSource = source == 1;
            Bitmap picture = mode.equals("custom") && customWallpaper != null ? customWallpaper : backdrop;
            // Online wallpaper overrides when enabled and a photo is loaded.
            if (onlineSource && onlineWallpaper != null) picture = onlineWallpaper;
            // Favorite wallpaper.
            if (source == 2 && favoriteWallpaper != null) picture = favoriteWallpaper;
            float y = clockY >= 0 ? clockY : dp(150);
            if (!mode.equals("solid") && picture != null) {
                boolean mountains = picture == backdrop;
                float[] frame = HomeAppearance.frame(getWidth(), getHeight(), picture.getWidth(), picture.getHeight(), y, mountains);
                canvas.drawBitmap(picture, null, new android.graphics.RectF(frame[0], frame[1], frame[2], frame[3]), wallpaperPaint);
                // Crossfade: incoming photo fades in over the current one.
                if (onlineSource && onlineNext != null && crossfade < 1f) {
                    wallpaperPaint.setAlpha((int) (crossfade * 255));
                    float[] nextFrame = HomeAppearance.frame(getWidth(), getHeight(), onlineNext.getWidth(), onlineNext.getHeight(), y, false);
                    canvas.drawBitmap(onlineNext, null, new android.graphics.RectF(nextFrame[0], nextFrame[1], nextFrame[2], nextFrame[3]), wallpaperPaint);
                    wallpaperPaint.setAlpha(255);
                }
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
                // Bottom shade: dark gradient behind tiles for readability (demo look).
                if (prefs.getBoolean("bottom_shade", true)) {
                    paint.setShader(new LinearGradient(0, getHeight() * 0.55f, 0, getHeight(),
                            Color.TRANSPARENT, 0x99000000, Shader.TileMode.CLAMP));
                    canvas.drawRect(0, getHeight() * 0.55f, getWidth(), getHeight(), paint);
                    paint.setShader(null);
                }


            }
            if (focusAccent != 0 && focusRadius > 0) {
                int glow = (focusAccent & 0x00ffffff) | 0x80000000;
                paint.setShader(new RadialGradient(focusX, focusY, focusRadius, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(focusX, focusY, focusRadius, paint); paint.setShader(null);
            }
            float cy = clockY >= 0 ? clockY : dp(150);
            // Clock glow: soft halo behind the clock.
            if (prefs.getBoolean("clock_glow", false)) {
                int accent = accentColor();
                paint.setShader(new RadialGradient(getWidth() / 2f, cy, dp(160),
                        (accent & 0x00ffffff) | 0x60000000, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(getWidth() / 2f, cy, dp(160), paint); paint.setShader(null);
            }
            // Analog clock face.
            if (clockStyle == 1) {
                float cx = getWidth() / 2f;
                float radius = clockRadius > 0 ? clockRadius : Math.min(getWidth(), getHeight()) / 7f;
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x99000000);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(0xffffffff);
                canvas.drawCircle(cx, cy, radius, paint);
                int hour = (minute / 60) % 12, min = minute % 60;
                // Tick marks.
                paint.setStrokeWidth(dp(2));
                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians(i * 30);
                    float inner = radius * .85f, outer = radius * .95f;
                    canvas.drawLine(cx + (float)(inner * Math.sin(angle)), cy - (float)(inner * Math.cos(angle)),
                            cx + (float)(outer * Math.sin(angle)), cy - (float)(outer * Math.cos(angle)), paint);
                }
                // Hands.
                double hourAngle = Math.toRadians((hour + min / 60f) * 30);
                double minAngle = Math.toRadians(min * 6);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(dp(6));
                canvas.drawLine(cx, cy, cx + (float)(radius * .5 * Math.sin(hourAngle)),
                        cy - (float)(radius * .5 * Math.cos(hourAngle)), paint);
                paint.setStrokeWidth(dp(4));
                canvas.drawLine(cx, cy, cx + (float)(radius * .75 * Math.sin(minAngle)),
                        cy - (float)(radius * .75 * Math.cos(minAngle)), paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, dp(5), paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    private android.graphics.drawable.Drawable tileBorder(int accent, boolean focused) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT); border.setCornerRadius(tileRadius());
        int thickness = focused ? new int[]{2, 3, 5}[Math.max(0, Math.min(2, prefs.getInt("focus_border", 1)))] : 1;
        border.setStroke(dp(thickness), focused ? accent : 0x4028435e);
        GradientDrawable bevel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x20ffffff, 0x00ffffff, 0x28000000});
        bevel.setCornerRadius(tileRadius());
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bevel, border});
    }

    private static int blend(int a, int b, float part) {
        return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * part),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * part),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * part));
    }
}
