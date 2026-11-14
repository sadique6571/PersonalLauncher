package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The activity preview and Android Dream use the same landscape screen. */
public final class LandscapeView extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final ConnectivityManager connectivity;
    private final boolean reducedMotion;
    private ImageView photo, previousPhoto;
    private int photoEffect;
    private int startupPhotoIndex;
    private final TextView clock, date, wifi, weather, city, status, details;
    private ExecutorService worker;
    private ExecutorService weatherWorker;
    private boolean running, registered, photoBusy, weatherBusy, hasPhoto, networkOnline;
    private int generation;
    private static final int[] OFFLINE_RESOURCES = {
            R.drawable.offline_autumn, R.drawable.offline_mountain_road, R.drawable.offline_mississippi
    };
    private static final String[] OFFLINE_IDS = {
            "publicdomainpictures:200000/velka/fall-by-the-lake-14767797082J2.jpg",
            "publicdomainpictures:470000/velka/landschap-bergweg-haarspeldbocht-1665124155qIR.jpg",
            "nps:29fc4bc2-b103-4b1b-939f-19d4b91ce209"
    };
    private static final String[] OFFLINE_SUBJECTS = {
            "Autumn by the lake", "Winding mountain road", "Mississippi River · U.S. National Park Service"
    };


    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { handler.post(() -> networkChanged()); }
        @Override public void onLost(Network network) { handler.post(() -> networkChanged()); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { handler.post(() -> networkChanged()); }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            updateClock(); updateNetwork();
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };
    private final Runnable nextPhoto = this::requestPhoto;
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestWeather();
        }
    };

    public LandscapeView(Context context) {
        super(context);
        prefs = ThemePreferences.get(context);
        connectivity = context.getSystemService(ConnectivityManager.class);
        setBackgroundColor(0xff041225);
        reducedMotion = Config.load(context).reducedMotion;
        FrameLayout photos = new FrameLayout(context);
        photo = photoLayer(context, photos);
        previousPhoto = photoLayer(context, photos);
        addView(photos, new LayoutParams(-1, -1));
        View shade = new View(context);
        shade.setBackgroundColor(0x24000000);
        addView(shade, new LayoutParams(-1, -1));
        LinearLayout timeBlock = new LinearLayout(context);
        timeBlock.setOrientation(LinearLayout.VERTICAL);
        clock = label(getResources().getConfiguration().screenHeightDp >= 650 ? 52 : 44);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setGravity(Gravity.LEFT);
        clock.setSingleLine(true);
        timeBlock.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        date = label(15);
        date.setGravity(Gravity.LEFT);
        timeBlock.addView(date);
        LayoutParams clockPosition = new LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        clockPosition.setMargins(dp(24), dp(18), 0, 0);
        addView(timeBlock, clockPosition);
        wifi = label(14);
        wifi.setPadding(dp(10), dp(8), dp(10), dp(8));
        LayoutParams networkPosition = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        networkPosition.setMargins(dp(20), dp(20), dp(20), 0);
        addView(wifi, networkPosition);
        LinearLayout conditions = new LinearLayout(context);
        conditions.setOrientation(LinearLayout.VERTICAL);
        city = label(16);
        city.setGravity(Gravity.START);
        weather = label(24);
        weather.setGravity(Gravity.START);
        weather.setMaxLines(2);
        weather.setText("Unavailable");
        conditions.addView(city);
        conditions.addView(weather);
        int availableWidth = Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(48));
        int weatherWidth = Math.min(dp(320), Math.round(availableWidth * .42f));
        LayoutParams weatherPosition = new LayoutParams(weatherWidth, -2, Gravity.BOTTOM | Gravity.START);
        weatherPosition.setMargins(dp(24), 0, 0, dp(24));
        addView(conditions, weatherPosition);
        status = label(12);
        status.setText("Connecting to online photos…");
        status.setGravity(Gravity.END);
        LayoutParams statusPosition = new LayoutParams(weatherWidth, -2, Gravity.TOP | Gravity.END);
        statusPosition.setMargins(0, dp(60), dp(24), 0);
        addView(status, statusPosition);
        // Wallpaper details caption at the bottom (user 2026-11-01).
        details = label(13);
        details.setGravity(Gravity.END); details.setMaxLines(2);
        LayoutParams detailsPosition = new LayoutParams(availableWidth - weatherWidth - dp(24), -2, Gravity.BOTTOM | Gravity.END);
        detailsPosition.setMargins(0, 0, dp(24), dp(24));
        addView(details, detailsPosition);
    }

    private static ImageView photoLayer(Context context, FrameLayout parent) {
        ImageView layer = new ImageView(context);
        layer.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(layer, new LayoutParams(-1, -1));
        return layer;
    }

    private void clearPhotos() {
        photo.animate().cancel();
        previousPhoto.animate().cancel();
        photo.setImageDrawable(null);
        previousPhoto.setImageDrawable(null);
        hasPhoto = false;
    }

    private void showPhoto(Bitmap image, String caption) {
        photo.animate().cancel();
        previousPhoto.animate().cancel();
        ImageView outgoing = photo;
        photo = previousPhoto;
        previousPhoto = outgoing;
        ImageView incoming = photo;
        incoming.bringToFront();
        incoming.setImageBitmap(image);
        incoming.setAlpha(0f);
        incoming.setTranslationX(0f);
        incoming.setTranslationY(0f);
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        if (hasPhoto && !reducedMotion) {
            float horizontal = getWidth() * .085f;
            float vertical = getHeight() * .10f;
            float scale = 1f;
            switch (photoEffect++ % 7) {
                case 1: outgoing.animate().scaleX(.87f).scaleY(.87f).setDuration(620).start(); break;
                case 2: scale = .87f; break;
                case 3: scale = .80f; incoming.setTranslationX(horizontal); break;
                case 4: scale = .80f; incoming.setTranslationX(-horizontal); break;
                case 5: scale = .76f; incoming.setTranslationY(vertical); break;
                case 6: scale = .76f; incoming.setTranslationY(-vertical); break;
                default: break;                          // Crossfade.
            }
            incoming.setScaleX(scale);
            incoming.setScaleY(scale);
        }
        details.setText(caption);
        incoming.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(reducedMotion ? Motion.REDUCED_MS : 620)
                .withEndAction(() -> outgoing.setImageDrawable(null)).start();
        hasPhoto = true;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size) {
        TextView label = new TextView(getContext());
        label.setTextColor(Color.WHITE);
        label.setTextSize(size);
        if (TvGlassStyle.enabled(prefs)) label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(3, 0, 1, Color.BLACK);
        return label;
    }

    public void start() {
        if (running) return;
        running = true;
        generation++;
        photoBusy = weatherBusy = false;
        worker = Executors.newSingleThreadExecutor();
        ScreensaverPrefetch.schedule(getContext());
        weatherWorker = Executors.newSingleThreadExecutor();
        networkOnline = online();
        city.setText(prefs.getString("weather_city", LandscapeModel.CITY));
        if (connectivity != null) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback); registered = true; }
            catch (RuntimeException unavailable) { registered = false; }
        }
        showStartupPhoto();
        minuteTick.run(); requestPhoto(); weatherTick.run();
    }

    public void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (registered && connectivity != null) {
            try { connectivity.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) { }
            registered = false;
        }
        if (worker != null) worker.shutdownNow();
        if (weatherWorker != null) weatherWorker.shutdownNow();
        clearPhotos();
        details.setText("");
    }

    private NetworkCapabilities capabilities() {
        Network active = connectivity == null ? null : connectivity.getActiveNetwork();
        return active == null ? null : connectivity.getNetworkCapabilities(active);
    }
    private boolean online() {
        NetworkCapabilities caps = capabilities();
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    private void updateNetwork() {
        NetworkCapabilities caps = capabilities();
        boolean connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        int color = connected ? 0xff59e493 : 0xffff6c78;
        int image = caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? R.drawable.ic_wifi : R.drawable.ic_internet;
        android.graphics.drawable.Drawable icon = getContext().getDrawable(image).mutate();
        icon.setTint(color);
        icon.setBounds(0, 0, dp(24), dp(24));
        wifi.setCompoundDrawables(icon, null, null, null);
        wifi.setCompoundDrawablePadding(dp(8));
        wifi.setText(connected ? "Online" : "No internet");
        wifi.setTextColor(color);
        if (!connected) weather.setText("Unavailable");
    }

    private void networkChanged() {
        if (!running) return;
        updateNetwork();
        boolean connected = online();
        boolean recovered = connected && !networkOnline;
        networkOnline = connected;
        if (!hasPhoto || recovered) requestPhoto();
        // Weather recovery must not depend on whether a landscape has already loaded.
        if (recovered) requestWeather();
    }
    private void updateClock() {
        Calendar now = Calendar.getInstance();
        String value = PersonalHomeModel.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        android.text.SpannableString clockText = new android.text.SpannableString(value);
        clockText.setSpan(new android.text.style.RelativeSizeSpan(.38f), value.length() - 2, value.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setText(clockText);
        date.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now.getTime()));
    }

    /** Fixed clock/date style (2026-11-01): white with black outline + drop shadow,
     *  readable on any photo. Replaces the old brightness-adaptive colors. */
    private void adaptClockColors() {
        clock.setTextColor(0xFFFFFFFF);
        clock.setShadowLayer(6, 0, 3, 0xE6000000);
        date.setTextColor(0xFFFFFFFF);
        date.setShadowLayer(5, 0, 3, 0xE6000000);
    }

    private void showStartupPhoto() {
        // Use a bundled photo as a placeholder while the worker checks the history and feeds.
        // It is not added to the unseen queue or used to reset photo history.
        int index = new java.util.Random().nextInt(OFFLINE_RESOURCES.length);
        startupPhotoIndex = index;
        photo.setImageResource(OFFLINE_RESOURCES[index]);
        photo.setAlpha(1f);
        photo.setTranslationX(0f);
        photo.setTranslationY(0f);
        photo.setScaleX(1f);
        photo.setScaleY(1f);
        hasPhoto = true;
        details.setText(OFFLINE_SUBJECTS[index]);
        status.setText("Looking for a new photo…");
    }

    private void requestPhoto() {
        if (!running || photoBusy) return;
        handler.removeCallbacks(nextPhoto);
        photoBusy = true;
        int request = generation;
        int placeholder = startupPhotoIndex;
        worker.execute(() -> {
            Bitmap result = null;
            String subject = "";
            try (LandscapeHistory history = new LandscapeHistory(getContext())) {
                LandscapeHistory.SavedPhoto selected;
                for (int i = 0; result == null && i < LandscapeHistory.RESERVE_SIZE && !Thread.currentThread().isInterrupted(); i++) {
                    selected = history.takeOffline();
                    if (selected == null) break;
                    result = rememberPhoto(history, selected);
                    if (result != null) subject = selected.subject;
                }
                if (result == null) {
                    for (int i = 0; i < OFFLINE_RESOURCES.length && result == null && !Thread.currentThread().isInterrupted(); i++) {
                        if (i == placeholder || history.contains(OFFLINE_IDS[i])) continue;
                        try (InputStream in = getResources().openRawResource(OFFLINE_RESOURCES[i])) {
                            selected = new LandscapeHistory.SavedPhoto(OFFLINE_IDS[i], OFFLINE_SUBJECTS[i],
                                    OnlineContent.readBounded(in, LandscapeHistory.RESERVE_PHOTO_LIMIT));
                            result = rememberPhoto(history, selected);
                            if (result != null) subject = selected.subject;
                        }
                    }
                }
            } catch (Exception unavailable) {
                android.util.Log.w("PersonalLandscape", "Photo history unavailable", unavailable);
            }
            Bitmap image = result;
            String caption = subject;
            handler.post(() -> {
                if (!running || request != generation) { if (image != null) image.recycle(); return; }
                photoBusy = false;
                ScreensaverPrefetch.schedule(getContext());
                if (image != null) {
                    startupPhotoIndex = -1;
                    showPhoto(image, caption);
                    status.setText("");
                    adaptClockColors();
                    handler.postDelayed(nextPhoto, 30_000L);
                } else {
                    boolean connected = online();
                    status.setText(connected ? "Looking for a new photo…" : "Offline");
                    handler.postDelayed(nextPhoto, connected ? 5_000L : 30_000L);
                }
            });
        });
    }

    private static Bitmap rememberPhoto(LandscapeHistory history, LandscapeHistory.SavedPhoto photo) {
        if (photo == null || history.contains(photo.id) || Thread.currentThread().isInterrupted()) return null;
        Bitmap image = ScreensaverPrefetch.decode(photo.bytes);
        if (image == null) return null;
        boolean accepted = false;
        try {
            accepted = history.remember(photo.id, photo.bytes, ScreensaverPrefetch.fingerprint(image));
            return accepted ? image : null;
        } finally {
            if (!accepted) image.recycle();
        }
    }

    private void requestWeather() {
        if (!running || weatherBusy) return;
        handler.removeCallbacks(weatherTick);
        if (!online()) { weather.setText("Unavailable"); handler.postDelayed(weatherTick, 60_000L); return; }
        weatherBusy = true;
        city.setText(prefs.getString("weather_city", LandscapeModel.CITY));
        int request = generation;
        boolean fahrenheit = prefs.getBoolean("weather_fahrenheit", false);
        // Photo downloads cannot delay weather refreshes on a slow connection.
        weatherWorker.execute(() -> {
            String result = null;
            try {
                double lat = Double.parseDouble(prefs.getString("weather_lat", Double.toString(LandscapeModel.LATITUDE)));
                double lon = Double.parseDouble(prefs.getString("weather_lon", Double.toString(LandscapeModel.LONGITUDE)));
                result = WeatherClient.fetch(lat, lon, fahrenheit);
            } catch (Exception unavailable) {
                android.util.Log.w("PersonalWeather", "Screensaver weather refresh failed", unavailable);
            }
            String value = result;
            handler.post(() -> {
                if (!running || request != generation) return;
                weatherBusy = false;
                boolean success = value != null && online();
                if (success) {
                    weather.setText(value);
                } else {
                    weather.setText("Unavailable");
                }
                handler.removeCallbacks(weatherTick);
                handler.postDelayed(weatherTick, success ? 15 * 60_000L : 60_000L);
            });
        });
    }
}
