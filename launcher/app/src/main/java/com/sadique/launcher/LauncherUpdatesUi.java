package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

final class LauncherUpdatesUi {
    private LauncherUpdatesUi() { }
    static void show(Activity host) {
        LinearLayout rows=new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        TextView state=GlassSettings.label(host,"",14,0xffe5f2ff); rows.addView(state);
        Handler handler=new Handler(Looper.getMainLooper());
        AlertDialog[] panel=new AlertDialog[1];
        rows.addView(GlassSettings.row(host,"Automatic GitHub updates","Check and install while Home is idle",R.drawable.ic_settings,
                GlassSettings.themeAccent(host),() -> GitHubUpdates.enabled(host,!GitHubUpdates.enabled(host))));
        rows.addView(GlassSettings.row(host,"Check now","Check the latest stable release",R.drawable.ic_internet,
                GlassSettings.themeAccent(host),() -> { panel[0].dismiss(); handler.postDelayed(() -> GitHubUpdates.check(host,true),300); }));
        rows.addView(GlassSettings.row(host,"Install update","Install a verified download",R.drawable.ic_apps,
                GlassSettings.themeAccent(host),() -> { panel[0].dismiss(); handler.postDelayed(() -> GitHubUpdates.installReady(host),300); }));
        panel[0]=GlassSettings.panel(host,"Updates · GitHub",rows);
        Runnable refresh=new Runnable() { @Override public void run() {
            if(!panel[0].isShowing() || !rows.isAttachedToWindow()) return;
            state.setText("Automatic updates: "+(GitHubUpdates.enabled(host)?"On":"Off")+"\n"+GitHubUpdates.status(host));
            handler.postDelayed(this,1000);
        }};
        rows.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View view) { handler.removeCallbacks(refresh); handler.post(refresh); }
            @Override public void onViewDetachedFromWindow(android.view.View view) { handler.removeCallbacks(refresh); }
        });
        panel[0].show(); handler.removeCallbacks(refresh); handler.post(refresh);
    }
}
