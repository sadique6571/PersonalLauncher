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
    private final ExecutorService weatherWorker = Executors.newSingleThreadExecutor();
    private boolean weatherBusy,weatherForceRefresh,clockDatesInline;
    private WeatherWidgetView weatherWidget;
    private SweepAnalogClock analogClock;
    private HarmonyVisionClockView harmonyClock;
    private TextView harmonyDateValue,harmonyGreetingValue,harmonyQuoteValue;
    private View harmonyWeatherCard;
    private WeatherForecastPanel forecastPanel;
    private WeatherForecast weatherSnapshot;
    private String weatherSnapshotKey="";
    private long weatherSnapshotAt;
    private boolean weatherWidgetEnabled(){return ((harmony()&&tv)||prefs.getBoolean("weather_widget",ThemeStyle.id(prefs)==ThemeStyle.GLASS))&&!prefs.getBoolean("apps_only",false);}
    private int clockAlignment(){return WeatherWidgetLayout.clockAlignment(weatherWidgetEnabled(),ThemeStyle.clockAlignment(prefs));}
    private long nextWeatherAttempt;
    private String lastWeatherRequestKey="";
    private final SharedPreferences.OnSharedPreferenceChangeListener weatherPreferences=(preferences,key)-> {
        if ("ambient_strength".equals(key)) updateAmbient();
        if ("weather_lat".equals(key) || "weather_lon".equals(key) || "weather_fahrenheit".equals(key) || "weather_city".equals(key)) {
            nextWeatherAttempt=0;
            handler.removeCallbacks(this.weatherRefresh); handler.post(this.weatherRefresh);
        }
    };
    private SharedPreferences prefs;
    private LinearLayout page, header, actions, clockLayout, appBody;    private HomeAppGrid<App> grid;
    private android.widget.ImageButton searchButton;
    private String selectedCategory = "";
    private android.os.Parcelable gridState;
    private int profileEpoch;
    private TextView userName, time, date, ampm, networkStatus, appHeading, greeting, homeWeather;
    private ImageView avatar;
    private TextView bluetoothStatus, islamicDate, focusedSpotlight;
    private String spotlightName="",spotlightCategory="";
    private GlassBluetooth bluetooth;
    private GlassTelemetry telemetry;
    private AppLibraryDialog appLibrary;
    private int glassAccent=0xffe50914;
    private boolean harmony() { return ThemeStyle.harmony(prefs); }
    private boolean glassOs() { return ThemeStyle.id(prefs)==ThemeStyle.GLASS || harmony(); }
    private void glassDefaults() {
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS&&!prefs.getBoolean("glass_os_defaults_v1",false))
            prefs.edit().putBoolean("glass_os_defaults_v1",true).putString("wallpaper_mode","solid")
                .putInt("wallpaper_source",0).putInt("header_bg_mode",0).putBoolean("header_glass_v2",true).putInt("clock_size",82)
                .putBoolean("focus_glow",true).putInt("ambient_strength",100).apply();
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS&&!prefs.getBoolean("glass_resume_v3",false))
            prefs.edit().putBoolean("glass_resume_v3",true).putBoolean("continue_watching",true).apply();
        if(ThemeStyle.id(prefs)==ThemeStyle.GLASS&&!prefs.getBoolean("glass_reference_v2",false))
            prefs.edit().putBoolean("glass_reference_v2",true).putInt("header_bg_mode",1).putBoolean("header_glass_v2",true).apply();
        // Dedicated opt-in profile: no change to previously saved styles or TV services.
        if(harmony()&&!prefs.getBoolean("harmony_defaults_v1",false))
            prefs.edit().putBoolean("harmony_defaults_v1",true)
                .putString("wallpaper_mode","mountains").putInt("wallpaper_source",1)
                .putInt("background_dim",16).putInt("header_bg_mode",0).putBoolean("header_glass_v2",true)
                .putInt("clock_size",88).putInt("clock_align",0).putInt("clock_style",0)
                .putBoolean("weather_widget",true).putBoolean("clock_seconds",false)
                .putBoolean("clock_shadow",true).putBoolean("focus_glow",false)
                .putInt("tv_columns",4).putInt("tile_corners",2).putInt("tile_spacing",1)
                .putInt("glass_alpha",58).putInt("glass_finish",0).putInt("glass_edge",1)
                .putBoolean("panel_transparency",true).putBoolean("continue_watching",false).apply();
    }
    /** Lightweight English service shortcuts with D-pad return to the app dock. */
    private View harmonyShortcuts() {
        LinearLayout links=new LinearLayout(this);links.setOrientation(LinearLayout.VERTICAL);
        View apps=GlassSettings.wideCard(this,"All apps",R.drawable.ic_apps,
                ThemeStyle.focusAccent(prefs),this::showAppLibrary);
        View scenery=GlassSettings.wideCard(this,"Scenery",R.drawable.ic_picture,
                ThemeStyle.focusAccent(prefs),()->launch(new Intent(this,LandscapeActivity.class)));
        for(View entry:new View[]{apps,scenery}) {
            entry.setOnKeyListener((view,key,event)->{
                if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN&&grid!=null){
                    grid.focusKey(focusedPkg);return true;
                }
                return false;
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(64));
            params.setMargins(0,dp(3),0,dp(3));links.addView(entry,params);
        }
        return links;
    }
/** A dedicated Huawei Vision-inspired scene, not a recoloured Glass OS home. */
    /** Three balanced zones: the original watch, one daily thought and a real weather widget. */
    private View harmonyHero(){
        LinearLayout stage=new LinearLayout(this);
        stage.setOrientation(LinearLayout.HORIZONTAL);
        stage.setGravity(Gravity.CENTER_VERTICAL);
        stage.setClipChildren(false);stage.setClipToPadding(false);
        int screenH=getResources().getConfiguration().screenHeightDp;
        int screenW=getResources().getConfiguration().screenWidthDp;
        int side=dp(HarmonyVisionLayout.clockDiameterDp(screenH,prefs.getInt("harmony_clock_size",1)));
        harmonyClock=new HarmonyVisionClockView(this);
        harmonyClock.setOnClickListener(v->harmonyClockSettings());
        harmonyClock.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN&&grid!=null){if(continueWatching!=null&&continueWatching.isShown()&&continueWatching.focusEntry())return true;grid.focusKey(focusedPkg);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&weatherWidget!=null){weatherWidget.requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP&&searchButton!=null){searchButton.requestFocus();return true;}
            return false;
        });
        if(!prefs.getBoolean("hide_clock",false)){
            LinearLayout.LayoutParams face=new LinearLayout.LayoutParams(side,side);
            face.setMarginEnd(dp(screenW<780?12:22));
            stage.addView(harmonyClock,face);
        }
        LinearLayout thought=new LinearLayout(this);
        thought.setOrientation(LinearLayout.VERTICAL);thought.setGravity(Gravity.CENTER_VERTICAL);
        stage.addView(thought,new LinearLayout.LayoutParams(0,-2,1));
        harmonyGreetingValue=text(greetingText(),screenH<470?12:14,0xffd7eaff);
        harmonyGreetingValue.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        thought.addView(harmonyGreetingValue);
        harmonyQuoteValue=text(HarmonyDailyWords.forCalendar(Calendar.getInstance()),
                screenW<780?19:screenH<470?21:25,0xffffffff);
        harmonyQuoteValue.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        harmonyQuoteValue.setMaxLines(3);
        harmonyQuoteValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        harmonyQuoteValue.setShadowLayer(dp(6),0,dp(2),0x88061627);
        LinearLayout.LayoutParams quote=new LinearLayout.LayoutParams(-1,-2);
        quote.topMargin=dp(7);thought.addView(harmonyQuoteValue,quote);
        harmonyDateValue=text("",12,0xffc0d3e3);
        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-1,-2);
        dateParams.topMargin=dp(10);thought.addView(harmonyDateValue,dateParams);
        harmonyQuoteValue.setVisibility(prefs.getBoolean("harmony_daily_quote",true)?View.VISIBLE:View.GONE);
        if(HarmonyVisionLayout.showNativeWeatherWidget()){
            weatherWidget=new WeatherWidgetView(this,this::showWeatherForecast);
            harmonyWeatherCard=weatherWidget;
            weatherWidget.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&grid!=null){if(continueWatching!=null&&continueWatching.isShown()&&continueWatching.focusEntry())return true;grid.focusKey(focusedPkg);return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP&&searchButton!=null){searchButton.requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT&&harmonyClock!=null&&harmonyClock.getParent()!=null){
                    harmonyClock.requestFocus();return true;
                }
                return key==KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            int weatherWidth=Math.max(210,Math.min(285,Math.round(screenW*.29f)));
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(weatherWidth),-2);
            wp.setMarginStart(dp(screenW<780?8:24));
            stage.addView(weatherWidget,wp);
            weatherWidget.active(resumed);
            if(weatherSnapshot!=null&&weatherSnapshotKey.equals(weatherCacheKey())
                    &&HomeWeatherPolicy.fresh("forecast",weatherSnapshotKey,weatherCacheKey(),
                    weatherSnapshotAt,System.currentTimeMillis(),WEATHER_CACHE_MS))
                weatherWidget.bind(weatherSnapshot,weatherCity(),prefs.getBoolean("weather_fahrenheit",false));
        }
        harmonyClock.active(resumed&&!prefs.getBoolean("hide_clock",false));
        return stage;
    }
    /** Strict focus bridge; no geometric focus jump from Watch Next into hidden controls. */
    boolean onHarmonyContinueKey(int key){
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(grid!=null){grid.focusKey(focusedPkg);return true;}
            return false;
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP){
            if(harmonyClock!=null&&harmonyClock.isShown())return harmonyClock.requestFocus();
            if(weatherWidget!=null&&weatherWidget.isShown())return weatherWidget.requestFocus();
            if(searchButton!=null)return searchButton.requestFocus();
        }
        return false;
    }
    private void harmonyClockSettings(){
        if(!harmony())return;
        String[] names={"Watch face","Metal finish","Clock accent","Second hand","Time format","Clock size","Hide clock","Daily thoughts"};
        String[] hints={"Heritage, Chronograph, Skeleton","Space Blue, Obsidian, Titanium, Ceramic",
                "Ice blue, Gold or Rose","Smooth sweep or Quartz tick","12-hour or 24-hour",
                "Fit the clock on the TV","Show or hide the dial","An original thought for every day"};
        int[] icons={R.drawable.ic_clock,R.drawable.ic_palette,R.drawable.ic_palette,R.drawable.ic_clock,
                R.drawable.ic_clock,R.drawable.ic_clock,R.drawable.ic_clock,R.drawable.ic_clock};
        GlassSettings.options(this,"Harmony Vision Clock",names,hints,icons,which->{
            switch(which){
                case 0:chooseSetting("Watch face",new String[]{"Heritage","Chronograph","Skeleton"},
                        i->{prefs.edit().putInt("harmony_clock_face",i).apply();if(harmonyClock!=null)harmonyClock.configure(prefs);});break;
                case 1:chooseSetting("Metal finish",new String[]{"Space Blue","Obsidian","Titanium","Ceramic"},
                        i->{prefs.edit().putInt("harmony_clock_finish",i).apply();if(harmonyClock!=null)harmonyClock.configure(prefs);});break;
                case 2:chooseSetting("Clock accent",new String[]{"Ice Blue","Gold","Rose"},
                        i->{prefs.edit().putInt("harmony_clock_accent",i).apply();if(harmonyClock!=null)harmonyClock.configure(prefs);});break;
                case 3:chooseSetting("Second hand",new String[]{"Smooth sweep","Quartz tick"},
                        i->{prefs.edit().putBoolean("harmony_clock_sweep",i==0).apply();if(harmonyClock!=null)harmonyClock.configure(prefs);});break;
                case 4:chooseSetting("Time format",new String[]{"12-hour","24-hour"},
                        i->{prefs.edit().putBoolean("time_24h",i==1).apply();if(harmonyClock!=null)harmonyClock.configure(prefs);updateClock();});break;
                case 5:chooseSetting("Harmony clock size",new String[]{"Small","Normal","Large"},
                        i->{prefs.edit().putInt("harmony_clock_size",i).apply();buildHomeSoon();});break;
                case 6:chooseSetting("Hide clock",new String[]{"Show","Hide"},
                        i->{prefs.edit().putBoolean("hide_clock",i==1).apply();buildHomeSoon();});break;
                case 7:chooseSetting("Daily thoughts",new String[]{"On","Off"},
                        i->{prefs.edit().putBoolean("harmony_daily_quote",i==0).apply();buildHomeSoon();});break;
            }
        });
    }
    private Bitmap avatarBitmap;
    private Bitmap customWallpaper;
    private ClockSampleKey contrastKey;
    private int wallpaperClockInk=0xffffffff;
    private LiquidBackdrop liquidBackdrop, headerBackdrop;
    private ContinueWatchingView continueWatching;
    private SkyClock sky;
    private ConnectivityManager connectivity;
    private boolean networkRegistered, resumed, tv;
    private final android.content.BroadcastReceiver appChanges = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) { loadApps(); }
    };
    private int pageWidth;
    /** Wallpaper-adaptive text: true while the region behind the text is bright (dark text in use). */
    /** Effective clock text size in sp (after the compact-screen cap), for fitClockText(). */
    private int clockTextSize = 68;
    private boolean tvGlass() { return tv && TvGlassStyle.enabled(prefs); }
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
    private boolean movingRendered;
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
        if (resumed && hasWindowFocus() && !GitHubUpdates.manualActive()) launch(new Intent(this, LandscapeActivity.class));
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
            }
            updateOnlineWallpaper();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    /** Re-applies backgrounds so tiles match the current day/night theme without a rebuild. */
    private void refreshTileSurfaces() {
        for (java.util.Map.Entry<String, View> entry : tileViews.entrySet()) {
            View tile = entry.getValue();
            boolean focused = tile.isFocused();
            Object tag = tile.getTag();
            int accent = tag instanceof Integer ? (Integer) tag : CYAN;
            if(tile instanceof HarmonyAppTile)((HarmonyAppTile)tile).appearance(focused);
            else if(tile instanceof GlassIconTile)((GlassIconTile)tile).appearance(accent,focused);
            else {tile.setBackground(appSurface(accent,focused));tile.setForeground(tileBorder(accent,focused));}
        }
        if (sky != null) sky.invalidate();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        onCreateStartMs = android.os.SystemClock.elapsedRealtime();
        BootLog.mark("home onCreate");
        prefs = ThemePreferences.get(this);
        prefs.registerOnSharedPreferenceChangeListener(weatherPreferences);
        if (!prefs.getBoolean("bing_daily_home_v67", false)) {
            prefs.edit().putBoolean("bing_daily_home_v67", true)
                    .putInt("wallpaper_source", 1).putString("wallpaper_mode", "mountains").apply();
        }
        if (!prefs.getBoolean("reference_layout_v42", false)) {
            prefs.edit().putBoolean("reference_layout_v42", true).putString("theme_mode", "dark")
                    .apply();
        }
        if (!prefs.getBoolean("reference_layout_v43", false)) {
            prefs.edit().putBoolean("reference_layout_v43", true).putInt("background_dim", 0).apply();
        }
        UiModeManager modes = getSystemService(UiModeManager.class);
        tv = modes != null && modes.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        ThemeStyle.demoDefaults(prefs,tv);
        connectivity = getSystemService(ConnectivityManager.class);
        bluetooth = new GlassBluetooth(this, this::updateBluetooth);
        android.content.IntentFilter packages = new android.content.IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED); packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(appChanges, packages, android.content.Context.RECEIVER_EXPORTED);
        else registerReceiver(appChanges, packages);
        lastLight = appearanceDaylight() >= .5f;
        focusedPkg = prefs.getString("home_focus_pkg", null);
        if (state != null) gridState = state.getParcelable("home_grid_state");
        loadAvatar();
        buildHome();
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
        if(ThemeStyle.custom(prefs)||harmony()) return GlassSettings.themeAccent(this);
        switch (prefs.getInt("theme_name", 0)) {
            case 1: return 0xffff9a5c; // Sunset Orange
            case 2: return 0xffb388ff; // Deep Purple
            case 3: return prefs.getInt("custom_accent", CYAN);
            default: return CYAN; // Midnight Cyan
        }
    }

    /** Tile corner radius from settings: sharp / rounded / round. */
    private int tileRadius() {
        return dp(HomeGeometry.tileRadius(prefs.getInt("tile_corners",1)));
    }

    private GradientDrawable surface(boolean focused) { return surface(focused,false); }
    private GradientDrawable appSurface(boolean focused) { return surface(focused,true); }
    private GradientDrawable appSurface(int accent,boolean focused){return glassOs()?GlassAppCard.surface(this,accent,focused,tileRadius()):appSurface(focused);}
    private GradientDrawable surface(boolean focused,boolean drawer) {
        if (TvGlassStyle.enabled(prefs)) return drawer
                ? TvGlassStyle.drawerCard(tileRadius(),dp(1),focused,prefs)
                : TvGlassStyle.surface(tileRadius(),dp(focused?2:1),focused,prefs);
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
        shape.setAlpha(PanelTransparency.alpha(ClassicGlassAlpha.card(intensity,focused),prefs,drawer));
        shape.setCornerRadius(tileRadius());
        shape.setStroke(dp(focused ? 3 : 1), focused ? accent : 0xff28435e);
        return shape;
    }

    private GradientDrawable avatarSurface(boolean focused) {
        return avatarSurface(focused,ThemeStyle.custom(prefs)&&tv?52:44);
    }
    private GradientDrawable avatarSurface(boolean focused,int size) {
        GradientDrawable shape=surface(focused);
        if (prefs.getInt("avatar_shape",0)==0) shape.setShape(GradientDrawable.OVAL);
        else shape.setCornerRadius(dp(HomeGeometry.avatarRadius(size)));
        return shape;
    }
    private void avatarOutline(ImageView photo) {
        photo.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {
                if (prefs.getInt("avatar_shape",0)==0) outline.setOval(0,0,view.getWidth(),view.getHeight());
                else outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),
                        HomeGeometry.avatarRadius(Math.min(view.getWidth(),view.getHeight())));
            }
        });
        photo.setClipToOutline(true);
    }
    private void chooseAvatarShape() {
        chooseSetting("Avatar shape",new String[]{"Round","Rounded square"},which->{
            prefs.edit().putInt("avatar_shape",which).apply();
            if (avatar!=null) { avatar.setBackground(avatarSurface(avatar.isFocused())); avatar.invalidateOutline(); }
            refreshProfileAvatarPreview();
        });
    }

    private TextView text(String value, int size, int color) {
        // Outline removed 2026-11-01 (user: "buri lag rahi hai") — plain text with drop shadow only.
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeStyle.contentColor(prefs,color));
        applyAppFont(view);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(ThemeStyle.foreground(prefs));
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setMinimumWidth(0);
        view.setPadding(dp(14), dp(8), dp(14), dp(8));
        view.setBackground(surface(false));
        view.setOnFocusChangeListener((v, focus) -> v.setBackground(surface(focus)));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private GradientDrawable toolbarSurface(boolean focused) {
        GradientDrawable shape = surface(focused);
        shape.setCornerRadius(dp(tvGlass() ? 24 : 16));
        return shape;
    }

    private android.widget.ImageButton iconAction(String title, int resource, Runnable action) {
        android.widget.ImageButton icon = new android.widget.ImageButton(this);
        icon.setImageDrawable(TvSymbols.icon(this, resource));
        icon.setColorFilter(HeaderAppearance.foreground(prefs));
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        boolean glassIcons = TvGlassStyle.enabled(prefs);
        int symbolInset = glassIcons ? dp(tv ? 7 : 6) : dp(11);
        icon.setPadding(symbolInset, symbolInset, symbolInset, symbolInset);
        // CENTER_INSIDE capped these custom 24px drawables at their intrinsic size on dense screens.
        icon.setScaleType(glassIcons ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE);
        icon.setBackground(glassIcons ? null : toolbarSurface(false));
        icon.setClipToOutline(!glassIcons);
        icon.setFocusable(true);
        icon.setOnFocusChangeListener((v, focus) -> {
            if (glassIcons) {
                // Remote focus is visible through scale and colour, never a surrounding box.
                icon.setColorFilter(focus ? ThemeStyle.focusAccent(prefs) : HeaderAppearance.foreground(prefs));
                icon.setScaleX(focus ? 1.14f : 1f); icon.setScaleY(focus ? 1.14f : 1f);
            } else v.setBackground(toolbarSurface(focus));
        });
        icon.setOnClickListener(v -> action.run());
        icon.setOnKeyListener((v, code, event) -> toolbarKey(v, event));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(tv ? 48 : 44), dp(tv ? 48 : 44));
        p.setMargins(dp(2), 0, dp(2), 0);
        if(ThemeStyle.id(prefs)==ThemeStyle.COBALT && actions.getChildCount()>0) {
            View separator=new View(this); separator.setBackgroundColor(0x6672a0ed);
            actions.addView(separator,new LinearLayout.LayoutParams(dp(1),dp(24)));
        }
        actions.addView(icon,p);
        return icon;
    }

    private boolean toolbarKey(View focused, KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT
                && code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.isCanceled()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (grid != null) grid.focusKey(focusedPkg);
        } else if (code != KeyEvent.KEYCODE_DPAD_UP) {
            java.util.List<View> buttons = new java.util.ArrayList<>();
            for (int i = 0; i < actions.getChildCount(); i++)
                if (actions.getChildAt(i).isFocusable()&&actions.getChildAt(i).isShown()) buttons.add(actions.getChildAt(i));
            int index = buttons.indexOf(focused);
            if (index >= 0) {
                if(glassOs()&&index==0&&code==KeyEvent.KEYCODE_DPAD_LEFT&&avatar.getParent() instanceof View){if(grid!=null)grid.cancelPendingFocus();((View)avatar.getParent()).requestFocus();return true;}
                int next = Math.max(0, Math.min(buttons.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
                if (grid != null) grid.cancelPendingFocus();
                buttons.get(next).requestFocus();
            }
        }
        return true;
    }

    private void buildHome() {
        glassDefaults();
        if(telemetry!=null){telemetry.stop();telemetry=null;}
        if(weatherWidget!=null){weatherWidget.active(false);weatherWidget=null;}
        if(analogClock!=null){analogClock.active(false);analogClock=null;}
        if(harmonyClock!=null){harmonyClock.active(false);harmonyClock=null;}
        harmonyDateValue=null;harmonyGreetingValue=null;harmonyQuoteValue=null;harmonyWeatherCard=null;
        clockDatesInline=glassOs()&&getResources().getConfiguration().screenWidthDp>=700&&!weatherWidgetEnabled()&&prefs.getInt("clock_style",0)!=1;
        islamicDate=null;bluetoothStatus=null;focusedSpotlight=null;
        HeaderAppearance.clockDefaults(prefs);
        if (liquidBackdrop != null) { liquidBackdrop.close(); liquidBackdrop=null; }
        if (headerBackdrop != null) { headerBackdrop.close(); headerBackdrop=null; }
        long buildMs = android.os.SystemClock.elapsedRealtime();
        handler.removeCallbacks(buildHomeTask);
        buildHomePending = false;
        if (continueWatching != null) continueWatching.stop();
        if (grid != null && grid.getLayoutManager() != null) gridState = grid.getLayoutManager().onSaveInstanceState();
        tileViews.clear();
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
        if(harmony()&&tv){
            View tint=new View(this);
            tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x180d243b,0x7c071c34}));
            root.addView(tint,new ViewGroup.LayoutParams(-1,-1));
        }
        ScrollView scroll = new ScrollView(this);
        // Harmony's hero follows the header naturally; fill+centering introduced a huge void.
        scroll.setFillViewport(glassOs()&&!harmony());
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(tv ? (harmony()?24:32) : 16);
        page.setPadding(inset, dp(harmony()?8:14), inset, dp(harmony()?4:12));
        if(harmony()){page.setClipChildren(true);page.setClipToPadding(true);}
        root.addView(page, new ViewGroup.LayoutParams(-1, -1));
        // Cinematic shade: subtle dark gradients top and bottom for a film-like frame.
        GradientDrawable topShade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ThemeStyle.light(prefs)?0x00000000:TvGlassStyle.enabled(prefs) ? 0x22000000 : ClassicGlassAlpha.panel(TvGlassStyle.intensity(prefs)) << 24, 0x00000000});
        View topBar = new View(this);
        topBar.setBackground(topShade);
        topBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(topBar, new android.widget.FrameLayout.LayoutParams(-1, dp(110), Gravity.TOP));
        GradientDrawable bottomShade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{ThemeStyle.light(prefs)?0x00000000:0x66000000, 0x00000000});
        View bottomBar = new View(this);
        bottomBar.setBackground(bottomShade);
        bottomBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(bottomBar, new android.widget.FrameLayout.LayoutParams(-1, dp(110), Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            page.setPadding(inset + insets.getSystemWindowInsetLeft(), dp(harmony()?8:14) + insets.getSystemWindowInsetTop(),
                    inset + insets.getSystemWindowInsetRight(), dp(harmony()?4:12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        applyHeaderBackground();
        avatar = glassOs()?new AppAccentAvatar(this):new ImageView(this);
        avatar.setId(R.id.profileAvatar);
        avatar.setContentDescription(getString(R.string.h_edit_profile));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(avatarSurface(false));
        if(!glassOs()) avatarOutline(avatar);
        if (tvGlass()) { avatar.setPadding(dp(2), dp(2), dp(2), dp(2)); avatar.setElevation(dp(2)); }
        avatar.setFocusable(false);
        avatar.setOnClickListener(v -> LauncherPin.require(this, this::editProfile));
        avatar.setOnFocusChangeListener((v, focus) -> v.setBackground(avatarSurface(focus)));
        LinearLayout identityCard = new LinearLayout(this); identityCard.setGravity(Gravity.CENTER_VERTICAL);
        // The header already supplies the glass; a second profile-card fill hid the wallpaper detail.
        if (tvGlass()) identityCard.setPadding(dp(6), dp(5), dp(14), dp(5));
        identityCard.setOnClickListener(v -> LauncherPin.require(this, this::editProfile));
        if(glassOs()) {
            identityCard.setId(R.id.leftBlock);identityCard.setBackground(null);
            identityCard.setFocusable(true);identityCard.setOnKeyListener((v,c,e)-> {
                if(c==KeyEvent.KEYCODE_DPAD_RIGHT && e.getAction()==KeyEvent.ACTION_DOWN){networkStatus.requestFocus();return true;}
                if(c==KeyEvent.KEYCODE_DPAD_DOWN && e.getAction()==KeyEvent.ACTION_DOWN){grid.focusKey(focusedPkg);return true;}
                return c==KeyEvent.KEYCODE_DPAD_LEFT||c==KeyEvent.KEYCODE_DPAD_UP||c==KeyEvent.KEYCODE_DPAD_RIGHT||c==KeyEvent.KEYCODE_DPAD_DOWN;
            });
            identityCard.setOnFocusChangeListener((v,f)->v.setAlpha(f?1:.88f));
        }
        identityCard.addView(avatar,new LinearLayout.LayoutParams(dp(glassOs()?42:ThemeStyle.custom(prefs)&&tv?52:44),dp(glassOs()?42:ThemeStyle.custom(prefs)&&tv?52:44)));
        if(ThemeStyle.id(prefs)==ThemeStyle.PEARL) {
            View rule=new View(this); rule.setBackgroundColor(0x7778807c);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(1),dp(40)); rp.setMarginStart(dp(10)); identityCard.addView(rule,rp);
        }
        LinearLayout identity = new LinearLayout(this);
        identity.setPadding(dp(12), 0, dp(4), 0);
        identity.setOrientation(LinearLayout.VERTICAL);
        userName = text("", tvGlass() ? 20 : tv ? 24 : 20, TEXT);
        userName.setMaxLines(1);
        userName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        applyAppFont(userName);
        if(ThemeStyle.custom(prefs)) userName.setTypeface(userName.getTypeface(),ThemeStyle.id(prefs)==ThemeStyle.DUNE?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);
        identity.addView(userName);
        greeting = text(greetingText(), 13, MUTED);
        applyAppFont(greeting);
        if (prefs.getBoolean("hide_greeting", false)||(harmony()&&!HarmonyVisionLayout.showSecondGreeting())) greeting.setVisibility(View.GONE);
        identity.addView(greeting);
        if(glassOs()) {
            userName.setId(R.id.profileName);userName.setTextSize(19);userName.setTypeface(userName.getTypeface(),android.graphics.Typeface.BOLD);
            greeting.setId(R.id.greeting);greeting.setTextSize(12);greeting.setAllCaps(false);
            greeting.setTypeface(greeting.getTypeface(),android.graphics.Typeface.BOLD);
            identity.removeView(greeting);identity.addView(greeting);
        }
        identityCard.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
        identityParams.setMarginEnd(dp(10)); header.addView(identityCard, identityParams);
        networkStatus = text("", 13, MUTED);
        networkStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER);
        networkStatus.setCompoundDrawablePadding(dp(5));
        networkStatus.setFocusable(false);
        networkStatus.setOnFocusChangeListener((v, focus) -> {
            if(glassOs())updateNetwork();
            else if (focus) v.setBackground(surface(true));
            else v.setBackgroundColor(Color.TRANSPARENT);
        });
        networkStatus.setPadding(dp(tv ? 13 : 11), dp(8), dp(tv ? 13 : 11), dp(8));
        networkStatus.setOnClickListener(v -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        page.addView(header);
        actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if(glassOs()) {actions.setId(R.id.topButtons);networkStatus.setFocusable(true);networkStatus.setOnKeyListener((v,c,e)->toolbarKey(v,e));}
        actions.addView(networkStatus, new LinearLayout.LayoutParams(-2, dp(44)));
        homeWeather = text("Unavailable", 13, MUTED);
        homeWeather.setGravity(Gravity.CENTER);
        homeWeather.setPadding(dp(10), dp(8), dp(10), dp(8));
        if(harmony())homeWeather.setVisibility(View.GONE);
        actions.addView(homeWeather, 0, new LinearLayout.LayoutParams(-2, dp(44)));
        if(glassOs()&&!harmony()) {
            bluetoothStatus=text("Bluetooth",11,0xffdbeafe);bluetoothStatus.setId(R.id.btnBluetooth);
            bluetoothStatus.setMaxWidth(dp(130));bluetoothStatus.setSingleLine();bluetoothStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bluetoothStatus.setPadding(dp(10),dp(8),dp(10),dp(8));bluetoothStatus.setGravity(Gravity.CENTER);
            android.graphics.drawable.Drawable btIcon=TvSymbols.icon(this,android.R.drawable.stat_sys_data_bluetooth);btIcon.setBounds(0,0,dp(18),dp(18));bluetoothStatus.setCompoundDrawablePadding(dp(5));bluetoothStatus.setCompoundDrawables(btIcon,null,null,null);
            bluetoothStatus.setBackground(GlassOsUi.panel(this,999));bluetoothStatus.setFocusable(true);
            bluetoothStatus.setOnFocusChangeListener((v,f)->v.setBackground(surface(f)));
            bluetoothStatus.setOnClickListener(v->bluetooth.show());bluetoothStatus.setOnKeyListener((v,c,e)->toolbarKey(v,e));
            actions.addView(bluetoothStatus,new LinearLayout.LayoutParams(-2,dp(40)));
            iconAction("Apps",R.drawable.ic_apps,this::showAppLibrary);
        }else if(harmony()){
            iconAction("All apps",R.drawable.ic_apps,this::showAppLibrary);
        }
        searchButton = iconAction(getString(R.string.h_search), R.drawable.ic_search, this::searchApps);
        iconAction(getString(R.string.h_notifications), R.drawable.ic_notifications, this::showNotifications);
        if(!ThemeStyle.custom(prefs)&&!harmony()) iconAction(getString(R.string.h_screensaver), R.drawable.ic_picture, () -> launch(new Intent(this, LandscapeActivity.class)));
        android.widget.ImageButton controls=iconAction("Control Center", R.drawable.ic_settings, this::showSettings);
        if(glassOs()) controls.setId(R.id.btnControlCenter);
        if (tv) {
            header.addView(actions, new LinearLayout.LayoutParams(-2, -2));
        } else {
            styleStatusBar(actions);
            page.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }
        clockLayout = new LinearLayout(this);
        LinearLayout clock = clockLayout;
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(weatherWidgetEnabled()||glassOs()?Gravity.START:new int[]{Gravity.START,Gravity.CENTER_HORIZONTAL,Gravity.END}[clockAlignment()]);
        boolean compact = getResources().getConfiguration().screenHeightDp < 480;
        clock.setPadding(0, dp(compact?0:tv?(ThemeStyle.custom(prefs)?32:10):16), 0, dp(compact ? 8 : 12));
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
        clockSize=HomeGeometry.clockSize(clockSize,compact);
        time = text("", clockSize, TEXT);
        time.setGravity(Gravity.CENTER);
        if(glassOs()) time.setId(R.id.clock);
        time.setSingleLine(true);
        // Clock shadow for readability on bright wallpapers (toggle).
        if (prefs.getBoolean("clock_shadow", true)) {
            time.setShadowLayer(dp(8), 0, dp(3), 0xcc000000);
        }
        // Use the regular family with bold weight; the light family overrides the intended weight.
        time.setTypeface(android.graphics.Typeface.create(
                ThemeStyle.clockFontFamily(prefs),ThemeStyle.clockBold(prefs)?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL));
        // Analog mode uses a bounded independent dial, including on the Glass gradient.
        boolean analogClock = prefs.getInt("clock_style", 0) == 1 && !harmony();
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
        date = text("",ThemeStyle.custom(prefs)?(tv?20:16):(tv?14:13),MUTED);
        if (prefs.getBoolean("clock_shadow", true)) {
            date.setShadowLayer(dp(6), 0, dp(2), 0xcc000000);
        }
        date.setGravity(weatherWidgetEnabled()?Gravity.START:Gravity.CENTER);
        if(glassOs()) {date.setId(R.id.date);ampm.setId(R.id.ampm);ampm.setTextSize(18);}
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL | new int[]{Gravity.START,Gravity.CENTER_HORIZONTAL,Gravity.END}[clockAlignment()]);
        timeRow.addView(time, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams ampmParams = new LinearLayout.LayoutParams(-2, -2);
        ampmParams.gravity = glassOs()?Gravity.CENTER_VERTICAL:Gravity.BOTTOM;
        ampmParams.bottomMargin = dp(glassOs()?0:6);if(glassOs())ampmParams.setMarginStart(dp(8));
        timeRow.addView(ampm, ampmParams);
        clock.addView(timeRow, new LinearLayout.LayoutParams(-1, dp(clockSize + (compact ? 10 : 36))));
        clock.addView(date);
        if(glassOs()) {
            time.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            ampm.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            GradientDrawable ampmBadge=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{harmony()?0xff406c92:0xff1b3855,harmony()?0xff183b65:0xff102941});ampmBadge.setCornerRadius(dp(10));ampmBadge.setStroke(dp(1),0x7796bdf5);
            ampm.setTextColor(Color.WHITE);ampm.setTextSize(20);ampm.setBackground(ampmBadge);ampm.setPadding(dp(8),dp(4),dp(8),dp(4));
            ampm.setMinWidth(dp(56));ampm.setMinHeight(dp(36));ampm.setGravity(Gravity.CENTER);ampm.setShadowLayer(0,0,0,Color.TRANSPARENT);
            clock.setPadding(0,0,0,0);
            islamicDate=text("",13,MUTED);islamicDate.setId(R.id.lunarDate);
            clock.addView(islamicDate);
            if(clockDatesInline) {
                clock.removeView(date);clock.removeView(islamicDate);
                View divider=new View(this);divider.setId(R.id.clockDivider);divider.setBackgroundColor(0x38ffffff);
                LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(dp(1.5f),dp(48));line.setMargins(dp(12),0,dp(12),0);timeRow.addView(divider,line);
                LinearLayout dates=new LinearLayout(this);dates.setOrientation(LinearLayout.VERTICAL);
                date.setTextSize(18);date.setTypeface(date.getTypeface(),android.graphics.Typeface.BOLD);date.setGravity(Gravity.START);dates.addView(date);dates.addView(islamicDate);
                timeRow.addView(dates,new LinearLayout.LayoutParams(0,-2,1));
            }
        }
        if(!glassOs()) {islamicDate=text("",12,MUTED);islamicDate.setId(R.id.lunarDate);clock.addView(islamicDate);}
        if(analogClock){
            timeRow.setVisibility(View.GONE);clock.removeView(date);clock.removeView(islamicDate);
            LinearLayout analogRow=new LinearLayout(this);analogRow.setGravity(Gravity.CENTER_VERTICAL);analogRow.setPadding(dp(glassOs()?16:8),0,0,0);
            this.analogClock=new SweepAnalogClock(this);
            int preferred=dp(glassOs()?208:196);
            analogRow.addView(this.analogClock,new LinearLayout.LayoutParams(preferred,preferred));
            LinearLayout dates=new LinearLayout(this);dates.setOrientation(LinearLayout.VERTICAL);dates.setGravity(Gravity.CENTER_VERTICAL);
            date.setTextSize(15);date.setGravity(Gravity.START);date.setMaxLines(2);islamicDate.setTextSize(13);islamicDate.setMaxLines(2);islamicDate.setPadding(0,dp(10),0,0);
            dates.addView(date,new LinearLayout.LayoutParams(-1,-2));dates.addView(islamicDate,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout.LayoutParams dpDates=new LinearLayout.LayoutParams(0,-2,1);dpDates.setMarginStart(dp(16));analogRow.addView(dates,dpDates);
            clock.addView(analogRow,0,new LinearLayout.LayoutParams(-1,-2));this.analogClock.active(resumed);
            SweepAnalogClock dial=this.analogClock;
            analogRow.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
                if(r-l<=0)return;
                int available=Math.max(1,r-l-analogRow.getPaddingLeft());
                int diameter=Math.min(preferred,Math.max(dp(144),available-dp(132)));
                diameter=Math.min(diameter,available);
                LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)dial.getLayoutParams();
                if(lp.width!=diameter){lp.width=diameter;lp.height=diameter;dial.setLayoutParams(lp);}
                int[] pos=new int[2],origin=new int[2];dial.getLocationInWindow(pos);root.getLocationInWindow(origin);sky.clockX=pos[0]-origin[0]+dial.getWidth()/2f;sky.clockY=pos[1]-origin[1]+dial.getHeight()/2f;sky.invalidate();
            });
        }
        timeRow.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol&&time.getText().length()>0)fitClockText(time.getText());});
        if(glassOs()&&!harmony()&&!analogClock) {
            focusedSpotlight=text("",13,0xffffffff);focusedSpotlight.setSingleLine();focusedSpotlight.setEllipsize(android.text.TextUtils.TruncateAt.END);
            focusedSpotlight.setPadding(dp(13),dp(7),dp(13),dp(7));focusedSpotlight.setBackground(GlassOsUi.panel(this,999));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.topMargin=dp(8);clock.addView(focusedSpotlight,sp);updateSpotlight();
        }
        time.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int[] position = new int[2], origin = new int[2];
            time.getLocationInWindow(position);
            root.getLocationInWindow(origin);
            sky.clockX = position[0] - origin[0] + time.getWidth() / 2f;
            sky.clockY = position[1] - origin[1] + time.getHeight() / 2f;
            sky.invalidate();
            if(updateWallpaperClockInk()) adaptVisibleTextColors();
        });
        appHeading = text("Your apps", prefs.getInt("focus_title_size", tv ? 32 : 26), TEXT);
        appHeading.setSingleLine(true);
        appHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appHeading.setPadding(dp(4), dp(8), 0, dp(12));
        grid = new HomeAppGrid<>(this, app -> app.info.activityInfo.packageName,
                (app, width) -> createAppTile(app, width, carriedApp != null && app.info.activityInfo.packageName.equals(carriedApp.info.activityInfo.packageName)));
        grid.setReferenceExpansion(glassOs()&&(!harmony()||HarmonyVisionLayout.expandAppOnFocus()));
        grid.setUpFromApps(() -> { if(harmony()&&continueWatching!=null&&continueWatching.isShown()&&continueWatching.focusEntry())return;
            if(harmony()&&harmonyClock!=null&&harmonyClock.isShown())harmonyClock.requestFocus();
            else if(weatherWidget!=null&&weatherWidget.isShown())weatherWidget.requestFocus();
            else if(searchButton!=null)searchButton.requestFocus(); });
        grid.setClipChildren(false);
        if(glassOs()) {
            grid.setId(R.id.grid);grid.setPadding(0,dp(harmony()?5:16),0,dp(harmony()?4:18));
            if(!harmony()||HarmonyVisionLayout.showDockReflections())grid.addItemDecoration(new DockDecoration.Reflections());
        }
        // Focus ring belongs inside a strictly bounded RecyclerView; never draw over the hero.
        grid.setClipToPadding(glassOs()&&!harmony());
        if(harmony())grid.setClipChildren(true);
        appBody = new LinearLayout(this);
        appBody.setOrientation(LinearLayout.VERTICAL);
        if(glassOs()){appBody.setClipChildren(!harmony());appBody.setClipToPadding(!harmony());}
        // App cards float directly on wallpaper in every visual style.
        boolean liquidGlass=TvGlassStyle.enabled(prefs);
        appBody.setBackground(harmony()&&HarmonyVisionLayout.showDockPanel()?HarmonyVisionSurface.status(this):null);
        // No opaque drawer behind the reflective first row in Harmony.
        appBody.setPadding(0,0,0,0);
        appBody.setClipToOutline(false);
        if(ThemeStyle.id(prefs)==ThemeStyle.COBALT) grid.addItemDecoration(new CobaltRails(dp(1)));
        if(ThemeStyle.custom(prefs)&&tv) grid.addOnLayoutChangeListener((v,l,t,rr,bb,ol,ot,orr,obb)->placeDemoCards());
        continueWatching = new ContinueWatchingView(this, label -> { if (!prefs.getBoolean("apps_only", false)) appHeading.setText(label); });
        if (!prefs.getBoolean("apps_only", false)&&!harmony()) grid.setFooter(continueWatching);
        appBody.addView(grid, new LinearLayout.LayoutParams(-1, -1));
        // Scrollable content: clock only. The "Your apps" header and the
        // drawer panel stay fixed below; tiles scroll inside the panel.
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        if(glassOs()&&!harmony())scrollContent.setGravity(Gravity.CENTER_VERTICAL);
        if ((!weatherWidgetEnabled()&&prefs.getBoolean("hide_clock",false)) || prefs.getBoolean("apps_only",false)) clock.setVisibility(View.GONE);
        int align = clockAlignment();
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, -2);
        clockParams.gravity = new int[]{Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END}[align];
        if(harmony()&&tv&&!prefs.getBoolean("apps_only",false)) {
            LinearLayout.LayoutParams heroParams=new LinearLayout.LayoutParams(-1,-2);
            heroParams.bottomMargin=dp(HarmonyVisionLayout.heroBottomGapDp());
            scrollContent.addView(harmonyHero(),heroParams);
            // A separate bounded Watch Next shelf below the clock, never overlaying app tiles.
            scrollContent.addView(continueWatching,new LinearLayout.LayoutParams(-1,-2));
        } else if(glassOs()||weatherWidgetEnabled()) {
            LinearLayout stage=new LinearLayout(this);stage.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
            boolean wide=getResources().getConfiguration().screenWidthDp>=700;
            stage.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
            if(wide)stage.addView(clock,new LinearLayout.LayoutParams(0,-2,1));else stage.addView(clock,clockParams);
            LinearLayout instruments=new LinearLayout(this);instruments.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
            if(!wide)instruments.setOrientation(LinearLayout.VERTICAL);
            int available=getResources().getConfiguration().screenWidthDp-(tv?64:32);
            if(weatherWidgetEnabled()){
                weatherWidget=new WeatherWidgetView(this,this::showWeatherForecast);
                LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(Math.min(available,WeatherWidgetLayout.width(available,glassOs()))),-2);
                if(glassOs()&&wide)wp.setMarginEnd(dp(12));else if(!wide)wp.bottomMargin=dp(10);
                instruments.addView(weatherWidget,wp);
                weatherWidget.setOnKeyListener((v,key,event)->{
                    if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN){grid.focusKey(focusedPkg);return true;}
                    if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT){if(searchButton!=null)searchButton.requestFocus();return true;}
                    return key==KeyEvent.KEYCODE_DPAD_RIGHT;
                });
                weatherWidget.active(resumed);
            }
            if(glassOs()){
                int cardWidth=wide?(weatherWidgetEnabled()?WeatherWidgetLayout.telemetryWidth(available):Math.min(315,getResources().getConfiguration().screenWidthDp/3)):Math.min(280,available);
                if(harmony()) instruments.addView(harmonyShortcuts(),new LinearLayout.LayoutParams(dp(cardWidth),-2));
                else {
                    telemetry=new GlassTelemetry(this);
                    instruments.addView(telemetry,new LinearLayout.LayoutParams(dp(cardWidth),-2));if(resumed)telemetry.start();
                }
            }
            stage.addView(instruments);if(prefs.getBoolean("apps_only",false))stage.setVisibility(View.GONE);scrollContent.addView(stage,new LinearLayout.LayoutParams(-1,-2));
        }else scrollContent.addView(clock,clockParams);
        scroll.addView(scrollContent, new ViewGroup.LayoutParams(-1, -2));
        boolean referenceStage=glassOs()&&!prefs.getBoolean("apps_only",false);
        page.addView(scroll, referenceStage?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(-1,-2));
        if(harmony()||ThemeStyle.custom(prefs)||glassOs())appHeading.setVisibility(View.GONE);
        page.addView(appHeading);
        page.addView(appBody,referenceStage?new LinearLayout.LayoutParams(-1,referenceDockHeight()):new LinearLayout.LayoutParams(-1,0,1));
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
        grid.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override public void onScrolled(androidx.recyclerview.widget.RecyclerView view, int dx, int dy) {
                adaptVisibleTextColors();
                glowTracker.onScrollChange(view, 0, 0, 0, 0);
            }
        });
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            adaptVisibleTextColors();
            glowTracker.onScrollChange(v, scrollX, scrollY, oldScrollX, oldScrollY);
        });
        page.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = page.getWidth() - page.getPaddingLeft() - page.getPaddingRight();
            if (width > 0 && width != pageWidth) {
                pageWidth = width;
                if(glassOs()&&!prefs.getBoolean("apps_only",false)){
                    LinearLayout.LayoutParams body=(LinearLayout.LayoutParams)appBody.getLayoutParams();
                    int height=referenceDockHeight();if(body.height!=height){body.height=height;appBody.setLayoutParams(body);}
                }
                if (cachedApps != null) renderApps(cachedApps);
                else loadApps();
            }
        });
        refreshProfile();
        updateClock();
        updateNetwork();
        // No wallpaper-capture layer is needed behind the open app grid.
        if (!glassOs() && headerBgMode()!=0 && (!ThemeStyle.custom(prefs)||headerBgMode()==2)) headerBackdrop=tv
                ? new LiquidBackdrop(sky,appWorker,handler,headerBgMode()==2?2:-1,header)
                : new LiquidBackdrop(sky,appWorker,handler,headerBgMode()==2?2:-1,header,actions);
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
        if (resumed && !prefs.getBoolean("apps_only", false)&&(!harmony()||HarmonyVisionLayout.showWatchNextFooter())) continueWatching.start();
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
        if(glassOs()) return hour>=5&&hour<12?"Good morning":hour<17&&hour>=12?"Good afternoon":hour>=17&&hour<22?"Good evening":"Good night";
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
        date.setText(new java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).format(now.getTime()));
        if(harmonyDateValue!=null)harmonyDateValue.setText(new java.text.SimpleDateFormat("EEE, d MMM",Locale.ENGLISH).format(now.getTime()));
        if(harmonyGreetingValue!=null)harmonyGreetingValue.setText(greetingText());
        if(harmonyQuoteValue!=null&&prefs.getBoolean("harmony_daily_quote",true)){
            String today=HarmonyDailyWords.forCalendar(now);
            if(!today.contentEquals(harmonyQuoteValue.getText()))harmonyQuoteValue.setText(today);
        }
        if(islamicDate!=null)try{
            java.time.LocalDate today=java.time.LocalDate.of(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));
            islamicDate.setText(HijriCalendar.format(today,prefs.getInt("hijri_adjustment",0)));
        }catch(java.time.DateTimeException unavailable){islamicDate.setText("Islamic date unavailable");}
        sky.minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        sky.clockStyle = 0; // The independent SweepAnalogClock draws analog mode in every theme.
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
        if((glassOs()||weatherWidgetEnabled()) && time.getParent() instanceof View && ((View)time.getParent()).getWidth()>0)
            maxWidth=Math.max(dp(70),((View)time.getParent()).getWidth()-dp(clockDatesInline?260:80));
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
    private boolean weatherOnline(){
        Network network=connectivity==null?null:connectivity.getActiveNetwork();
        NetworkCapabilities caps=network==null?null:connectivity.getNetworkCapabilities(network);
        return HomeWeatherPolicy.canAttempt(network!=null,caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
    }
    private boolean weatherNeedsForecast(){return weatherWidgetEnabled()||(forecastPanel!=null&&forecastPanel.isShowing());}
    private String weatherCity(){return prefs.getString("weather_city",LandscapeModel.CITY);}
    private void showWeatherForecast(){
        forecastPanel=new WeatherForecastPanel(this,()->{weatherForceRefresh=true;nextWeatherAttempt=0;refreshHomeWeather();},()->WeatherSettings.show(this));
        WeatherForecast available=weatherOnline()&&weatherSnapshotKey.equals(weatherCacheKey())&&HomeWeatherPolicy.fresh("forecast",weatherSnapshotKey,weatherCacheKey(),weatherSnapshotAt,System.currentTimeMillis(),WEATHER_CACHE_MS)?weatherSnapshot:null;
        forecastPanel.show(available,weatherCity(),prefs.getBoolean("weather_fahrenheit",false));refreshHomeWeather();
    }
    private void bindWeatherForecast(WeatherForecast model){
        if(weatherWidget!=null)weatherWidget.bind(model,weatherCity(),prefs.getBoolean("weather_fahrenheit",false));
        if(forecastPanel!=null)forecastPanel.update(model,weatherCity(),prefs.getBoolean("weather_fahrenheit",false));
    }
    private void refreshHomeWeather() {
        if(homeWeather==null||!resumed||isDestroyed())return;
        String cacheKey=weatherCacheKey();long now=System.currentTimeMillis();boolean widget=weatherWidgetEnabled(),forecastNeeded=weatherNeedsForecast();
        String requestKey=cacheKey+(forecastNeeded?"|forecast":"|current");
        handler.removeCallbacks(weatherRefresh);homeWeather.setVisibility(widget||harmony()?View.GONE:View.VISIBLE);
        if(!weatherOnline()){setHomeWeather("Unavailable");bindWeatherForecast(null);nextWeatherAttempt=0;handler.postDelayed(weatherRefresh,60_000L);return;}
        WeatherSettings.resolvePhonePlace(this, false);
        String cached=prefs.getString("weather_cached",null);long cachedAt=prefs.getLong("weather_cached_at",0);
        boolean currentFresh=HomeWeatherPolicy.fresh(cached,prefs.getString("weather_cache_key",""),cacheKey,cachedAt,now,WEATHER_CACHE_MS);
        boolean modelFresh=weatherSnapshot!=null&&HomeWeatherPolicy.fresh("forecast",weatherSnapshotKey,cacheKey,weatherSnapshotAt,now,WEATHER_CACHE_MS);
        setHomeWeather(currentFresh?cached:"Unavailable");bindWeatherForecast(modelFresh?weatherSnapshot:null);
        if(!weatherForceRefresh&&currentFresh&&(!forecastNeeded||modelFresh)){
            handler.postDelayed(weatherRefresh,Math.max(60_000L,WEATHER_CACHE_MS-(now-(forecastNeeded?Math.min(cachedAt,weatherSnapshotAt):cachedAt))));return;
        }
        if(weatherBusy)return;
        if(!weatherForceRefresh&&requestKey.equals(lastWeatherRequestKey)&&now<nextWeatherAttempt){handler.postDelayed(weatherRefresh,Math.max(1000,nextWeatherAttempt-now));return;}
        boolean force=weatherForceRefresh;weatherForceRefresh=false;weatherBusy=true;lastWeatherRequestKey=requestKey;
        String latitude=prefs.getString("weather_lat",Double.toString(LandscapeModel.LATITUDE)),longitude=prefs.getString("weather_lon",Double.toString(LandscapeModel.LONGITUDE));
        boolean fahrenheit=prefs.getBoolean("weather_fahrenheit",false);
        weatherWorker.execute(()->{
            WeatherForecast forecast=null;String summary=null,encoded=null;long fetched=System.currentTimeMillis();
            try{
                if(forecastNeeded){
                    String persisted=prefs.getString("weather_forecast_json",null);long timestamp=prefs.getLong("weather_forecast_at",0);
                    if(!force&&HomeWeatherPolicy.fresh(persisted,prefs.getString("weather_forecast_key",""),cacheKey,timestamp,fetched,WEATHER_CACHE_MS)){
                        try{forecast=WeatherForecast.decode(persisted);fetched=timestamp;}catch(Exception invalid){forecast=null;}
                    }
                    if(forecast==null){forecast=WeatherForecast.fetch(Double.parseDouble(latitude),Double.parseDouble(longitude),fahrenheit);fetched=System.currentTimeMillis();}
                    WeatherOrbView.prepareMoon(this);
                    WeatherImages.prepare(this);
                    encoded=forecast.encode();summary=forecast.summary(fahrenheit);
                }else{summary=WeatherClient.fetch(Double.parseDouble(latitude),Double.parseDouble(longitude),fahrenheit);fetched=System.currentTimeMillis();}
            }catch(Exception unavailable){android.util.Log.w("PersonalWeather","Weather unavailable ("+unavailable.getClass().getSimpleName()+")");}
            WeatherForecast result=forecast;String value=summary,cache=encoded;long fetchedAt=fetched;
            handler.post(()->{
                weatherBusy=false;if(isDestroyed())return;
                if(!cacheKey.equals(weatherCacheKey())){nextWeatherAttempt=0;refreshHomeWeather();return;}
                if(value!=null){
                    SharedPreferences.Editor saved=prefs.edit().putString("weather_cached",value).putLong("weather_cached_at",fetchedAt).putString("weather_cache_key",cacheKey);
                    if(result!=null){weatherSnapshot=result;weatherSnapshotKey=cacheKey;weatherSnapshotAt=fetchedAt;saved.putString("weather_forecast_json",cache).putLong("weather_forecast_at",fetchedAt).putString("weather_forecast_key",cacheKey);}saved.apply();
                }else{weatherSnapshot=null;weatherSnapshotKey="";prefs.edit().putLong("weather_cached_at",0).putLong("weather_forecast_at",0).apply();}
                nextWeatherAttempt=System.currentTimeMillis()+(value==null?60_000L:WEATHER_CACHE_MS);
                if(!resumed)return;
                boolean online=weatherOnline();setHomeWeather(online&&value!=null?value:"Unavailable");
                WeatherForecast ready=result!=null?result:weatherSnapshot!=null&&HomeWeatherPolicy.fresh("forecast",weatherSnapshotKey,cacheKey,weatherSnapshotAt,System.currentTimeMillis(),WEATHER_CACHE_MS)?weatherSnapshot:null;
                bindWeatherForecast(online?ready:null);
                homeWeather.setVisibility(weatherWidgetEnabled()||harmony()?View.GONE:View.VISIBLE);
                handler.removeCallbacks(weatherRefresh);
                if((weatherNeedsForecast()&&!forecastNeeded)||weatherForceRefresh){nextWeatherAttempt=0;handler.post(weatherRefresh);}
                else handler.postDelayed(weatherRefresh,value==null?60_000L:Math.max(60_000L,WEATHER_CACHE_MS-(System.currentTimeMillis()-fetchedAt)));
            });
        });
    }

    /** Cache key: changes when city or units change, invalidating the cache. */
    private void setHomeWeather(String value) {
        homeWeather.setText(ThemeStyle.custom(prefs)?value.replace(" · "," "):value);
        if(ThemeStyle.custom(prefs)&&!"Unavailable".equals(value)) {
            StatusWeatherGlyph glyph=new StatusWeatherGlyph(value,Calendar.getInstance().get(Calendar.HOUR_OF_DAY),HeaderAppearance.foreground(prefs));
            glyph.setBounds(0,0,dp(22),dp(22)); homeWeather.setCompoundDrawablePadding(dp(6));
            homeWeather.setCompoundDrawablesRelative(glyph,null,null,null);
        } else homeWeather.setCompoundDrawablesRelative(null,null,null,null);
    }

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
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        networkStatus.setText(ThemeStyle.custom(prefs)?"":online?"Connected":caps==null?"Disconnected":"No internet access");
        networkStatus.setContentDescription(online?"Network connected":caps==null?"Network disconnected":"Network without internet");
        networkStatus.setTooltipText(online ? (wifi ? "Wi-Fi · Internet available" : "Internet available") : "No internet");
        int color = online ? 0xff59e493 : 0xffff6c78;
        networkStatus.setTextColor(ThemeStyle.custom(prefs)?HeaderAppearance.foreground(prefs):color);
        int iconId = wifi || caps == null ? R.drawable.ic_wifi
                : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? R.drawable.ic_ethernet : R.drawable.ic_internet;
        android.graphics.drawable.Drawable icon = TvSymbols.icon(this, iconId);
        icon.setTint(ThemeStyle.custom(prefs)?HeaderAppearance.foreground(prefs):color);
        icon.setBounds(0, 0, dp(28), dp(24));
        networkStatus.setCompoundDrawables(icon, null, null, null);
        networkStatus.setContentDescription(online ? "Internet available. Open Wi-Fi settings" : "No validated internet access. Open Wi-Fi settings");
        if(glassOs()) {
            String transport=caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?"Ethernet":wifi||caps==null?"Wi-Fi":"Internet";
            networkStatus.setText("● "+transport+" • "+(online?"Online":"No Internet"));networkStatus.setTextSize(11);
            icon.setBounds(0,0,dp(18),dp(18));networkStatus.setCompoundDrawables(icon,null,null,null);
            networkStatus.setTextColor(online?0xffdcfce7:0xfffee2e2);
            GradientDrawable state=new GradientDrawable();state.setColor(online?0x8014532d:0x947f1d1d);state.setCornerRadius(dp(999));state.setStroke(dp(networkStatus.isFocused()?2:1),online?0xb322c55e:0xccef4444);networkStatus.setBackground(state);
            if(avatar instanceof AppAccentAvatar)((AppAccentAvatar)avatar).online(online);
        }
        refreshHomeWeather();
    }

    private volatile int appGeneration;
    private void loadApps() {
        if (carriedApp != null) { carriedApp = null; moveOrder = null; }
        int generation = ++appGeneration;
        List<App> prior = cachedApps;
        appWorker.execute(() -> {
            List<ResolveInfo> found = new ArrayList<>();
            if (tv) found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            found.addAll(getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            Set<String> packages = new HashSet<>();
            List<App> apps = new ArrayList<>();
            java.util.Map<String, App> previous = new java.util.HashMap<>();
            List<App> snapshot = prior;
            if (snapshot != null) for (App app : snapshot) previous.put(app.info.activityInfo.packageName, app);
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
        });
    }

    /** Only bound/visible tiles request artwork. Serial work and generation checks bound concurrency. */
    private void requestAppIcon(App app) {
        if (app.iconReady || app.iconRequest.pending(appGeneration) || isDestroyed()) return;
        int generation = appGeneration;
        app.iconRequest.begin(generation);
        appWorker.execute(() -> {
            if (generation != appGeneration || isDestroyed()) return;
            String pkg = app.info.activityInfo.packageName;
            try {
                if(harmony()){
                    android.graphics.drawable.Drawable nativeIcon=app.info.loadIcon(getPackageManager());
                    handler.post(()->{
                        if(!app.iconRequest.finish(generation))return;
                        if(!isDestroyed()&&generation==appGeneration)
                            updateTileIcon(pkg,nativeIcon,ThemeStyle.accent(prefs));
                    });
                    return;
                }
                IconCache cache = new IconCache(this);
                android.content.pm.PackageManager pm = getPackageManager();
                long version = IconCache.version(pm, pkg);
                Bitmap card = cache.load(pkg, version);
                int accent;
                if (card != null) accent = cache.accent(pkg, version, CYAN);
                else {
                    android.graphics.drawable.Drawable logo = app.info.loadIcon(pm);
                    if (pkg.equals("com.google.android.youtube.tv") || pkg.equals("com.google.android.youtube")) logo = getDrawable(R.drawable.logo_youtube);
                    if (pkg.equals("com.netflix.ninja") || pkg.equals("com.netflix.mediaclient")) logo = getDrawable(R.drawable.logo_netflix);
                    if(cache.mark()) logo=DemoBrandMark.choose(pkg,logo);
                    AppArtwork artwork = AppArtwork.create(getResources(), logo, cache.logoScale(),cache.mark());
                    card = ((android.graphics.drawable.BitmapDrawable) artwork.image).getBitmap(); accent = artwork.accent;
                    cache.save(pkg, version, card, accent);
                }
                Bitmap finished = card; int finishedAccent = accent;
                handler.post(() -> {
                    if (!app.iconRequest.finish(generation)) return;
                    if (!isDestroyed() && generation == appGeneration) {
                        updateTileIcon(pkg, new android.graphics.drawable.BitmapDrawable(getResources(), finished), finishedAccent);
                        trimAppIcons();
                    }
                });
            } catch (RuntimeException removed) { handler.post(() -> app.iconRequest.finish(generation)); }
        });
    }

    private void trimAppIcons() {
        if (cachedApps == null) return;
        long bytes = 0;
        for (App app : cachedApps) if (app.icon instanceof android.graphics.drawable.BitmapDrawable)
            bytes += ((android.graphics.drawable.BitmapDrawable) app.icon).getBitmap().getAllocationByteCount();
        long budget = MemoryProfile.iconCacheBytes(MemoryProfile.effectiveTier(this, prefs));
        for (App app : cachedApps) {
            if (bytes <= budget) break;
            if (!app.iconReady || tileViews.containsKey(app.info.activityInfo.packageName)) continue;
            if (app.icon instanceof android.graphics.drawable.BitmapDrawable)
                bytes -= ((android.graphics.drawable.BitmapDrawable) app.icon).getBitmap().getAllocationByteCount();
            app.icon = null; app.iconReady = false; // Visible tiles will reload from disk; never recycle a drawn bitmap.
        }
    }

    /** Cheap letter placeholder so the grid is useful before icons finish. Runs on the worker thread. */
    private android.graphics.drawable.Drawable letterIcon(String name) {
        boolean square=harmony();
        Bitmap bitmap=Bitmap.createBitmap(square?96:160,square?96:90,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        if(square){
            paint.setColor(0xff537d9a);
            canvas.drawRoundRect(5,5,91,91,21,21,paint);
            paint.setColor(0xfff5fbff);
            paint.setTextSize(47);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);
            canvas.drawText(letter,48,64,paint);
        }else{
            if(!ThemeStyle.custom(prefs))canvas.drawColor(0xff0d2036);
            paint.setColor(ThemeStyle.custom(prefs)?accentColor():0x6640d9ff);
            paint.setTextSize(44);
            paint.setTextAlign(Paint.Align.CENTER);
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);
            canvas.drawText(letter,80,60,paint);
        }
        return new android.graphics.drawable.BitmapDrawable(getResources(),bitmap);
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
        if(tile instanceof HarmonyAppTile){((HarmonyAppTile)tile).artwork(icon,accent);tile.setTag(accent);}
        else if(tile instanceof GlassIconTile){((GlassIconTile)tile).artwork(icon,accent);tile.setTag(accent);if(tile.isFocused())updateGlassAccent(accent);}
        else if (tile instanceof android.widget.FrameLayout) {
            View iconView = ((android.widget.FrameLayout) tile).getChildAt(0);
            if (iconView instanceof ImageView) ((ImageView) iconView).setImageDrawable(icon);
            tile.setForeground(tileBorder(accent, tile.isFocused()));
            tile.setTag(accent);
            if(glassOs())tile.setBackground(appSurface(accent,tile.isFocused()));
            if(glassOs()&&tile.isFocused())updateGlassAccent(accent);
        }
    }

    private static final class App {
        final String name;
        final ResolveInfo info;
        android.graphics.drawable.Drawable icon;
        int accent;
        boolean iconReady;
        final ArtworkRequestState iconRequest = new ArtworkRequestState();
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
        boolean restoreFocus = !grid.footerFocusedOrPending()&&(grid.hasFocus() || (focusedPkg != null && !hasWindowFocus()));
        cachedApps = apps;
        appHeading.setText(harmony()?"YOUR APPS":getString(R.string.h_your_apps));
        HomeLibraryStore library = new HomeLibraryStore(this);
        List<App> visible = orderedApps(apps);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        visible.removeIf(app -> hidden.contains(app.info.activityInfo.packageName)
                || (!selectedCategory.isEmpty() && !selectedCategory.equals(library.category(app.info.activityInfo.packageName))));
        int avail = gridWidth();
        int columns = harmony()?HarmonyVisionLayout.dockColumns(Math.round(avail/getResources().getDisplayMetrics().density)):
                PersonalHomeModel.columns((int)(avail/getResources().getDisplayMetrics().density),
                prefs.getInt(tv?"tv_columns":"phone_columns",0),prefs.getBoolean("large_tiles",false));
        grid.submit(visible, columns, avail / columns);
        if (movingRendered) { movingRendered = false; grid.rebind(); }
        if (gridState != null) { grid.getLayoutManager().onRestoreInstanceState(gridState); gridState = null; }
        appHeading.setText(visible.isEmpty()?R.string.h_no_apps:R.string.h_your_apps);
        if (restoreFocus && (panel == null || !panel.isShowing()) && (searchDialog == null || !searchDialog.isShowing()))
            grid.focusKey(focusedPkg);
        firstGridRender = false;
        if (searchRefresh != null) searchRefresh.run();
    }

    private void chooseCategoryFilter() {
        List<String> names = new HomeLibraryStore(this).categories(); names.add(0, getString(R.string.h_all_apps));
        new ThemeDialogBuilder(this).setTitle(R.string.h_categories).setItems(names.toArray(new String[0]), (dialog, which) -> {
            selectedCategory = which == 0 ? "" : names.get(which); gridState = null;
            if (cachedApps != null) renderApps(cachedApps);
        }).show();
    }

    private File profileAsset(String name) { return new File(getFilesDir(), new HomeLibraryStore(this).assetName(name)); }

    /** Direct move mode: long-press > Move, then arrows carry the tile, OK drops it, Back cancels. */
    private void startMoveMode(App app) {
        if (cachedApps == null) return;
        List<App> visible = orderedApps(cachedApps);
        Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
        visible.removeIf(a -> hidden.contains(a.info.activityInfo.packageName));
        moveOrder = new ArrayList<>();
        for (App a : visible) moveOrder.add(a.info.activityInfo.packageName);
        selectedCategory = "";
        carriedApp = app;
        renderMoveGrid();
        Toast.makeText(this, "Arrows move · OK places · Back cancels", Toast.LENGTH_LONG).show();
    }

    private void renderMoveGrid() {
        movingRendered = true;
        java.util.Map<String, App> byPackage = new java.util.LinkedHashMap<>();
        for (App app : cachedApps) byPackage.put(app.info.activityInfo.packageName, app);
        appHeading.setText("Move " + carriedApp.name);
        int avail = gridWidth();
        int columns = PersonalHomeModel.columns((int) (avail / getResources().getDisplayMetrics().density),
                prefs.getInt(tv ? "tv_columns" : "phone_columns", 0), prefs.getBoolean("large_tiles", false));
        String movingPkg = carriedApp.info.activityInfo.packageName;
        List<App> moved = new ArrayList<>();
        for (String pkg : moveOrder) { App app = byPackage.get(pkg); if (app != null) moved.add(app); }
        grid.submit(moved, columns, avail / columns); grid.rebind(); grid.focusKey(movingPkg);
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
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_MUTE && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0 && !event.isCanceled()) VolumeSlider.nativeMute(this);
        if (VolumeKeys.direction(event.getKeyCode()) != 0
                && !PassiveHomeService.enabled(this) && VolumeKeys.control(this, event)) return true;
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
        if (grid != null && grid.navigateKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private final Runnable demoPlacement=this::placeDemoCards;
    private void placeDemoCards() {
        if(isDestroyed()||grid==null||!ThemeStyle.custom(prefs)||!tv||grid.getChildCount()==0) return;
        int row=grid.getChildAt(0).getHeight();
        int top=Math.max(dp(10),Math.min(grid.getHeight()/4,grid.getHeight()-row*2-dp(14)));
        if(grid.getPaddingTop()!=top) grid.setPadding(0,top,0,dp(14));
    }

    private int referenceCardHeight(int cellWidth){return referenceCardHeight(cellWidth,grid==null?4:grid.getColumnCount());}
    private int referenceCardHeight(int cellWidth,int columns){
        int spacing=Math.max(0,Math.min(2,prefs.getInt("tile_spacing",1)));
        int margin=(tvGlass()?new int[]{dp(6),dp(10),dp(16)}:new int[]{dp(4),dp(8),dp(14)})[spacing];
        int widthDp=Math.round((GlassDockLayout.compactCellWidth(cellWidth*columns,columns)-margin*2)/getResources().getDisplayMetrics().density);
        int height=GlassDockLayout.iconTileHeight(widthDp,getResources().getConfiguration().screenHeightDp,referenceCaptionHeight());
        if(harmony())height=HarmonyVisionLayout.dockTileHeightDp(getResources().getConfiguration().screenHeightDp);
        return dp(height);
    }
    private int referenceCaptionHeight(){return GlassDockLayout.captionHeight(prefs.getInt("app_label_size",16),getResources().getConfiguration().fontScale);}
    private int referenceDockHeight(){
        int available=pageWidth>0?gridWidth():dp(Math.max(160,getResources().getConfiguration().screenWidthDp-(tv?64:32)));
        int cols=harmony()?HarmonyVisionLayout.dockColumns(Math.round(available/getResources().getDisplayMetrics().density)):PersonalHomeModel.columns(Math.round(available/getResources().getDisplayMetrics().density),prefs.getInt(tv?"tv_columns":"phone_columns",0),prefs.getBoolean("large_tiles",false));
        int card=referenceCardHeight(available/cols,cols);
        int spacing=Math.max(0,Math.min(2,prefs.getInt("tile_spacing",1)));
        int margin=new int[]{6,8,12}[spacing];
        if(harmony())return dp(HarmonyVisionLayout.dockHeightDp(getResources().getConfiguration().screenHeightDp));
        return card+dp(GlassDockLayout.reflectionHeight(Math.round(card/getResources().getDisplayMetrics().density))+margin*2+44);
    }
    private View createAppTile(App app, int width, boolean moving) {
        requestAppIcon(app);
        android.widget.FrameLayout tile = harmony()?new HarmonyAppTile(this,app.name,app.icon,app.accent,HarmonyVisionLayout.dockPlateDp(getResources().getConfiguration().screenHeightDp)):glassOs()?new GlassIconTile(this,app.name,app.icon,app.accent,referenceCardHeight(width)-dp(referenceCaptionHeight()),dp(referenceCaptionHeight()),tileRadius(),moving):ThemeStyle.custom(prefs)?new DemoTile(this,ThemeStyle.id(prefs),tileRadius()):new android.widget.FrameLayout(this);
        if(!(tile instanceof GlassIconTile)&&!(tile instanceof HarmonyAppTile)){
        tile.setBackground(appSurface(app.accent,false));
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {
                outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),tileRadius());
            }
        });
        tile.setClipToOutline(!ThemeStyle.custom(prefs));
        }else tile.setClipToOutline(false);
        tile.setElevation(perfAllows("parallax") ? dp(tvGlass() ? 4 : 3) : 0);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(app.name);
        tile.setTag(app.accent);
        if(!(tile instanceof GlassIconTile)&&!(tile instanceof HarmonyAppTile))tile.setForeground(tileBorder(app.accent, false));
        if (moving) {
            tile.setScaleX(1.07f);
            tile.setScaleY(1.07f);
            tile.setElevation(dp(10));
            if(tile instanceof HarmonyAppTile)((HarmonyAppTile)tile).appearance(true);else if(tile instanceof GlassIconTile)((GlassIconTile)tile).appearance(app.accent,true);else tile.setForeground(tileBorder(accentColor(), true));
        }
        ImageView icon = new ImageView(this);
        if (app.icon != null) icon.setImageDrawable(app.icon);
        else icon.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_apps));
        // Artwork is composed at the card's 16:9 ratio; no second inset or crop here.
        icon.setScaleType(ThemeStyle.custom(prefs)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.FIT_XY);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if(ThemeStyle.custom(prefs)) {
            int logoWidth=Math.max(dp(26),Math.round(width*.25f));
            android.widget.FrameLayout.LayoutParams logo=new android.widget.FrameLayout.LayoutParams(logoWidth,-1,Gravity.START|Gravity.CENTER_VERTICAL);
            logo.setMarginStart(dp(10)); logo.topMargin=dp(10); logo.bottomMargin=dp(10); tile.addView(icon,logo);
            TextView title=text(app.name,prefs.getInt("app_label_size",tv?17:14),ThemeStyle.foreground(prefs));
            title.setShadowLayer(dp(2),0,dp(1),ThemeStyle.light(prefs)?0xeeffffff:0xee000000);
            title.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setTypeface(title.getTypeface(),ThemeStyle.id(prefs)==ThemeStyle.DUNE?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);
            android.widget.FrameLayout.LayoutParams caption=new android.widget.FrameLayout.LayoutParams(-1,-1);
            if(ThemeStyle.id(prefs)!=ThemeStyle.DUNE) {
                View separator=new View(this); separator.setBackgroundColor(ThemeStyle.light(prefs)?0x44708070:0x6682a2cf);
                android.widget.FrameLayout.LayoutParams rule=new android.widget.FrameLayout.LayoutParams(dp(1),-1);
                rule.setMarginStart(logoWidth+dp(15)); rule.topMargin=dp(18); rule.bottomMargin=dp(18); tile.addView(separator,rule);
            }
            caption.setMarginStart(logoWidth+dp(24)); caption.setMarginEnd(dp(10)); tile.addView(title,caption);
        }else if(!(tile instanceof GlassIconTile)&&!(tile instanceof HarmonyAppTile))tile.addView(icon,new android.widget.FrameLayout.LayoutParams(-1,-1));
        tile.setOnFocusChangeListener((v, focus) -> {
            if(tile instanceof HarmonyAppTile)((HarmonyAppTile)tile).appearance(focus||moving);
            else if(tile instanceof GlassIconTile)((GlassIconTile)tile).appearance(app.accent,focus||moving);
            else {tile.setBackground(appSurface(app.accent,focus));if(!moving)tile.setForeground(tileBorder(app.accent,focus));}
            if(focus)focusedPkg=app.info.activityInfo.packageName;
            if(glassOs()&&!harmony()&&!moving)grid.expandKey(focus?app.info.activityInfo.packageName:null);
            if (!moving) {
                // Focus zoom: gentle scale animation.
                float scale = focus ? (glassOs()?1f:tvGlass() ? 1.06f : 1.05f) : 1f;
                float sy=glassOs()?1:scale;
                float lift=0;
                v.animate().cancel();
                if(harmony()){
                    v.setScaleX(1f);v.setScaleY(1f);v.setTranslationY(0);
                }else if (MemoryProfile.effectiveTier(this, prefs) == MemoryProfile.TIER_LIGHT) {
                    v.setScaleX(scale); v.setScaleY(sy);v.setTranslationY(lift);
                } else v.animate().scaleX(scale).scaleY(sy).translationY(lift).setUpdateListener(a->grid.refreshReflections()).setDuration(tvGlass() ? 180 : 150).start();
                if(glassOs()&&!harmony())grid.refreshReflections();
            }
            tile.setTranslationZ(focus && prefs.getBoolean("focus_glow", true) ? dp(10) : 0);
            if(glassOs()&&!harmony()&&tile.getParent() instanceof View)((View)tile.getParent()).setTranslationZ(focus?dp(10):0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                tile.setOutlineAmbientShadowColor(app.accent);
                tile.setOutlineSpotShadowColor(app.accent);
            }
            if (focus) {
                if(glassOs()){spotlightName=app.name;spotlightCategory=appCategory(app);updateGlassAccent(app.accent);}
                if (!TvGlassStyle.enabled(prefs)) appHeading.setText(app.name);
                appHeading.setTextColor(ThemeStyle.foreground(prefs));
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
                        .translationY(perfAllows("parallax") ? (size.y / 2f - cy) / size.y * dp(36) : 0)
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
            v.animate().scaleX(glassOs()?1f:1.08f).scaleY(glassOs()?1f:1.08f).alpha(.7f).setDuration(120).withEndAction(() -> {
                v.setScaleX(glassOs()?1f:1.05f); v.setScaleY(glassOs()?1:1.05f); v.setAlpha(1f);
                open.run();
            }).start();
        });
        tile.setOnLongClickListener(v -> {
            new ThemeDialogBuilder(this).setTitle(app.name).setItems(new String[]{"App info", "Hide from launcher", "Move app", getString(R.string.h_category)}, (dialog, which) -> {
                if (which == 0) launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.activityInfo.packageName)));
                else if (which == 2) startMoveMode(app);
                else if (which == 3) HomeLibraryUi.category(this, app.info.activityInfo.packageName, () -> { if (cachedApps != null) renderApps(cachedApps); });
                else {
                    Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", Collections.emptySet()));
                    hidden.add(app.info.activityInfo.packageName);
                    prefs.edit().putStringSet("hidden_apps", hidden).apply();
                    refreshApps(); focusFirstApp();
                }
            }).show();
            return true;
        });
        android.widget.FrameLayout.LayoutParams position = new android.widget.FrameLayout.LayoutParams(-1, -2);
        int spacing = Math.max(0, Math.min(2, prefs.getInt("tile_spacing", 1)));
        int hm = harmony()?dp(3):(tvGlass() ? new int[]{dp(6), dp(10), dp(16)} : new int[]{dp(4), dp(8), dp(14)})[spacing];
        int vm = harmony()?dp(4):(glassOs()?new int[]{dp(6),dp(8),dp(12)}:tvGlass() ? new int[]{dp(8), dp(12), dp(18)} : new int[]{dp(5), dp(10), dp(16)})[spacing];
        position.width = width - hm * 2;
        position.height = ThemeStyle.custom(prefs)?Math.max(dp(54),Math.round(position.width/DemoGeometry.ratio()))+Math.round(DemoGeometry.footer(ThemeStyle.id(prefs),dp(1))):Math.round(position.width*9f/16f);
        if(harmony()){position.height=dp(HarmonyVisionLayout.dockTileHeightDp(getResources().getConfiguration().screenHeightDp));position.width=-1;}
        else if(glassOs()){position.height=referenceCardHeight(width);position.width=-1;}
        position.setMargins(hm, vm, hm, vm);
        tile.setLayoutParams(position);
        tile.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                tileViews.put(app.info.activityInfo.packageName,view);
                if(ThemeStyle.custom(prefs)&&tv) { grid.removeCallbacks(demoPlacement); grid.post(demoPlacement); }
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (tileViews.get(app.info.activityInfo.packageName) == view) tileViews.remove(app.info.activityInfo.packageName);
            }
        });
        return tile;
    }

    private void searchApps() {
        if (searchDialog != null && searchDialog.isShowing()) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Search installed apps");
        searchInput = input;
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setTextColor(ThemeStyle.foreground(prefs)); input.setHintTextColor(ThemeStyle.muted(prefs)); applyAppFont(input);
        input.setPadding(dp(16), dp(10), dp(16), dp(10)); input.setBackground(toolbarSurface(false));
        form.addView(input);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        ScrollView matches = new ScrollView(this); matches.addView(results);
        form.addView(matches, new LinearLayout.LayoutParams(-1, dp(tv ? 250 : 300)));
        searchDialog = new ThemeDialogBuilder(this).setTitle("Search apps").setView(form)
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
                View result = GlassSettings.row(this, app.name, null, R.drawable.ic_apps, GlassSettings.themeAccent(this), () -> {
                    if (searchDialog != null) searchDialog.dismiss();
                    launch(new Intent(Intent.ACTION_MAIN).setClassName(app.info.activityInfo.packageName, app.info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                });
                LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2); position.setMargins(0, dp(4), 0, dp(4));
                if (result instanceof LinearLayout && app.icon != null) {
                    ImageView resultIcon = (ImageView) ((LinearLayout) result).getChildAt(0);
                    resultIcon.setImageDrawable(app.icon.getConstantState() == null ? app.icon : app.icon.getConstantState().newDrawable(getResources()));
                    resultIcon.clearColorFilter(); resultIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
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
        searchDialog.show(); GlassSettings.styleDialog(this, searchDialog); searchRefresh.run();
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

    private void showNotifications() {
        if (panel != null && panel.isShowing()) panel.dismiss();
        panel = HomeNotificationsUi.show(this);
    }

    private void updateAmbient(){if(sky!=null)sky.invalidate();}
    private void updateGlassAccent(int color) {
        glassAccent=color;
        if(avatar instanceof AppAccentAvatar)((AppAccentAvatar)avatar).accent(color);
        if(sky!=null)sky.invalidate();updateSpotlight();
    }
    private String appCategory(App app){
        CharSequence value=android.content.pm.ApplicationInfo.getCategoryTitle(this,app.info.activityInfo.applicationInfo.category);
        return value==null?"App":value.toString();
    }
    private void updateSpotlight(){
        if(focusedSpotlight==null)return;
        String name=spotlightName.isEmpty()?getString(R.string.h_your_apps):spotlightName;
        String caption="●  "+name+(spotlightCategory.isEmpty()?"":"  • "+spotlightCategory);
        android.text.SpannableString text=new android.text.SpannableString(caption);
        text.setSpan(new android.text.style.ForegroundColorSpan(glassAccent),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int secondary=caption.indexOf("  • ");if(secondary>=0)text.setSpan(new android.text.style.ForegroundColorSpan(MUTED),secondary,caption.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        focusedSpotlight.setText(text);
    }
    private void updateBluetooth(){if(bluetoothStatus!=null&&bluetooth!=null)bluetoothStatus.setText(bluetooth.connectedName());}
    boolean libraryVisibility(String pkg,boolean visible){
        Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden_apps",Collections.emptySet()));
        if(visible)hidden.remove(pkg);else hidden.add(pkg);
        prefs.edit().putStringSet("hidden_apps",hidden).apply();return true;
    }
    private void showAppLibrary(){
        if(appLibrary!=null)appLibrary.close();
        appLibrary=new AppLibraryDialog(this,false,true,false,true,pkg->!prefs.getStringSet("hidden_apps",Collections.emptySet()).contains(pkg),this::refreshApps,()->{},Collections.emptySet());appLibrary.show();
    }
    private void showSettings() {
        if (panel != null && panel.isShowing()) { panel.dismiss(); return; }
        LauncherPin.require(this, this::showSettingsUnlocked);
    }

    private void showSettingsUnlocked() {
        panel = HomeDashboard.show(this, networkStatus.getText().toString(), () -> {
            gridState = null; buildHome();
        }, action -> {
            switch (action) {
                case HomeDashboard.SETTINGS: launcherSettings(); break;
                case HomeDashboard.SEARCH: searchApps(); break;
                case HomeDashboard.STORE: openStore(""); break;
                case HomeDashboard.SCREENSAVER: launch(new Intent(this, LandscapeActivity.class)); break;
                case HomeDashboard.CATEGORIES: chooseCategoryFilter(); break;
                case HomeDashboard.WIFI: openSetting(Settings.ACTION_WIFI_SETTINGS); break;
                case HomeDashboard.BLUETOOTH: if(glassOs())bluetooth.show();else launch(SystemSettings.bluetoothAccessoriesIntent(this)); break;
                case HomeDashboard.INPUTS: showInputs(); break;
                case HomeDashboard.DISPLAY: panel = TvControlPreview.show(this, true); break;
                case HomeDashboard.SOUND: panel = TvControlPreview.show(this, false); break;
                case HomeDashboard.IDENTITY: editProfile(); break;
                default: openSetting(Settings.ACTION_SETTINGS); break;
            }
        });
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
        String[] names = {"Cyan", "Orange", "Purple", "Green", "Red", "Yellow", "Pink", "White", "Black", "Graphite", "Silver", "Blue", "Teal", "Gold", "Rose", "Indigo"};
        int[] colors = {0xff40d9ff, 0xffff9a5c, 0xffb388ff, 0xff69f0ae, 0xffff5252, 0xffffea00, 0xffff80ab, 0xffffffff,
                0xff000000, 0xff343b46, 0xffcbd3df, 0xff508dff, 0xff44c6bc, 0xffe8c66a, 0xffd890a5, 0xff7986ed};
        chooseSetting("Accent color", names, which -> {
            prefs.edit().putInt("theme_name", 3).putInt("custom_accent", colors[which]).apply();
            applyAppearance(); refreshApps();
        });
    }

    private void launcherSettings() {
        String[] cats = {"Tiles", "Clock", "Theme", "Wallpaper", "Weather", "Apps", "System"};
        String[] catHints = {"App cards layout", "Time display", "Colors & appearance", "Background", "Widget, city and forecast", "Your apps", "Launcher system"};
        int[] catIcons = {R.drawable.ic_apps, R.drawable.ic_clock, R.drawable.ic_palette,
                R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_apps, R.drawable.ic_settings};
        GlassSettings.options(this, "Launcher settings", cats, catHints, catIcons, which -> {
            switch (which) {
                case 0: tileSettings(); break;
                case 1: clockSettings(); break;
                case 2: themeSettings(); break;
                case 3: wallpaperMenu(); break;
                case 4: weatherMenu(); break;
                case 5: appMenu(); break;
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

    /** Off, glass or frosted: independently saved for each visual style. */
    private int headerBgMode() { return HeaderAppearance.mode(prefs); }

    void liquidPanel(View target, int radius) { liquidPanel(target,radius,false); }
    void liquidDrawerPanel(View target, int radius) { liquidPanel(target,radius,true); }
    private void liquidPanel(View target, int radius, boolean drawer) {
        if(drawer) { target.setBackground(null); target.setClipToOutline(false); return; }
        if (!TvGlassStyle.enabled(prefs)) return;
        target.setBackground(drawer ? TvGlassStyle.drawer(dp(radius), dp(1), prefs)
                : TvGlassStyle.liquid(dp(radius), dp(1), prefs));
        target.setClipToOutline(true);
        if (liquidBackdrop != null) liquidBackdrop.bind(target);
    }

    private void applyHeaderBackground() {
        if (header==null) return;
        styleStatusBar(header);
    }
    private void styleStatusBar(View bar) {
        int mode=headerBgMode();
        GradientDrawable background=mode==0 ? null : glassOs()?GlassOsUi.panel(this,26):TvGlassStyle.header(dp(tvGlass()?28:18),dp(1),prefs,mode==2);
        bar.setBackground(background); bar.setClipToOutline(background!=null&&!ThemeStyle.custom(prefs));
        if (bar==header) bar.setPadding(background==null?0:dp(12),background==null?0:dp(8),
                background==null?0:dp(12),background==null?0:dp(8));
        else bar.setPadding(0,background==null?0:dp(5),0,background==null?0:dp(5));
    }

    /** Applies the launcher-wide font to a TextView. */
    private void applyAppFont(TextView view) {
        view.setTypeface(android.graphics.Typeface.create(
                ThemeStyle.fontFamily(prefs), android.graphics.Typeface.NORMAL));
    }

    /** Submenu helper with per-row icons. */
    private void subMenu(String title, String[] labels, String[] hints, int[] icons, Runnable[] choices) {
        if (!ThemeStyle.fixedTypography(prefs)) { GlassSettings.options(this, title, labels, hints, icons, which -> choices[which].run()); return; }
        List<Integer> visible = new ArrayList<>();
        for (int i=0;i<labels.length;i++) {
            if ("Launcher font".equals(labels[i]) || "Clock font".equals(labels[i]) || "Clock alignment".equals(labels[i])) continue;
            visible.add(i);
        }
        String[] names = new String[visible.size()], descriptions = new String[visible.size()];
        int[] symbols = new int[visible.size()];
        for (int i=0;i<visible.size();i++) {
            int source = visible.get(i); names[i]=labels[source]; descriptions[i]=hints[source]; symbols[i]=icons[source];
        }
        GlassSettings.options(this, title, names, descriptions, symbols, which -> choices[visible.get(which)].run());
    }

    private void tileSettings() {
        String[] labels = {"App columns", "Tile size", "Tile spacing", "Tile corners", "Logo size", ThemeStyle.custom(prefs)?"App label size":"Focused app title size", "App colour glow", "Focus border", "App entrance"};
        String[] hints = {"Number of cards per row", "Compact or large cards", "Gap between cards", "Sharp, rounded or round", "App logo size in cards", ThemeStyle.custom(prefs)?"Text beside the app icon":"Name above the app grid", "Match the focused app", "Thin, normal or thick", "Staggered slide-in"};
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
                prefs.edit().putInt("tile_corners", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Logo size", new String[]{"Small", "Normal", "Large"}, which -> {
                prefs.edit().putInt("logo_size", which).apply();
                appWorker.execute(() -> new IconCache(this).clear());
                cachedApps = null;
                loadApps();
            }),
            () -> chooseSetting(ThemeStyle.custom(prefs)?"App label size":"Focused app title size",new String[]{"Small","Normal","Large","Extra large"},which->{
                if(ThemeStyle.custom(prefs)) { prefs.edit().putInt("app_label_size",new int[]{14,17,20,24}[which]).apply(); refreshApps(); }
                else { int size=new int[]{24,32,40,48}[which]; prefs.edit().putInt("focus_title_size",size).apply(); appHeading.setTextSize(size); }
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
        if(harmony()&&tv){harmonyClockSettings();return;}
        String[] labels = {"Clock size", "Clock style", "Clock font", "Time format", "Clock alignment", "Clock seconds", "Clock glow", "Clock shadow", "Hide clock", "Hide greeting", "Hijri day adjustment"};
        String[] hints = {"Small, normal or large", "Digital or analog", "Bold, serif or mono", "12-hour or 24-hour", "Left, center or right", "Show seconds", "Halo behind the clock", "Shadow for readability", "Hide the clock", "Hide the greeting", "Adjust for local moon sighting"};
        int[] icons = {R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock, R.drawable.ic_clock};
        Runnable[] choices = {
            () -> chooseSetting("Clock size", new String[]{"Small", "Normal", "Large"}, which -> {
                prefs.edit().putInt("clock_size", (ThemeStyle.custom(prefs)?DemoGeometry.clockSizes(tv):tv?new int[]{42,52,62}:new int[]{36,44,52})[which]).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Clock style", new String[]{"Digital", "Analog"}, which -> {
                prefs.edit().putInt("clock_style", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Clock font", new String[]{"Bold", "Serif", "Mono"}, which -> {
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
            () -> chooseSetting("Hijri day adjustment",new String[]{"-2 days","-1 day","Calculated date","+1 day","+2 days"},which->{prefs.edit().putInt("hijri_adjustment",which-2).apply();updateClock();}),
        };
        subMenu("Clock", labels, hints, icons, choices);
    }

    private void switchVisualStyle(int style) {
        if (ThemeStyle.id(prefs)==style) return;
        int priorLogo=prefs.getInt("logo_size",1); boolean priorMark=ThemeStyle.id(prefs)!=ThemeStyle.CLASSIC;
        boolean wasHarmony=harmony();
        appGeneration++; // Reject artwork started for the previous style, even if its palette was similar.
        prefs.edit().putInt("visual_style",style).apply();
        ThemeStyle.demoDefaults(prefs,tv);
        boolean reload=wasHarmony!=harmony()||priorLogo!=prefs.getInt("logo_size",1) || priorMark!=(ThemeStyle.id(prefs)!=ThemeStyle.CLASSIC);
        if(cachedApps!=null) for(App app:cachedApps) {
            if(reload) { app.iconReady=false; app.icon=null; }
        }
        if(ThemeStyle.custom(prefs) && !prefs.getBoolean("material_defaults_v1",false)) {
            prefs.edit().putBoolean("material_defaults_v1",true).putInt("tile_corners",style==ThemeStyle.DUNE||style==ThemeStyle.AMBER?0:1)
                    .putInt("phone_columns",2).putInt("tv_columns",4)
                    .putString("theme_mode",ThemeStyle.light(prefs)?"light":"dark")
                    .putBoolean("clock_shadow",true).apply();
        }
        GlassSettings.dismiss(this);
        buildHomeSoon();
        handler.removeCallbacks(themeMenuRefresh); handler.postDelayed(themeMenuRefresh,300);
    }

    private final Runnable themeMenuRefresh=()-> {
        if (resumed && !isFinishing() && !isDestroyed() && !GlassSettings.showing(this)) themeSettings();
    };

    private void themeSettings() {
        // "Dark mode" removed 2026-11-01 (user): the theme glass color covers it.
        String[] labels = {"Visual style", "Theme", "Accent color", "Launcher font", "Glass intensity", "Glass finish", "Glass edge", "Panel transparency", "Header background", "Avatar shape", "Reset current theme"};
        String[] hints = {"Eight independent visual styles", "Cyan, Orange, Purple or Custom", "Custom theme color", "Font for the launcher", "Transparency of panels", "Soft, Clear or Frosted panels", "Reflection around glass surfaces", "Solid panels when off; app drawer stays transparent", "Status-bar glass in this theme", "Round or rounded-square profile photo", "Restore defaults for the active style"};
        int[] icons = {R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_palette, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_brightness, R.drawable.ic_person, android.R.drawable.ic_popup_sync};
        Runnable[] choices = {
            () -> chooseSetting("Visual style", new String[]{"Sadique Glass OS", "Classic", "Dune Loom", "Pearl Current", "Cobalt Rail", "Sage Fold", "Pine Mist", "Amber Night", "Harmony Vision"}, this::switchVisualStyle),
            () -> chooseSetting("Theme", new String[]{ThemeStyle.custom(prefs)?"Theme default":harmony()?"Harmony Blue":"Midnight Cyan", "Sunset Orange", "Deep Purple", "Custom"}, which -> {
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
            () -> chooseSetting("Glass finish", new String[]{"Soft", "Clear", "Frosted"}, which -> {
                prefs.edit().putInt("glass_finish", which).apply();
                buildHomeSoon();
            }),
            () -> chooseSetting("Glass edge", new String[]{"Off", "Subtle", "Bright"}, which -> {
                prefs.edit().putInt("glass_edge", which).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Panel transparency", new String[]{"On", "Off"}, which -> {
                prefs.edit().putBoolean("panel_transparency", which==0).apply(); buildHomeSoon();
            }),
            () -> chooseSetting("Header background",new String[]{"Off","Glass","Frosted"},which->{
                prefs.edit().putInt("header_bg_mode",which).apply(); buildHomeSoon();
            }),
            this::chooseAvatarShape,
            () -> chooseSetting("Reset theme", new String[]{"Keep current settings", "Reset to original defaults"}, which -> {
                if (which == 1) {
                    resetCurrentTheme();
                }
            })
        };
        subMenu("Theme", labels, hints, icons, choices);
    }

    private void resetCurrentTheme() {
        int currentStyle = ThemeStyle.id(prefs);
        String prefix = ThemeStyle.prefix(currentStyle);
        SharedPreferences raw = getSharedPreferences("personal_home", 0);
        SharedPreferences.Editor editor = raw.edit();
        for (String key : raw.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
        ThemeStyle.demoDefaults(prefs, tv);
        appGeneration++;
        if (cachedApps != null) {
            for (App app : cachedApps) {
                app.iconReady = false;
                app.icon = null;
            }
        }
        applyAppearance();
        buildHomeSoon();
        Toast.makeText(this,"Theme settings reset to defaults",Toast.LENGTH_SHORT).show();
    }

    private void wallpaperMenu() {
        // Removed 2026-11-01 (user): "Save as favorite", "Day / night wallpaper",
        // "Smooth day/night", and the built-in Midnight mountains / Solid midnight options.
        String[] labels = {"Wallpaper", "Wallpaper source", "Wallpaper slideshow", "Wallpaper darkness", "Bottom shade"};
        String[] hints = {"Your own picture", "Built-in, online or favorite", "Change wallpaper over time", "Keep the landscape visible", "Dark gradient behind tiles"};
        int[] icons = {R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_picture, R.drawable.ic_brightness, R.drawable.ic_brightness};
        Runnable[] choices = {
            this::wallpaperSettings,
            () -> chooseSetting("Wallpaper source", glassOs()?new String[]{"Built-in", "Online", "Favorite", "Gradient only"}:new String[]{"Built-in", "Online", "Favorite"}, which -> {
                SharedPreferences.Editor wallpaper=prefs.edit().putInt("wallpaper_source",which==3?0:which);
                if(glassOs())wallpaper.putString("wallpaper_mode",which==3?"solid":"mountains");wallpaper.apply();
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
                else {
                    SharedPreferences.Editor settings=prefs.edit().putBoolean("continue_watching",which==0);
                    if(which==0)settings.putBoolean("apps_only",false);settings.apply();
                    buildHomeSoon();
                }
            }),
        };
        subMenu("Apps", labels, hints, icons, choices);
    }

    private void systemMenu() {
        String[] labels = {"Screensaver & weather", "Profile name & photo", "Performance", "Updates", "Export settings", "Import settings", "Reset layout & unhide apps"};
        String[] hints = {"Photos, weather & idle timer", "Name and local profile picture", "Light, Balanced or Full", "Automatic GitHub updates", "Save to a file", "Restore from a file", "Restore the default layout"};
        int[] icons = {R.drawable.ic_picture, R.drawable.ic_person, R.drawable.ic_settings, R.drawable.ic_internet, R.drawable.ic_settings, R.drawable.ic_settings, R.drawable.ic_settings};
        Runnable[] choices = {
            this::screensaverSettings,
            this::editProfile,
            () -> chooseSetting("Performance", new String[]{"Auto", "Light", "Balanced", "Full"}, which -> {
                prefs.edit().putInt("perf_mode", which).apply();
                applyPerformanceProfile();
                buildHomeSoon();
            }),
            () -> LauncherUpdatesUi.show(this),
            this::exportSettings,
            this::importSettings,
            () -> new ThemeDialogBuilder(this).setTitle("Reset layout and show all apps?").setPositiveButton("Reset", (dialog, which) -> {
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
                if (!profileAsset("personal-wallpaper.jpg").exists()) { Toast.makeText(this, "Choose a picture first", Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("wallpaper_mode", "custom").apply(); loadWallpaper();
            }
        });
    }

    private void loadWallpaper() {
        if (!"custom".equals(prefs.getString("wallpaper_mode", "mountains"))) return;
        int epoch = profileEpoch; File file = profileAsset("personal-wallpaper.jpg");
        worker.execute(() -> {
            Bitmap image = ProfileAssets.load(this, file, false);
            handler.post(() -> { if (!isDestroyed() && epoch == profileEpoch) { customWallpaper = image; if (sky != null) sky.invalidate(); adaptTextColors(); } });
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
        applyLabelScheme(islamicDate, false, true);
        applyLabelScheme(appHeading, false, false);
    }

    /** Fixed label style (2026-11-01): white text with a black outline halo and drop shadow,
     *  readable on any wallpaper. Replaces the old per-label adaptive colors. */
    private void applyLabelScheme(android.widget.TextView label, boolean dark, boolean muted) {
        if (label == null) return;
        if(glassOs()&&label==networkStatus)return;
        if(glassOs()&&label==ampm){label.setTextColor(Color.WHITE);return;}
        label.setTextColor(ThemeStyle.custom(prefs)?(muted?ThemeStyle.muted(prefs):ThemeStyle.foreground(prefs)):(muted ? 0xE6FFFFFF : 0xFFFFFFFF));
        boolean clockLabel=label==time || label==ampm || label==date || label==islamicDate;
        boolean headerLabel=label==userName || label==greeting || label==homeWeather || label==networkStatus;
        if(headerLabel) label.setTextColor(HeaderAppearance.foreground(prefs));
        if(glassOs()&&label==networkStatus)return;
        if(clockLabel) label.setTextColor(wallpaperClockInk);
        if(clockLabel && !prefs.getBoolean("clock_shadow",true)) label.setShadowLayer(0,0,0,Color.TRANSPARENT);
        else {
            boolean darkInk=clockLabel?wallpaperClockInk!=0xffffffff:headerLabel && ThemeStyle.light(prefs) && headerBgMode()!=0;
            label.setShadowLayer(dp(clockLabel?5:2),0,dp(clockLabel?2:1),darkInk?0x99ffffff:0xe6000000);
        }
    }

    /** Nine cached photo samples choose clock ink; the photo itself is never tinted for text contrast. */
    private boolean updateWallpaperClockInk() {
        if(sky==null) { contrastKey=null; return false; }
        Bitmap photo=sky.currentPicture();
        if(photo==null || photo.isRecycled()) {
            contrastKey=null; boolean changed=wallpaperClockInk!=0xffffffff; wallpaperClockInk=0xffffffff; return changed;
        }
        if(sky.getWidth()<=0 || sky.getHeight()<=0) return false;
        float y=sky.clockY>=0?sky.clockY:dp(150),x=sky.clockX>=0?sky.clockX:sky.getWidth()/2f;
        float[] frame=HomeAppearance.frame(sky.getWidth(),sky.getHeight(),photo.getWidth(),photo.getHeight(),y,photo==sky.backdrop);
        int px=Math.max(0,Math.min(photo.getWidth()-1,Math.round((x-frame[0])/(frame[2]-frame[0])*photo.getWidth())));
        int py=Math.max(0,Math.min(photo.getHeight()-1,Math.round((y-frame[1])/(frame[3]-frame[1])*photo.getHeight())));
        int shade=HomeAppearance.shade(prefs.getString("theme_mode","light"),sky.minute,prefs.getInt("background_dim",0));
        int darkInk=0xff11191c;
        if(contrastKey!=null && contrastKey.matches(photo,px,py,shade,darkInk)) return false;
        contrastKey=new ClockSampleKey(photo,px,py,shade,darkInk);
        int red=0,green=0,blue=0,dx=Math.max(1,photo.getWidth()/30),dy=Math.max(1,photo.getHeight()/30);
        for(int row=-1;row<=1;row++) for(int col=-1;col<=1;col++) {
            int pixel=photo.getPixel(Math.max(0,Math.min(photo.getWidth()-1,px+col*dx)),Math.max(0,Math.min(photo.getHeight()-1,py+row*dy)));
            red+=(pixel>>16)&255; green+=(pixel>>8)&255; blue+=pixel&255;
        }
        int background=MaterialAppearance.mix(Color.rgb(red/9,green/9,blue/9),0xff051327,shade/255f);
        // A white cloud or bright patch must not turn an otherwise colourful photo's clock black.
        boolean whiteWallpaper=true;
        for(int row=0;row<3 && whiteWallpaper;row++) for(int col=0;col<3;col++) {
            int pixel=photo.getPixel(Math.min(photo.getWidth()-1,Math.round(photo.getWidth()*(.15f+.35f*col))),
                    Math.min(photo.getHeight()-1,Math.round(photo.getHeight()*(.15f+.35f*row))));
            if(ThemeStyle.wallpaperClockInk(pixel,darkInk)==0xffffffff) { whiteWallpaper=false; break; }
        }
        int ink=whiteWallpaper?ThemeStyle.wallpaperClockInk(background,darkInk):0xffffffff; boolean changed=ink!=wallpaperClockInk; wallpaperClockInk=ink; return changed;
    }

    private void adaptTextColors() {
        if (liquidBackdrop != null) liquidBackdrop.refresh();
        if (headerBackdrop != null) headerBackdrop.refresh();
        updateWallpaperClockInk();
        adaptVisibleTextColors();
    }

    /** Applies the current light/dark text scheme to every wallpaper-backed label. */
    private void chooseSetting(String title, String[] options, java.util.function.IntConsumer selected) {
        GlassSettings.choices(this, title, options, () -> settingCurrentIndex(title), which -> {
            selected.accept(which);
            GlassSettings.refresh(this);
        });
    }

    /** Index of the currently applied option for a settings chooser (-1 when none applies). (User 2026-11-01.) */
    private int settingCurrentIndex(String title) {
        switch (title) {
            case "Accent color": return prefs.getInt("theme_name",0)!=3?-1:indexOf(new int[]{0xff40d9ff,0xffff9a5c,0xffb388ff,0xff69f0ae,0xffff5252,0xffffea00,0xffff80ab,0xffffffff,
                    0xff000000,0xff343b46,0xffcbd3df,0xff508dff,0xff44c6bc,0xffe8c66a,0xffd890a5,0xff7986ed},prefs.getInt("custom_accent",0));
            case "Panel transparency": return PanelTransparency.enabled(prefs) ? 0 : 1;
            case "Glass finish": return TvGlassStyle.finish(prefs);
            case "Glass edge": return Math.max(0, Math.min(2, prefs.getInt("glass_edge", 1)));
            case "Visual style": return ThemeStyle.id(prefs);
            case "Wallpaper darkness": return Math.min(2, prefs.getInt("background_dim", 0) / 20);
            case "App colour glow": return prefs.getBoolean("focus_glow", true) ? 0 : 1;
            case "Header background": return headerBgMode();
            case "App columns": {
                int v = prefs.getInt(tv ? "tv_columns" : "phone_columns", 0);
                return v == 0 ? 0 : v - 1;
            }
            case "Tile size": return prefs.getBoolean("large_tiles", false) ? 1 : 0;
            case "Tile spacing": return prefs.getInt("tile_spacing", 1);
            case "Tile corners": return prefs.getInt("tile_corners", 1);
            case "Logo size": return Math.max(0, Math.min(2, prefs.getInt("logo_size", 1)));
            case "App label size": return indexOf(new int[]{14,17,20,24},prefs.getInt("app_label_size",tv?17:14));
            case "Focused app title size": return indexOf(new int[]{24, 32, 40, 48}, prefs.getInt("focus_title_size", tv ? 32 : 26));
            case "Focus border": return prefs.getInt("focus_border", 1);
            case "App entrance": return prefs.getBoolean("app_entrance", true) ? 1 : 0;
            case "Clock size": return indexOf(ThemeStyle.custom(prefs)?DemoGeometry.clockSizes(tv):tv?new int[]{42,52,62}:new int[]{36,44,52},prefs.getInt("clock_size",tv?52:44));
            case "Clock style": return prefs.getInt("clock_style", 0);
            case "Watch face": return prefs.getInt("harmony_clock_face",0);
            case "Metal finish": return prefs.getInt("harmony_clock_finish",0);
            case "Clock accent": return prefs.getInt("harmony_clock_accent",0);
            case "Second hand": return prefs.getBoolean("harmony_clock_sweep",true)?0:1;
            case "Harmony clock size": return prefs.getInt("harmony_clock_size",1);
            case "Daily thoughts": return prefs.getBoolean("harmony_daily_quote",true)?0:1;
            case "Clock font": return prefs.getInt("clock_font", 0);
            case "Home weather widget": return prefs.getBoolean("weather_widget",ThemeStyle.id(prefs)==ThemeStyle.GLASS)?0:1;
            case "Widget animation": return prefs.getBoolean("weather_widget_animation",true)?0:1;
            case "Hijri day adjustment": return Math.max(0,Math.min(4,prefs.getInt("hijri_adjustment",0)+2));
            case "Time format": return prefs.getBoolean("time_24h", false) ? 1 : 0;
            case "Clock alignment": return prefs.getInt("clock_align", 1);
            case "Clock seconds": return prefs.getBoolean("clock_seconds", false) ? 1 : 0;
            case "Clock glow": return prefs.getBoolean("clock_glow", false) ? 1 : 0;
            case "Clock shadow": return prefs.getBoolean("clock_shadow", true) ? 1 : 0;
            case "Hide clock": return prefs.getBoolean("hide_clock", false) ? 1 : 0;
            case "Hide greeting": return prefs.getBoolean("hide_greeting", false) ? 1 : 0;
            case "Theme": return prefs.getInt("theme_name", 0);
            case "Launcher font": return prefs.getInt("app_font", 0);
            case "Glass intensity": return indexOf(new int[]{30, 70, 100}, prefs.getInt("glass_alpha", 70));
            case "Wallpaper source": return glassOs()&&"solid".equals(prefs.getString("wallpaper_mode","solid"))?3:prefs.getInt("wallpaper_source", 0);
            case "Wallpaper slideshow": return indexOf(new int[]{0, 15, 30, 60}, prefs.getInt("wallpaper_slideshow", 0));
            case "Bottom shade": return prefs.getBoolean("bottom_shade", true) ? 1 : 0;
            case "Continue watching": return prefs.getBoolean("continue_watching", true) ? 0 : 1;
            case "Avatar shape": return prefs.getInt("avatar_shape", 0);
            case "Performance": return prefs.getInt("perf_mode", 0);
            case "Home wallpaper": return "custom".equals(prefs.getString("wallpaper_mode", "mountains")) ? 1 : -1;
            case "Temperature unit": return prefs.getBoolean("weather_fahrenheit", false) ? 1 : 0;
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
        if (cachedApps != null) { renderApps(cachedApps); grid.rebind(); } else loadApps();
        // Style preferences can change while app identities/content remain unchanged.
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

    private void weatherMenu(){
        GlassSettings.options(this,"Weather",new String[]{"Home weather widget","Widget animation","Weather city","Temperature unit","Hourly / daily forecast"},
            new String[]{"Saved separately for this theme","Animate only while Home is visible","Shared weather location","Celsius or Fahrenheit","Open weather details"},
            new int[]{R.drawable.ic_brightness,R.drawable.ic_brightness,R.drawable.ic_picture,R.drawable.ic_brightness,R.drawable.ic_brightness},which->{
                if(which==0)chooseSetting("Home weather widget",new String[]{"On","Off"},value->{
                    SharedPreferences.Editor edit=prefs.edit().putBoolean("weather_widget",value==0);if(value==0)edit.putBoolean("apps_only",false);edit.apply();buildHomeSoon();
                });
                else if(which==1)chooseSetting("Widget animation",new String[]{"On","Off"},value->{prefs.edit().putBoolean("weather_widget_animation",value==0).apply();if(weatherWidget!=null)weatherWidget.active(resumed);});
                else if(which==2)WeatherSettings.show(this);
                else if(which==3)chooseSetting("Temperature unit",new String[]{"Celsius","Fahrenheit"},value->prefs.edit().putBoolean("weather_fahrenheit",value==1).apply());
                else showWeatherForecast();
            });
    }
    private void screensaverSettings() {
        chooseSetting("Online landscape screensaver", new String[]{"Preview", "Weather city", "Temperature unit", "Clock position", "Start after idle", "Set automatic screensaver"}, which -> {
            if (which == 0) launch(new Intent(this, LandscapeActivity.class));
            else if (which == 1) WeatherSettings.show(this);
            else if (which == 2) chooseSetting("Temperature unit", new String[]{"Celsius", "Fahrenheit"}, value -> prefs.edit().putBoolean("weather_fahrenheit", value == 1).apply());
            else if (which == 3) chooseSetting("Clock position", new String[]{"Top", "Center", "Bottom"}, value -> prefs.edit().putInt("ss_clock_pos", value).apply());
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

    private ImageView profileAvatarPreview;

    /** Refreshes the avatar preview inside the profile dialog, if open. */
    private void refreshProfileAvatarPreview() {
        if (profileAvatarPreview == null) return;
        profileAvatarPreview.setBackground(avatarSurface(false,96)); profileAvatarPreview.invalidateOutline();
        if (avatarBitmap != null) profileAvatarPreview.setImageBitmap(avatarBitmap);
        else profileAvatarPreview.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_person));
    }

    private void editProfile() {
        if (profile != null && profile.isShowing()) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(8));
        // Avatar preview inside the dialog (user 2026-11-01).
        profileAvatarPreview = new ImageView(this);
        profileAvatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profileAvatarPreview.setBackground(avatarSurface(false,96));
        avatarOutline(profileAvatarPreview);
        profileAvatarPreview.setContentDescription("Profile photo");
        refreshProfileAvatarPreview();
        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setGravity(Gravity.CENTER);
        previewRow.setPadding(0, 0, 0, dp(8));
        previewRow.addView(profileAvatarPreview, new LinearLayout.LayoutParams(dp(96), dp(96)));
        form.addView(previewRow);
        form.addView(button("Photo shape · Round / Rounded square",this::chooseAvatarShape));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Your name");
        name.setText(prefs.getString("name", "Sadique Sadiq"));
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setTextColor(ThemeStyle.foreground(prefs)); name.setHintTextColor(ThemeStyle.muted(prefs)); applyAppFont(name);
        name.setPadding(dp(16), dp(12), dp(16), dp(12)); name.setBackground(toolbarSurface(false));
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
            File photo = profileAsset("personal-avatar.png");
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
        profile = new ThemeDialogBuilder(this).setTitle(prefs.getBoolean("setup", false) ? "Your profile" : "Welcome · set up your Home")
                .setView(form).create();
        profile.setOnDismissListener(d -> { profileAvatarPreview = null; handler.postDelayed(notificationOffer, 1200); });
        profile.show(); GlassSettings.styleDialog(this, profile);
    }

    /**
     * Offers to make Personal Launcher the default Home. The old logic set a permanent
     * "asked once" flag, so after an update/reinstall that reset the default-home setting
     * the prompt never came back. Now the offer reappears whenever we are not the default
     * (with a short snooze after "Later" so it doesn't nag every launch).
     */
    private final Runnable updateCheck = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if ((profile == null || !profile.isShowing()) && !homeOfferShowing && (searchDialog == null || !searchDialog.isShowing()))
                GitHubUpdates.check(PersonalHomeActivity.this, false);
            handler.postDelayed(this, 60_000L);
        }
    };
    private final Runnable notificationOffer = this::maybeOfferNotificationAccess;
    private boolean homeOfferShowing;
    private void maybeOfferNotificationAccess() {
        if (!resumed || homeOfferShowing || (profile != null && profile.isShowing()) || (searchDialog != null && searchDialog.isShowing()) || GlassSettings.showing(this)) return;
        NotificationAccessSetup.offer(this);
    }

    private void maybeOfferDefaultHome() {
        if (isDestroyed() || isFinishing()) return;
        boolean alreadyDefault = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                alreadyDefault = rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
        }
        if (homeOfferShowing || (HomeAutomation.enabled(this) && VolumeAutoStart.paired(this))) return;
        if (alreadyDefault && VolumeAutoStart.paired(this)) return;
        if (!HomeSettingsActivity.available(this) && VolumeAutoStart.paired(this)) return;
        // One-time migration: drop the old permanent flag so installs that lost the
        // default-home status on update get the offer again.
        if (prefs.contains("home_offer_done")) prefs.edit().remove("home_offer_done").apply();
        if (System.currentTimeMillis() < prefs.getLong("home_offer_snooze", 0)) return;
        homeOfferShowing = true;
        new ThemeDialogBuilder(this)
            .setTitle("Use as Home screen?")
            .setMessage("Set Personal Launcher as your default launcher so the Home button opens it.")
            .setPositiveButton("Set as default", (d, w) -> LauncherSetupUi.show(this))
            .setNegativeButton("Later", (d, w) -> prefs.edit().putLong("home_offer_snooze",
                    System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000).apply())
            .setOnDismissListener(d -> { homeOfferShowing = false; handler.postDelayed(notificationOffer, 1200); })
            .show();
    }

    private void refreshProfile() {
        String name = prefs.getString("name", "Sadique Sadiq");
        userName.setText(name.isEmpty() ? "Your name" : name);
        if (avatarBitmap != null) avatar.setImageBitmap(avatarBitmap);
        else avatar.setImageDrawable(TvSymbols.icon(this, R.drawable.ic_person));
    }

    private void loadAvatar() {
        // The bounded local avatar must be ready before Home binds its first frame.
        avatarBitmap = ProfileAssets.load(this, profileAsset("personal-avatar.png"), true);
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
                if (k.equals(ThemePreferences.MIGRATED) || k.equals(NotificationAccessSetup.ASKED) || k.contains("key") || k.contains("secret") || k.contains("token")
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
                        if (k.equals(ThemePreferences.MIGRATED) || k.equals(NotificationAccessSetup.ASKED)) continue;
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
        int importEpoch = profileEpoch;
        File targetAsset = profileAsset(wallpaper ? "personal-wallpaper.jpg" : "personal-avatar.png");
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
                android.util.AtomicFile destination = new android.util.AtomicFile(targetAsset);
                FileOutputStream out = destination.startWrite();
                try {
                    if (!decoded.compress(wallpaper ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, wallpaper ? 85 : 100, out)) throw new java.io.IOException("Cannot save image");
                    destination.finishWrite(out);
                } catch (Exception error) { destination.failWrite(out); throw error; }
                handler.post(() -> {
                    if (isDestroyed() || importEpoch != profileEpoch) return;
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
        if(bluetooth!=null){bluetooth.start();updateBluetooth();}
        if(telemetry!=null)telemetry.start();if(weatherWidget!=null)weatherWidget.active(true);if(analogClock!=null)analogClock.active(true);if(harmonyClock!=null)harmonyClock.active(true);
        GitHubUpdates.resume(this);
        VolumeAutoStart.request(this);
        HomeAutomation.onResume(this);
        handler.removeCallbacks(notificationOffer); handler.postDelayed(notificationOffer, 1200);
        handler.removeCallbacks(updateCheck); handler.postDelayed(updateCheck, 12000);
        armIdleScreensaver();
        handler.removeCallbacks(tick);
        tick.run();
        updateNetwork();
        if (!prefs.getBoolean("apps_only", false)&&(!harmony()||HarmonyVisionLayout.showWatchNextFooter())) continueWatching.start();
        refreshHomeWeather();
        updateOnlineWallpaper();
        armSlideshow();
        handler.removeCallbacks(secondTick);
        if (prefs.getBoolean("clock_seconds", false)) secondTick.run();
        if (pageWidth > 0 && cachedApps == null) loadApps();
        else if (grid != null && !grid.footerFocusedOrPending() && focusedPkg != null && (panel == null || !panel.isShowing())) grid.post(() -> grid.focusKey(focusedPkg));
        if (connectivity != null && !networkRegistered) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback); networkRegistered = true; }
            catch (RuntimeException unavailable) { /* Snapshot above remains available. */ }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (grid != null && grid.getLayoutManager() != null) state.putParcelable("home_grid_state", grid.getLayoutManager().onSaveInstanceState());
    }

    @Override protected void onPause() {
        handler.removeCallbacks(themeMenuRefresh);
        VolumeKeys.stop();
        VolumeSlider.hide(this);
        resumed = false;
        if(weatherWidget!=null)weatherWidget.active(false);if(analogClock!=null)analogClock.active(false);if(harmonyClock!=null)harmonyClock.active(false);
        if(bluetooth!=null)bluetooth.stop();if(telemetry!=null)telemetry.stop();
        if (grid != null && grid.getLayoutManager() != null) gridState = grid.getLayoutManager().onSaveInstanceState();
        if (focusedPkg != null) prefs.edit().putString("home_focus_pkg", focusedPkg).apply();
        if (continueWatching != null) continueWatching.stop();
        handler.removeCallbacks(idleScreensaver);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(secondTick);
        handler.removeCallbacks(weatherRefresh);
        handler.removeCallbacks(notificationOffer);
        handler.removeCallbacks(updateCheck);
        handler.removeCallbacks(slideshowStep);
        if (connectivity != null && networkRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        handler.removeCallbacks(themeMenuRefresh);
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
        else if (MainActivity.ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) handler.post(() -> LauncherPin.require(this, this::launcherSettings));
        else if (MainActivity.ACTION_SHOW_SETTINGS.equals(intent.getAction())) handler.post(this::showSettings);
    }

    @Override public void onBackPressed() {
        launch(new Intent(this, LandscapeActivity.class));
    }

    private void focusFirstApp() {
        if (grid != null) grid.focusKey(focusedPkg);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if(request==GlassBluetooth.PERMISSION){
            bluetooth.requestingPermission=false;
            if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){bluetooth.stop();bluetooth.start();bluetooth.show();}
        }
        if (request == ContinueWatchingView.PERMISSION_REQUEST && continueWatching != null) continueWatching.reload();
    }

    @Override protected void onDestroy() {
        if (liquidBackdrop != null) { liquidBackdrop.close(); liquidBackdrop=null; }
        if (headerBackdrop != null) { headerBackdrop.close(); headerBackdrop=null; }
        unregisterReceiver(appChanges);
        if(weatherWidget!=null)weatherWidget.active(false);if(analogClock!=null)analogClock.active(false);if(harmonyClock!=null)harmonyClock.active(false);if(forecastPanel!=null)forecastPanel.close();
        if(bluetooth!=null)bluetooth.stop();if(telemetry!=null)telemetry.stop();
        if(appLibrary!=null)appLibrary.close();
        if (panel != null) panel.dismiss();
        if (profile != null) profile.dismiss();
        if (searchDialog != null) searchDialog.dismiss();
        handler.removeCallbacksAndMessages(null);
        if (continueWatching != null) continueWatching.stop();
        prefs.unregisterOnSharedPreferenceChangeListener(weatherPreferences);
        weatherWorker.shutdownNow();
        worker.shutdownNow();
        appWorker.shutdownNow();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // UI_HIDDEN is the ordinary trip to another app, not memory pressure.
        // Keep the displayed online photo warm so Home does not flash the built-in
        // background and download the photo again on every return.
        if (MemoryTrimPolicy.shouldRelease(level, resumed)) {
            if (sky != null) sky.releaseTransient();
            trimAppIcons();
        }
    }

    /** Static dark wallpaper and a single focus glow; no continuously running animation. */
    private final class SkyClock extends View {
        private final GlassOsBackdrop glassBackdrop=new GlassOsBackdrop();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // Never share the bitmap paint with translucent sun/moon or focus effects.
        private final Paint wallpaperPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        /** Decoded off the main thread; onDraw draws the gradient alone until it arrives. */
        private Bitmap backdrop;
        private float smoothDay = -1f;

        /** Releases transient bitmaps (online photos, crossfade buffer) under memory pressure. */
        void releaseTransient() {
            handler.removeCallbacks(crossfadeStep);
            crossfade = 1f;
            // Don't recycle backdrop — it's the built-in wallpaper, still used in onDraw.
            // Only clear the reloadable online variants.
            onlineNext = null;
            // Keep onlineWallpaper if it's currently displayed; it will be re-fetched on demand.
            // Null it only if we have a fallback.
            if (backdrop != null) {
                onlineWallpaper = null;
            }

        }
        /**
         * Carry the already-loaded wallpapers into a fresh SkyClock created by
         * buildHome(), so a settings change never flashes the default wallpaper.
         * The old instance is detached: its fields are nulled so a stale posted
         * runnable can never recycle a bitmap this instance is drawing.
         * (User 2026-11-01.)
         */
        void adoptWallpapers(SkyClock old) {
            if (old.backdrop != null) backdrop = old.backdrop;
            if (old.onlineWallpaper != null || old.onlineNext != null) {
                onlineWallpaper = old.onlineWallpaper;
                onlineNext = old.onlineNext;
                onlineIndex = old.onlineIndex;
                onlineImageUrl = old.onlineImageUrl;
                onlineCheckedAt = old.onlineCheckedAt;
                crossfade = old.crossfade;
            }
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
        float clockX=-1, clockY = -1, focusX, focusY, focusRadius;
        /** Analog face radius in px, set from clock_size pref. */
        float clockRadius = -1;
        /** Analog clock mode: 0=digital, 1=analog. Set from prefs. */
        int clockStyle;
        /** Online wallpaper with crossfade. */
        private Bitmap onlineWallpaper, onlineNext;
        private float crossfade = 1f;
        private int onlineIndex = -1;
        private String onlineImageUrl = "";
        private long onlineCheckedAt;
        private boolean onlineLoading;
        /** Favorite wallpaper (saved by user). */
        private Bitmap favoriteWallpaper;
        SkyClock() {
            super(PersonalHomeActivity.this);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            // A restored photo must exist before this View's first frame, including process restarts.
            if (sky == null || (sky.onlineWallpaper == null && sky.onlineNext == null)) restoreBingSnapshot();
            worker.execute(() -> {
                Bitmap bmp = prefs.getInt("wallpaper_source", 0) == 1 ? null
                        : BitmapFactory.decodeResource(getResources(), R.drawable.personal_wallpaper);
                Bitmap fav = null;
                try {
                    java.io.File f = profileAsset("favorite_wallpaper.jpg");
                    if (f.exists()) fav = BitmapFactory.decodeFile(f.getAbsolutePath());
                } catch (Exception ignored) {}
                Bitmap bg = bmp, fg = fav;
                handler.post(() -> { backdrop = bg; favoriteWallpaper = fg; invalidate(); adaptTextColors(); });
            });
        }

        private java.io.File bingSnapshotFile() { return new java.io.File(getFilesDir(), "home_bing_snapshot.bin"); }

        private Bitmap decodeBingPhoto(byte[] bytes) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) return null;
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                options.inSampleSize = WallpaperSize.sampleSize(options.outWidth, options.outHeight, metrics.widthPixels, metrics.heightPixels);
                if (!WallpaperSize.accepted(options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize)) return null;
                int[] scale = WallpaperSize.coverScale(options.outWidth / options.inSampleSize, options.outHeight / options.inSampleSize, metrics.widthPixels, metrics.heightPixels);
                if (scale != null) { options.inScaled = true; options.inDensity = scale[0]; options.inTargetDensity = scale[1]; }
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            } catch (RuntimeException | OutOfMemoryError unavailable) { return null; }
        }

        private void restoreBingSnapshot() {
            if (prefs.getInt("wallpaper_source", 0) != 1 || onlineWallpaper != null || onlineNext != null) return;
            HomeWallpaperCache.Entry saved = HomeWallpaperCache.read(bingSnapshotFile());
            if (saved == null) return;
            Bitmap restored = decodeBingPhoto(saved.image);
            if (restored == null) return;
            onlineWallpaper = restored; onlineImageUrl = saved.url; onlineCheckedAt = saved.checkedAt;
            onlineIndex = 0; crossfade = 1f;
        }

        void reloadFavorite() {
            worker.execute(() -> {
                Bitmap fav = null;
                try {
                    java.io.File f = profileAsset("favorite_wallpaper.jpg");
                    if (f.exists()) fav = BitmapFactory.decodeFile(f.getAbsolutePath());
                } catch (Exception ignored) {}
                Bitmap loaded = fav;
                handler.post(() -> { favoriteWallpaper = loaded; invalidate(); adaptTextColors(); });
            });
        }

        /** The wallpaper bitmap currently drawn (or null if none yet). */
        Bitmap currentPicture() {
            String mode = prefs.getString("wallpaper_mode", "mountains");
            int source = prefs.getInt("wallpaper_source", 0);
            Bitmap picture = mode.equals("custom") && customWallpaper != null ? customWallpaper : backdrop;
            if (source == 1) picture = onlineWallpaper != null ? onlineWallpaper : onlineNext;
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
                    java.io.File out = profileAsset("favorite_wallpaper.jpg");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                        current.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    }
                    handler.post(() -> android.widget.Toast.makeText(PersonalHomeActivity.this,
                            "Saved as favorite", android.widget.Toast.LENGTH_SHORT).show());
                } catch (Exception ignored) {}
            });
        }

        /** Keep Bing's daily image; periodically check metadata so a new day works while Home stays open. */
        void loadOnlinePhoto(int index) {
            if (onlineLoading) return;
            restoreBingSnapshot();
            if (index == onlineIndex && (onlineWallpaper != null || onlineNext != null)
                    && !LandscapeModel.bingCheckDue(onlineCheckedAt, System.currentTimeMillis())) return;
            onlineLoading = true;
            // Bing daily: use the index directly (0=today). No time-based seed —
            // the launcher must always show today's wallpaper. (User 2026-10-31.)
            final int bingIdx = index;
            final String previousUrl = onlineImageUrl;
            final boolean hasCurrentPhoto = onlineWallpaper != null || onlineNext != null;
            worker.execute(() -> {
                Bitmap bmp = null;
                String fetchedUrl = "";
                boolean checked = false;
                try {
                    // Retire the old 6-photo disk cache: it is no longer populated.
                    java.io.File[] stale = new java.io.File(getCacheDir(), "screensaver_photos").listFiles();
                    if (stale != null) for (java.io.File f : stale) f.delete();
                    // Bing daily wallpaper: API returns JSON, parse to get image URL.
                    String apiUrl = LandscapeModel.photoUrl(bingIdx);
                    byte[] jsonBytes = OnlineContent.get(apiUrl, 64 * 1024);
                    String json = new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8);
                    String imageUrl = LandscapeModel.bingImageUrl(json);
                    fetchedUrl = imageUrl;
                    checked = true;
                    if (!imageUrl.equals(previousUrl) || !hasCurrentPhoto) {
                        byte[] bytes = OnlineContent.get(imageUrl, 6 * 1024 * 1024);
                        bmp = decodeBingPhoto(bytes);
                        if (bmp != null) {
                            try { HomeWallpaperCache.write(bingSnapshotFile(), imageUrl, System.currentTimeMillis(), bytes); }
                            catch (java.io.IOException unavailable) { android.util.Log.w("PersonalWallpaper", "Could not persist the last Home photo", unavailable); }
                        }
                    }
                } catch (Exception ignored) {}
                Bitmap loaded = bmp;
                String newUrl = fetchedUrl;
                boolean metadataChecked = checked;
                handler.post(() -> {
                    onlineLoading = false;
                    if (sky != this) { if (loaded != null) loaded.recycle(); return; }
                    if (metadataChecked && (loaded != null || newUrl.equals(onlineImageUrl))) {
                        onlineCheckedAt = System.currentTimeMillis();
                        onlineIndex = index;
                    }
                    if (loaded == null) return;
                    onlineImageUrl = newUrl;
                    if (onlineWallpaper == null) {
                        onlineWallpaper = loaded; crossfade = 1f;
                        invalidate(); adaptTextColors(); return;
                    }
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
                if (onlineNext == null) { crossfade = 1f; return; }
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
            if(glassOs() && "solid".equals(prefs.getString("wallpaper_mode","solid"))) {
                glassBackdrop.draw(canvas,getWidth(),getHeight(),glassAccent,Math.max(0,Math.min(100,prefs.getInt("ambient_strength",100))));
                return;
            }
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
            if (onlineSource) picture = onlineWallpaper != null ? onlineWallpaper : onlineNext;
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
                int glow = (focusAccent & 0x00ffffff) | (ThemeStyle.light(prefs)?0x33000000:0x80000000);
                paint.setShader(new RadialGradient(focusX, focusY, focusRadius, glow, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(focusX, focusY, focusRadius, paint); paint.setShader(null);
            }
            float cy = clockY >= 0 ? clockY : dp(150);
            // Clock glow: soft halo behind the clock.
            if (prefs.getBoolean("clock_glow", false)) {
                int accent = accentColor();
                paint.setShader(new RadialGradient(clockX>=0?clockX:getWidth()/2f, cy, dp(160),
                        (accent & 0x00ffffff) | 0x60000000, Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(clockX>=0?clockX:getWidth()/2f, cy, dp(160), paint); paint.setShader(null);
            }
            // Analog clock face.
            if (clockStyle == 1) {
                float radius = clockRadius > 0 ? clockRadius : Math.min(getWidth(), getHeight()) / 7f;
                float cx=Math.max(radius,Math.min(getWidth()-radius,clockX>=0?clockX:getWidth()/2f));
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ThemeStyle.light(prefs)?0xccfff6e9:0x99000000);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(ThemeStyle.foreground(prefs));
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
        if(ThemeStyle.custom(prefs)) return null; // The shared path surface owns the matching edge.
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT); border.setCornerRadius(tileRadius());
        int thickness = focused ? new int[]{2, 3, 5}[Math.max(0, Math.min(2, prefs.getInt("focus_border", 1)))] : 1;
        border.setStroke(dp(thickness), tvGlass() ? (focused ? 0xfff4f8ff : TvGlassStyle.edge(prefs)) : (focused ? accent : 0x4028435e));
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




