package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The activity preview and Android Dream use the same online-only landscape screen. */
public final class LandscapeView extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final ConnectivityManager connectivity;
    private final ImageView photo;
    private final TextView clock, date, wifi, weather, city, status, details, weatherStatus;
    private ExecutorService worker;
    private ExecutorService weatherWorker;
    private boolean running, registered, photoBusy, weatherBusy, hasPhoto, networkOnline;
    private String lastWeather;
    private int generation;
    private final java.util.Random photoRandom = new java.util.Random();

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
    private final Runnable photoRetry = () -> { if (running && !hasPhoto) requestPhoto(); };
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestPhoto();
            handler.postDelayed(this, 30_000L);
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestWeather();
        }
    };

    public LandscapeView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("personal_home", Context.MODE_PRIVATE);
        connectivity = context.getSystemService(ConnectivityManager.class);
        setBackgroundColor(0xff041225);
        photo = new ImageView(context);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(photo, new LayoutParams(-1, -1));
        View shade = new View(context);
        shade.setBackgroundColor(0x60000000);
        addView(shade, new LayoutParams(-1, -1));
        LinearLayout center = new LinearLayout(context);
        center.setGravity(Gravity.CENTER);
        center.setOrientation(LinearLayout.VERTICAL);
        clock = label(88);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setSingleLine(true);
        // Bigger screensaver clock (user 2026-11-01).
        clock.setAutoSizeTextTypeUniformWithConfiguration(44, 132, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        center.addView(clock, new LinearLayout.LayoutParams(-1, dp(168)));
        date = label(20);
        center.addView(date);
        int ssPos = Math.max(0, Math.min(2, prefs.getInt("ss_clock_pos", 1)));
        LayoutParams centered = new LayoutParams(-1, -2,
                new int[]{Gravity.TOP | Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL}[ssPos]);
        centered.setMargins(dp(24), ssPos == 0 ? dp(60) : 0, dp(24), ssPos == 2 ? dp(120) : 0);
        addView(center, centered);
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
        weather.setText("Loading weather…");
        conditions.addView(city);
        conditions.addView(weather);
        weatherStatus = label(12); weatherStatus.setGravity(Gravity.START);
        conditions.addView(weatherStatus);
        int availableWidth = Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(48));
        int weatherWidth = Math.min(dp(320), Math.round(availableWidth * .42f));
        LayoutParams weatherPosition = new LayoutParams(weatherWidth, -2, Gravity.BOTTOM | Gravity.START);
        weatherPosition.setMargins(dp(24), 0, 0, dp(24));
        addView(conditions, weatherPosition);
        status = label(12);
        status.setText("Connecting to online landscapes…");
        status.setGravity(Gravity.START);
        LayoutParams statusPosition = new LayoutParams(weatherWidth, -2, Gravity.TOP | Gravity.START);
        statusPosition.setMargins(dp(24), dp(24), 0, 0);
        addView(status, statusPosition);
        // Wallpaper details caption at the bottom (user 2026-11-01).
        details = label(13);
        details.setGravity(Gravity.END); details.setMaxLines(2);
        LayoutParams detailsPosition = new LayoutParams(availableWidth - weatherWidth - dp(24), -2, Gravity.BOTTOM | Gravity.END);
        detailsPosition.setMargins(0, 0, dp(24), dp(24));
        addView(details, detailsPosition);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size) {
        TextView label = new TextView(getContext());
        label.setTextColor(Color.WHITE);
        label.setTextSize(size);
        label.setGravity(Gravity.CENTER);
        label.setShadowLayer(3, 0, 1, Color.BLACK);
        return label;
    }

    public void start() {
        if (running) return;
        running = true;
        generation++;
        photoBusy = weatherBusy = false;
        worker = Executors.newFixedThreadPool(2);
        weatherWorker = Executors.newSingleThreadExecutor();
        networkOnline = false;
        city.setText(prefs.getString("weather_city", LandscapeModel.CITY));
        if (connectivity != null) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback); registered = true; }
            catch (RuntimeException unavailable) { registered = false; }
        }
        minuteTick.run(); rotate.run(); weatherTick.run();
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
        photo.animate().cancel();
        photo.setImageDrawable(null);
        details.setText("");
        hasPhoto = false;
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
        if (!connected) {
            photo.animate().cancel();
            photo.setImageDrawable(null); hasPhoto = false;
            details.setText("");
            status.setText("Offline · landscapes need internet");
            if (lastWeather == null) weather.setText("Weather unavailable · no internet");
            weatherStatus.setText("Offline · waiting to refresh");
        }
    }

    private void networkChanged() {
        if (!running) return;
        updateNetwork();
        boolean connected = online();
        boolean recovered = connected && !networkOnline;
        networkOnline = connected;
        if (connected && !hasPhoto) requestPhoto();
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

    private void requestPhoto() {
        if (!running || photoBusy || !online()) return;
        photoBusy = true;
        int request = generation;
        int seed = photoRandom.nextInt();
        worker.execute(() -> {
            Bitmap result = null;
            String caption = "";
            int downloads = 0;
            try (LandscapeHistory history = new LandscapeHistory(getContext())) {
                for (int attempt = 0; attempt < 3 && result == null && downloads < 3 && !Thread.currentThread().isInterrupted(); attempt++) {
                    try {
                        String body = new String(OnlineContent.get(LandscapeFeed.queryUrl(seed + attempt, System.nanoTime()), 256 * 1024), java.nio.charset.StandardCharsets.UTF_8);
                        for (LandscapeFeed.Scene scene : LandscapeFeed.scenes(body, history::contains)) {
                            if (downloads >= 3 || Thread.currentThread().isInterrupted()) break;
                            downloads++;
                            try {
                                byte[] bytes = OnlineContent.get(scene.url, 6 * 1024 * 1024);
                                BitmapFactory.Options bounds = new BitmapFactory.Options();
                                bounds.inJustDecodeBounds = true;
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                                if (!LandscapeModel.isLandscape(bounds.outWidth, bounds.outHeight)) continue;
                                bounds.inJustDecodeBounds = false; bounds.inSampleSize = 1;
                                while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > 1920) bounds.inSampleSize *= 2;
                                Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                                if (decoded == null) continue;
                                if (!history.remember(scene.id, bytes)) { decoded.recycle(); continue; }
                                result = decoded; caption = scene.caption; break;
                            } catch (Exception unavailable) { /* Try another unseen source image. */ }
                        }
                    } catch (Exception unavailable) { /* Try a different nature query. */ }
                }
            } catch (Exception unavailable) { /* Never show an untracked repeat if history is unavailable. */ }
            Bitmap image = result;
            String photoCaption = caption;
            handler.post(() -> {
                if (!running || request != generation) { if (image != null) image.recycle(); return; }
                photoBusy = false;
                if (image != null && online()) {
                    // Fade through: old photo out, new photo in.
                    photo.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                        if (!running || request != generation || !online()) { image.recycle(); return; }
                        photo.setImageBitmap(image);
                        details.setText(photoCaption);
                        photo.animate().alpha(1f).setDuration(500).start();
                    }).start();
                    hasPhoto = true;
                    status.setText("");
                    // Wallpaper details caption at the bottom (user 2026-11-01).
                    adaptClockColors();
                } else {
                    if (image != null) image.recycle();
                    status.setText("New landscape unavailable · retrying automatically");
                    handler.removeCallbacks(photoRetry);
                    handler.postDelayed(photoRetry, 15_000);
                }
            });
        });
    }

    private void requestWeather() {
        if (!running || weatherBusy) return;
        handler.removeCallbacks(weatherTick);
        if (!online()) { handler.postDelayed(weatherTick, 60_000L); return; }
        weatherBusy = true;
        weatherStatus.setText("Updating weather…");
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
                    lastWeather = value;
                    weather.setText(value);
                    weatherStatus.setText("Updated " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date()));
                } else {
                    if (lastWeather == null) weather.setText("Weather unavailable");
                    weatherStatus.setText(online() ? "Update failed · retrying in 1 minute" : "Offline · waiting to refresh");
                }
                handler.removeCallbacks(weatherTick);
                handler.postDelayed(weatherTick, success ? 15 * 60_000L : 60_000L);
            });
        });
    }
}
