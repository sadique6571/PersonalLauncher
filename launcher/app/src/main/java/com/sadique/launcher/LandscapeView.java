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
    private final TextView clock, date, wifi, weather, city, status;
    private ExecutorService worker;
    private boolean running, registered, photoBusy, weatherBusy, hasPhoto;
    private int displayedPhoto = -1;
    private int generation, photoIndex = (int) (System.currentTimeMillis() / 60_000);

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
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestPhoto();
            handler.postDelayed(this, Math.max(30, Math.min(300, prefs.getInt("photo_seconds", 30))) * 1000L);
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            requestWeather();
            handler.postDelayed(this, 15 * 60_000L);
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
        clock.setAutoSizeTextTypeUniformWithConfiguration(32, 100, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
        center.addView(clock, new LinearLayout.LayoutParams(-1, dp(126)));
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
        LayoutParams weatherPosition = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START);
        weatherPosition.setMargins(dp(24), 0, dp(24), dp(44));
        addView(conditions, weatherPosition);
        status = label(12);
        status.setText("Connecting to online landscapes…");
        LayoutParams statusPosition = new LayoutParams(-1, -2, Gravity.BOTTOM);
        statusPosition.setMargins(dp(24), 0, dp(24), dp(16));
        addView(status, statusPosition);
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
        photo.setImageDrawable(null);
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
            photo.setImageDrawable(null); hasPhoto = false;
            status.setText("Offline · landscapes need internet");
            weather.setText("Weather unavailable · no internet");
        }
    }

    private void networkChanged() {
        if (!running) return;
        updateNetwork();
        if (online() && !hasPhoto) { requestPhoto(); requestWeather(); }
    }
    private void updateClock() {
        Calendar now = Calendar.getInstance();
        String value = PersonalHomeModel.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        android.text.SpannableString clockText = new android.text.SpannableString(value);
        clockText.setSpan(new android.text.style.RelativeSizeSpan(.38f), value.length() - 2, value.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setText(clockText);
        date.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now.getTime()));
    }

    /** Fresh photo per request: picsum serves a different real photo per unique seed,
     *  so nothing is disk-cached — every rotation downloads a new photo (still bounded
     *  to 6 MB with timeouts, see OnlineContent). */
    private byte[] freshPhotoBytes(int seed) throws java.io.IOException {
        return OnlineContent.get(LandscapeModel.photoUrl(seed), 6 * 1024 * 1024);
    }

    /** Average luminance of the downloaded photo (0=black, 1=white), sampled small. */
    private static float photoBrightness(Bitmap image) {
        if (image == null || image.isRecycled()) return -1;
        int w = 24, h = 12;
        Bitmap small = Bitmap.createScaledBitmap(image, w, h, true);
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        small.recycle();
        long total = 0;
        for (int p : pixels) total += (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000;
        return total / (float) (pixels.length * 255);
    }

    /** Adapts the screensaver clock/date to the photo's brightness (the 0x60 shade
     *  dims the photo, so only genuinely bright photos flip to dark text). */
    private void adaptClockColors(boolean brightPhoto) {
        clock.setTextColor(brightPhoto ? 0xff101418 : 0xfff4f7fb);
        date.setTextColor(brightPhoto ? 0xff3a4552 : 0xffc4d3e4);
    }

    private void requestPhoto() {
        if (!running || photoBusy || !online()) return;
        photoBusy = true;
        int request = generation;
        int previousPhoto = displayedPhoto;
        int firstPhoto = photoIndex;
        photoIndex++;
        worker.execute(() -> {
            Bitmap result = null;
            int selectedPhoto = -1;
            for (int attempt = 0; attempt < 3 && result == null && !Thread.currentThread().isInterrupted(); attempt++) {
              int candidate = firstPhoto + attempt;
              if (candidate == previousPhoto) continue;
              try {
                byte[] bytes = freshPhotoBytes(candidate);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException("Unsupported photo");
                bounds.inJustDecodeBounds = false;
                bounds.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > 1920) bounds.inSampleSize *= 2;
                result = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                selectedPhoto = candidate;
              } catch (Exception unavailable) { /* Try another landscape instead of retaining one failed source forever. */ }
            }
            Bitmap image = result;
            int loadedPhoto = selectedPhoto;
            // Sample brightness off the UI thread for the adaptive clock color.
            final boolean brightPhoto = image != null && photoBrightness(image) > 0.62f;
            handler.post(() -> {
                if (!running || request != generation) return;
                photoBusy = false;
                if (image != null && online()) {
                    // Fade through: old photo out, new photo in.
                    photo.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                        photo.setImageBitmap(image);
                        photo.animate().alpha(1f).setDuration(500).start();
                    }).start();
                    hasPhoto = true; displayedPhoto = loadedPhoto;
                    // No photo credit label: the screensaver shows just clock + photos.
                    status.setText("");
                    adaptClockColors(brightPhoto);
                } else {
                    status.setText("Landscape rotation unavailable · retrying automatically");
                    handler.removeCallbacks(rotate);
                    handler.postDelayed(rotate, 15_000);
                }
            });
        });
    }

    private void requestWeather() {
        if (!running || weatherBusy || !online()) return;
        weatherBusy = true;
        int request = generation;
        boolean fahrenheit = prefs.getBoolean("weather_fahrenheit", false);
        worker.execute(() -> {
            String result;
            try {
                double lat = Double.parseDouble(prefs.getString("weather_lat", Double.toString(LandscapeModel.LATITUDE)));
                double lon = Double.parseDouble(prefs.getString("weather_lon", Double.toString(LandscapeModel.LONGITUDE)));
                result = LandscapeModel.parseWeather(OnlineContent.json(LandscapeModel.weatherUrl(lat, lon, fahrenheit)), fahrenheit);
            } catch (Exception unavailable) { result = "Weather unavailable · retrying later"; }
            String value = result;
            handler.post(() -> {
                if (!running || request != generation) return;
                weatherBusy = false;
                if (online()) weather.setText(value);
            });
        });
    }
}
