package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Read-only, shared Android TV Watch Next entries. Never scrapes an app's private history. */
public final class ContinueWatchingView extends LinearLayout {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int PERMISSION_REQUEST = 31;
    private final Activity host;
    private final SharedPreferences prefs;
    private final Consumer<String> focusTitle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout cards;
    private final TextView heading;
    private final TextView explanation;
    private final Button allow;
    private ExecutorService worker;
    /** Poster artwork has its own queue so a slow download never blocks the provider query. */
    private ExecutorService artworkWorker;
    private CancellationSignal query;
    private boolean running, observing;
    private int generation;
    private final Runnable refresh = this::reload;
    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 750); }
    };

    public ContinueWatchingView(Activity host, Consumer<String> focusTitle) {
        super(host); this.host = host; this.focusTitle = focusTitle;
        prefs = host.getSharedPreferences("personal_home", 0);
        setOrientation(VERTICAL); setPadding(0, dp(22), 0, dp(12));
        heading = label("Continue watching", 23);
        addView(heading);
        HorizontalScrollView scroll = new HorizontalScrollView(host);
        scroll.setHorizontalScrollBarEnabled(false);
        cards = new LinearLayout(host);
        cards.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(cards, new LayoutParams(-2, -2));
        addView(scroll, new LayoutParams(-1, -2));
        explanation = label("Only resume entries shared by supported TV apps appear here.", 14);
        addView(explanation);
        allow = new Button(host);
        allow.setText("Allow TV Watch Next access"); allow.setAllCaps(false);
        allow.setOnClickListener(v -> requestAccess());
        addView(allow, new LayoutParams(-2, -2));
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView text = new TextView(host); text.setText(value); text.setTextSize(size); text.setTextColor(Color.WHITE);
        text.setShadowLayer(dp(5), 0, dp(2), 0xE6000000);
        return text;
    }
    public void start() {
        if (running) { reload(); return; }
        running = true; worker = Executors.newFixedThreadPool(2); artworkWorker = Executors.newFixedThreadPool(2);
        try { host.getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, observer); observing = true; }
        catch (RuntimeException unavailable) { observing = false; }
        reload();
    }
    public void stop() {
        running = false; generation++;
        if (query != null) query.cancel();
        if (observing) { try { host.getContentResolver().unregisterContentObserver(observer); } catch (RuntimeException ignored) { } observing = false; }
        if (worker != null) worker.shutdownNow();
        if (artworkWorker != null) artworkWorker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }
    public void requestAccess() {
        if (host.getPackageManager().resolveContentProvider(TvContract.AUTHORITY, 0) == null) {
            Toast.makeText(host, "This device has no TV Watch Next provider", Toast.LENGTH_LONG).show(); return;
        }
        if (host.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) { reload(); return; }
        try {
            host.getPackageManager().getPermissionInfo(PERMISSION, 0);
            new AlertDialog.Builder(host).setTitle("Continue watching access")
                    .setMessage("Allow reading TV listings so this launcher can show Watch Next entries shared by your apps. Private app history is not accessed.")
                    .setPositiveButton("Continue", (d, which) -> host.requestPermissions(new String[]{PERMISSION}, PERMISSION_REQUEST))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } catch (PackageManager.NameNotFoundException | SecurityException unavailable) {
            Toast.makeText(host, "This device does not expose shared TV Watch Next access", Toast.LENGTH_LONG).show();
        }
    }

    public void reload() {
        // The Netflix/YouTube banner rows already show continue-watching content,
        // so this row hides while any banner row is enabled (avoids duplication).
        boolean bannersOn = prefs.getBoolean("row_netflix", false)
                || prefs.getBoolean("row_youtube", false);
        boolean enabled = prefs.getBoolean("continue_watching", true) && !bannersOn;
        setVisibility(enabled ? VISIBLE : GONE);
        int request = ++generation;
        if (query != null) query.cancel();
        if (!running || !enabled) { cards.removeAllViews(); return; }
        query = new CancellationSignal();
        CancellationSignal cancellation = query;
        cards.removeAllViews();
        if (host.getPackageManager().resolveContentProvider(TvContract.AUTHORITY, 0) == null) {
            allow.setVisibility(GONE); explanation.setText("Resume is available on Android TVs with a shared Watch Next provider."); return;
        }
        boolean permitted = host.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
        allow.setVisibility(permitted ? GONE : VISIBLE);
        if (!permitted) { explanation.setText("Allow TV listings access to read shared Watch Next entries. Some TVs restrict this feature."); return; }
        explanation.setText("Loading shared resume entries…");
        worker.execute(() -> {
            List<Item> entries = new ArrayList<>();
            String error = null;
            String[] columns = {TvContract.WatchNextPrograms.COLUMN_TITLE, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI, TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI};
            Set<String> hidden = prefs.getStringSet("hidden_apps", Collections.emptySet());
            try (Cursor cursor = host.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null,
                    TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC", cancellation)) {
                int scanned = 0;
                while (cursor != null && cursor.moveToNext() && scanned++ < 200 && entries.size() < 16) {
                    String title = cursor.getString(0), pkg = cursor.getString(1), intent = cursor.getString(2);
                    long position = cursor.getLong(4), duration = cursor.getLong(5);
                    if (title == null || title.trim().isEmpty() || pkg == null || intent == null || intent.length() > 8192 || hidden.contains(pkg) || cursor.getInt(7) == 0
                            || !WatchNextModel.resumable(cursor.getInt(3), position, duration)) continue;
                    String owner;
                    try { owner = host.getPackageManager().getApplicationLabel(host.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
                    catch (PackageManager.NameNotFoundException removed) { continue; }
                    entries.add(new Item(title, pkg, owner, intent, position, duration, cursor.getString(8), cursor.getString(9)));
                }
            } catch (RuntimeException unavailable) { error = "Your TV has not exposed shared Watch Next entries to this launcher."; }
            String message = error;
            handler.post(() -> {
                if (!running || request != generation) return;
                for (Item item : entries) addCard(item);
                explanation.setText(message != null ? message : entries.isEmpty()
                        ? "No shared resume items yet. Apps must publish Watch Next entries; private histories cannot be read."
                        : "Shared by your TV apps · select a title to resume");
            });
        });
    }

    private void addCard(Item item) {
        LinearLayout card = new LinearLayout(host); card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setFocusable(true); card.setClickable(true);
        card.setBackground(background(false)); card.setContentDescription("Resume " + item.title + " in " + item.owner);
        ImageView poster = new ImageView(host);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(background(false)); poster.setClipToOutline(true);
        poster.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(poster, new LayoutParams(-1, dp(106)));
        try { poster.setImageDrawable(host.getPackageManager().getApplicationIcon(item.pkg)); }
        catch (PackageManager.NameNotFoundException ignored) { }
        int request = generation;
        artworkWorker.execute(() -> {
            if (!running || request != generation) return;
            Bitmap bitmap = ResumeArtwork.load(host, item.thumbnail, item.poster);
            if (bitmap != null) handler.post(() -> {
                if (running && request == generation) poster.setImageBitmap(bitmap);
            });
        });
        TextView title = label(item.title, 19); title.setPadding(0, dp(10), 0, 0); title.setMaxLines(2); title.setMinLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, new LayoutParams(-1, -2));
        TextView owner = label(item.owner, 13); owner.setPadding(0, dp(6), 0, dp(8)); card.addView(owner);
        if (item.duration > 0) {
            ProgressBar progress = new ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000); progress.setProgress(WatchNextModel.progress(item.position, item.duration));
            card.addView(progress, new LayoutParams(-1, dp(6)));
        }
        card.setOnFocusChangeListener((v, focused) -> { card.setBackground(background(focused)); if (focused) focusTitle.accept(item.owner); });
        card.setOnClickListener(v -> {
            try {
                Intent intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME);
                intent.setSelector(null);
                if (intent.getComponent() != null && !item.pkg.equals(intent.getComponent().getPackageName())) intent.setComponent(null);
                intent.setPackage(item.pkg);
                intent.setAction(Intent.ACTION_VIEW); intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.startActivity(intent);
            } catch (Exception unavailable) { Toast.makeText(host, "This app cannot resume the item now", Toast.LENGTH_LONG).show(); }
        });
        LayoutParams position = new LayoutParams(dp(220), -2); position.setMargins(dp(6), 0, dp(6), 0);
        cards.addView(card, position);
    }
    private GradientDrawable background(boolean focused) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(0xe00a2036); shape.setCornerRadius(dp(16));
        shape.setStroke(dp(focused ? 3 : 1), focused ? 0xff40d9ff : 0xff28435e); return shape;
    }
    private static final class Item {
        final String title, pkg, owner, intent, poster, thumbnail; final long position, duration;
        Item(String title, String pkg, String owner, String intent, long position, long duration, String poster, String thumbnail) {
            this.title=title; this.pkg=pkg; this.owner=owner; this.intent=intent; this.position=position; this.duration=duration; this.poster=poster; this.thumbnail=thumbnail;
        }
    }
}
