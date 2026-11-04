package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Explicit opt-in; pairing stays in the TV's system dialog while setup runs through ADB. */
final class VolumeAutoUi {
    static void show(Activity host) {
        if (android.os.Build.VERSION.SDK_INT<30) {
            android.widget.Toast.makeText(host,"Automatic wireless pairing needs Android 11 or newer",android.widget.Toast.LENGTH_LONG).show(); return;
        }
        LinearLayout rows=new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        TextView status=GlassSettings.label(host,VolumeAutoStart.status(host),14,0xffe5f2ff); rows.addView(status);
        TextView explanation=GlassSettings.label(host,"Auto-start uses this TV's local wireless debugging. One-time pairing authorizes local ADB access. Home & remote setup can reuse this connection to keep the launcher as Home and restore its remote permissions. Port changes are detected automatically.",12,0xffb7cbdf);
        explanation.setPadding(0,12,0,12); rows.addView(explanation);
        rows.addView(GlassSettings.row(host,"Pairing setup","Open Wireless debugging → Pair device with pairing code. Keep that dialog open while completing the one-time setup from your existing ADB connection.",R.drawable.ic_settings,GlassSettings.themeAccent(host),() -> {
            try { host.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (RuntimeException unavailable) { android.widget.Toast.makeText(host,"Open Developer options from TV settings",android.widget.Toast.LENGTH_LONG).show(); }
        }));
        rows.addView(GlassSettings.row(host,"Enable / Start now","Requires completed pairing and the existing settings grant",R.drawable.ic_volume,GlassSettings.themeAccent(host),() -> VolumeAutoStart.enable(host)));
        rows.addView(GlassSettings.row(host,"Disable auto-start","Leave the current helper running; skip future automatic starts",R.drawable.ic_settings,GlassSettings.themeAccent(host),() -> VolumeAutoStart.disable(host)));
        AlertDialog dialog=GlassSettings.panel(host,"Volume auto-start",rows);
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable refresh=new Runnable() { public void run() {
            if (!dialog.isShowing() || !rows.isAttachedToWindow()) return;
            status.setText("Last result: "+VolumeAutoStart.status(host)); handler.postDelayed(this,1000);
        }};
        rows.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(android.view.View view) { handler.removeCallbacks(refresh); handler.post(refresh); }
            public void onViewDetachedFromWindow(android.view.View view) { handler.removeCallbacks(refresh); }
        });
        dialog.show(); handler.removeCallbacks(refresh); handler.post(refresh);
    }
    private VolumeAutoUi() { }
}
