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
    private final boolean reducedMotion;
    private ImageView photo, previousPhoto;
    private int photoEffect;
    private final TextView clock, date, wifi, weather, city, status, details;
    private ExecutorService worker;
    private ExecutorService weatherWorker;
    private boolean running, registered, photoBusy, weatherBusy, hasPhoto, networkOnline;
    private int generation;
    private LandscapeFeed photoFeed;

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
        photoFeed = new LandscapeFeed();
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
        if (!connected) {
            clearPhotos();
            details.setText("");
            status.setText("Offline · photos need internet");
            weather.setText("Unavailable");
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
        LandscapeFeed feed = photoFeed;
        worker.execute(() -> {
            Bitmap result = null;
            String subject = "";
            try (LandscapeHistory history = new LandscapeHistory(getContext())) {
                for (int attempt = 0; attempt < 6 && result == null && !Thread.currentThread().isInterrupted(); attempt++) {
                    LandscapeFeed.Scene scene = feed.next(history::contains);
                    if (scene == null) break;
                    try {
                        byte[] bytes = OnlineContent.get(scene.url, 6 * 1024 * 1024, 5000, 8000);
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        if (!LandscapeFeed.usableDimensions(bounds.outWidth, bounds.outHeight, scene.reviewed)) continue;
                        bounds.inJustDecodeBounds = false; bounds.inSampleSize = 1;
                        while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > 1920) bounds.inSampleSize *= 2;
                        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                        if (decoded == null) continue;
                        if ((!scene.reviewed && !clearLandscape(decoded)) || !history.remember(scene.id, bytes, visualFingerprint(decoded))) {
                            decoded.recycle();
                            continue;
                        }
                        result = decoded;
                        subject = scene.subject;
                    } catch (Exception unavailable) {
                        android.util.Log.w("PersonalLandscape", "Scenery photo unavailable", unavailable);
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
                if (image != null && online()) {
                    showPhoto(image, caption);
                    status.setText("");
                    // Wallpaper details caption at the bottom (user 2026-11-01).
                    adaptClockColors();
                } else {
                    if (image != null) image.recycle();
                    clearPhotos();
                    details.setText("");
                    status.setText(online() ? "New photo unavailable · retrying automatically" : "Offline · photos unavailable");
                    handler.removeCallbacks(photoRetry);
                    handler.postDelayed(photoRetry, 5_000);
                }
            });
        });
    }

    private static long[] visualFingerprint(Bitmap image) {
        int width = Math.min(image.getWidth(), Math.round(image.getHeight() * 16f / 9));
        int height = Math.min(image.getHeight(), Math.round(image.getWidth() * 9f / 16));
        int left = (image.getWidth() - width) / 2;
        int top = (image.getHeight() - height) / 2;
        int size = LandscapeFingerprint.SIZE;
        Bitmap sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            android.graphics.Canvas canvas = new android.graphics.Canvas(sample);
            canvas.drawBitmap(image, new android.graphics.Rect(left, top, left + width, top + height),
                    new android.graphics.Rect(0, 0, size, size), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            int[] pixels = new int[size * size];
            sample.getPixels(pixels, 0, size, 0, 0, size, size);
            return LandscapeFingerprint.of(pixels);
        } finally {
            sample.recycle();
        }
    }

    /** Supplement source filters with a lightweight on-device face check for live landscapes. */
    private static boolean clearLandscape(Bitmap image) {
        int width = 480;
        int height = Math.max(1, Math.round((float) width * image.getHeight() / image.getWidth()));
        Bitmap sample = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        try {
            android.graphics.Canvas canvas = new android.graphics.Canvas(sample);
            canvas.drawBitmap(image, null, new android.graphics.Rect(0, 0, width, height),
                    new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            android.media.FaceDetector detector = new android.media.FaceDetector(width, height, 1);
            return detector.findFaces(sample, new android.media.FaceDetector.Face[1]) == 0;
        } finally {
            sample.recycle();
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
