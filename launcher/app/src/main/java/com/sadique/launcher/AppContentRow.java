package com.sadique.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** A horizontal banner row for one app (Netflix, YouTube), fed by the TV's Watch Next. */
public final class AppContentRow extends LinearLayout {
    private final Activity host;
    private final SharedPreferences prefs;
    private final String[] packages;
    private final Consumer<String> focusTitle;
    private final LinearLayout cards;
    private final TextView heading;
    /** Current label color, following the wallpaper brightness (cards sit on the wallpaper). */
    private int labelColor = 0xfff0f7ff;
    private final HorizontalScrollView scroller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private ExecutorService artworkWorker;
    private CancellationSignal query;
    private boolean running;
    private int generation;

    public AppContentRow(Activity host, String title, String[] packages, Consumer<String> focusTitle) {
        super(host);
        this.host = host;
        this.packages = packages;
        this.focusTitle = focusTitle;
        prefs = host.getSharedPreferences("personal_home", 0);
        setOrientation(VERTICAL);
        int rowGap = new int[]{8, 18, 28}[Math.max(0, Math.min(2, prefs.getInt("row_spacing", 1)))];
        setPadding(0, dp(rowGap), 0, dp(8));
        // Title row with app logo.
        LinearLayout titleRow = new LinearLayout(host);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(host);
        try {
            String iconPkg = packages[0];
            logo.setImageDrawable(host.getPackageManager().getApplicationIcon(iconPkg));
        } catch (Exception ignored) {}
        titleRow.addView(logo, new LayoutParams(dp(28), dp(28)));
        heading = new TextView(host);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setTextColor(0xfff0f7ff);
        heading.setPadding(dp(10), 0, 0, dp(10));
        titleRow.addView(heading, new LayoutParams(-2, -2));
        addView(titleRow);
        scroller = new HorizontalScrollView(host);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        cards = new LinearLayout(host);
        cards.setOrientation(HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        scroller.addView(cards, new LayoutParams(-2, -2));
        addView(scroller, new LayoutParams(-1, -2));
        setVisibility(GONE);
    }

    /** Adapts the heading and card labels to wallpaper brightness. */
    void setHeadingColor(int color) {
        labelColor = color;
        heading.setTextColor(color);
        for (int i = 0; i < cards.getChildCount(); i++) {
            android.view.View card = cards.getChildAt(i);
            if (card instanceof LinearLayout) {
                LinearLayout box = (LinearLayout) card;
                for (int j = 0; j < box.getChildCount(); j++) {
                    android.view.View v = box.getChildAt(j);
                    if (v instanceof TextView && "banner_label".equals(v.getTag())) ((TextView) v).setTextColor(color);
                }
            }
        }
    }

    void start() {
        if (running) return;
        running = true;
        worker = Executors.newSingleThreadExecutor();
        artworkWorker = Executors.newFixedThreadPool(2);
        reload();
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacks(autoScroll);
        if (query != null) query.cancel();
        if (worker != null) worker.shutdownNow();
        if (artworkWorker != null) artworkWorker.shutdownNow();
    }

    /** Gentle auto-scroll for banner rows (toggle in settings). */
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(this);
            if (!running || !prefs.getBoolean("banner_autoscroll", false)) return;
            int max = cards.getWidth() - scroller.getWidth();
            if (max > 0) {
                int next = scroller.getScrollX() + dp(160);
                if (next >= max) next = 0;
                scroller.smoothScrollTo(next, 0);
            }
            handler.postDelayed(this, 4000);
        }
    };

    void reload() {
        if (!running) return;
        if (query != null) query.cancel();
        query = new CancellationSignal();
        CancellationSignal cancellation = query;
        int request = ++generation;
        cards.removeAllViews();
        worker.execute(() -> {
            List<Item> entries = new ArrayList<>();
            String[] columns = {TvContract.WatchNextPrograms.COLUMN_TITLE, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                    TvContract.WatchNextPrograms.COLUMN_INTENT_URI, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI, TvContract.WatchNextPrograms.COLUMN_BROWSABLE};
            try (Cursor cursor = host.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, columns,
                    null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC", cancellation)) {
                int scanned = 0;
                while (cursor != null && cursor.moveToNext() && scanned++ < 200 && entries.size() < 12) {
                    String title = cursor.getString(0), pkg = cursor.getString(1), intent = cursor.getString(2);
                    if (title == null || title.trim().isEmpty() || pkg == null || intent == null || cursor.getInt(5) == 0) continue;
                    boolean match = false;
                    for (String p : packages) if (p.equals(pkg)) { match = true; break; }
                    if (!match) continue;
                    entries.add(new Item(title, pkg, intent, cursor.getString(3), cursor.getString(4)));
                }
            } catch (Exception ignored) {}
            handler.post(() -> {
                if (!running || request != generation) return;
                for (Item item : entries) addCard(item);
                setVisibility(entries.isEmpty() ? GONE : VISIBLE);
                handler.removeCallbacks(autoScroll);
                if (prefs.getBoolean("banner_autoscroll", false) && !entries.isEmpty()) {
                    handler.postDelayed(autoScroll, 4000);
                }
            });
        });
    }

    private void addCard(Item item) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setFocusable(true);
        card.setClickable(true);
        ImageView poster = new ImageView(host);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Rounded corners like the app tiles: clip the poster bitmap to the
        // rounded outline (tile_bg radius = 16dp).
        poster.setBackgroundResource(R.drawable.banner_poster_bg);
        poster.setClipToOutline(true);
        int sizePref = Math.max(0, Math.min(2, prefs.getInt("banner_poster_size", 1)));
        int w = dp(new int[]{120, 160, 200}[sizePref]), h = dp(new int[]{68, 90, 112}[sizePref]);
        card.addView(poster, new LayoutParams(w, h));
        TextView label = new TextView(host);
        label.setText(item.title);
        label.setTextSize(13);
        label.setTextColor(labelColor);
        label.setTag("banner_label");
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(0, dp(6), 0, 0);
        card.addView(label, new LayoutParams(w, -2));
        card.setOnClickListener(v -> {
            try { host.startActivity(Intent.parseUri(item.intent, 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) {}
        });
        card.setOnFocusChangeListener((v, focus) -> {
            if (focus) focusTitle.accept(item.title);
            v.setBackground(focus ? focusBorder() : null);
        });
        cards.addView(card);
        int request = generation;
        artworkWorker.execute(() -> {
            if (!running || request != generation) return;
            Bitmap bitmap = ResumeArtwork.load(host, item.thumbnail, item.poster);
            if (bitmap != null) handler.post(() -> {
                if (!running || request != generation) return;
                poster.setImageBitmap(bitmap);
            });
        });
    }

    private android.graphics.drawable.Drawable focusBorder() {
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(0x00000000);
        border.setCornerRadius(dp(16));
        border.setStroke(dp(2), GlassSettings.themeAccent(host));
        return border;
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }

    private static final class Item {
        final String title, pkg, intent, poster, thumbnail;
        Item(String title, String pkg, String intent, String poster, String thumbnail) {
            this.title = title; this.pkg = pkg; this.intent = intent;
            this.poster = poster; this.thumbnail = thumbnail;
        }
    }
}
