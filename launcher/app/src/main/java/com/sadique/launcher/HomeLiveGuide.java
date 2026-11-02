package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.CancellationSignal;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only, bounded now/next guide from TV providers, not Google's regional Freeplay catalogue. */
final class HomeLiveGuide {
    private HomeLiveGuide() { }
    static void show(Activity host) {
        LinearLayout rows = HomeLibraryUi.rows(host);
        AlertDialog dialog = GlassSettings.panel(host, host.getString(R.string.h_live_guide), rows);
        if (host.checkSelfPermission(ContinueWatchingView.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            rows.addView(GlassSettings.row(host, host.getString(R.string.h_tv_access), host.getString(R.string.h_guide_access), R.drawable.ic_input,
                    GlassSettings.themeAccent(host), () -> {
                        try { host.requestPermissions(new String[]{ContinueWatchingView.PERMISSION}, ContinueWatchingView.PERMISSION_REQUEST); }
                        catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
                    })); dialog.show(); return;
        }
        android.widget.TextView status = GlassSettings.label(host, host.getString(R.string.h_loading), 14, 0xffb5cadd); rows.addView(status);
        ExecutorService worker = Executors.newSingleThreadExecutor(); CancellationSignal cancellation = new CancellationSignal();
        dialog.setOnDismissListener(d -> { cancellation.cancel(); worker.shutdownNow(); });
        dialog.show();
        worker.execute(() -> {
            List<String[]> items = new ArrayList<>();
            try (Cursor channels = host.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                    new String[]{"_id", "display_name", "browsable"}, null, null, null, cancellation)) {
                int scanned = 0;
                while (channels != null && channels.moveToNext() && scanned++ < 200 && items.size() < 24 && !cancellation.isCanceled()) {
                    if (channels.getInt(2) == 0) continue;
                    long id = channels.getLong(0), now = System.currentTimeMillis();
                    String name = channels.getString(1), detail = "";
                    Uri programs = TvContract.buildProgramsUriForChannel(id, now, now + 4 * 60 * 60_000L);
                    try (Cursor epg = host.getContentResolver().query(programs, new String[]{"title", "start_time_utc_millis", "end_time_utc_millis"}, null, null, "start_time_utc_millis ASC", cancellation)) {
                        int read = 0;
                        while (epg != null && epg.moveToNext() && read++ < 2) {
                            String time = android.text.format.DateFormat.getTimeFormat(host).format(new java.util.Date(epg.getLong(1)));
                            detail += (detail.isEmpty() ? "" : "\n") + time + " · " + epg.getString(0);
                        }
                    } catch (RuntimeException unavailable) { /* Channel can still be opened without its EPG. */ }
                    items.add(new String[]{Long.toString(id), name == null ? Long.toString(id) : name, detail});
                }
            } catch (RuntimeException unavailable) { /* The provider may refuse third-party listing access. */ }
            host.runOnUiThread(() -> {
                if (host.isDestroyed() || !dialog.isShowing()) return;
                status.setText(items.isEmpty() ? host.getString(R.string.h_guide_empty) : host.getString(R.string.h_guide_hint));
                for (String[] item : items) rows.addView(GlassSettings.row(host, item[1], item[2], R.drawable.ic_input, GlassSettings.themeAccent(host), () -> {
                    try { host.startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUri(Long.parseLong(item[0])))); }
                    catch (RuntimeException unavailable) { Toast.makeText(host, R.string.h_unavailable, Toast.LENGTH_LONG).show(); }
                }));
            }); worker.shutdown();
        });
    }
}
