package com.sadique.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Remote-friendly setup with actual system state and explicit repair controls. */
final class LauncherSetupUi {
    private LauncherSetupUi() { }
    static void show(Activity host) {
        LinearLayout rows=new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        TextView status=GlassSettings.label(host,"",14,0xffe5f2ff); rows.addView(status);
        TextView help=GlassSettings.label(host,"With saved local pairing, automatic setup keeps Personal Launcher as Home and restores its Home/Settings helpers after boot or an app update. On a new TV, approve Android permissions and pairing once.",12,0xffb7cbdf);
        help.setPadding(0,12,0,12); rows.addView(help);
        row(host,rows,"Enable automatic setup / Repair now","Use this TV's saved local ADB authorization",() -> HomeAutomation.enable(host));
        row(host,rows,"Stop automatic Home repair","Stop future Home changes; current default stays selected",() -> HomeAutomation.disable(host));
        row(host,rows,"Android Home picker","Choose through Android when this TV supports it",() -> {
            HomeAutomation.disable(host);
            host.startActivity(new Intent(host,HomeSettingsActivity.class));
        });
        row(host,rows,"Home / Settings button permissions","Enable this launcher's accessibility helpers",() -> open(host,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        row(host,rows,"App permissions / restricted settings","Android may require Allow restricted settings for this app",() -> open(host,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+host.getPackageName()))));
        row(host,rows,"Local pairing and volume auto-start","Pair once or inspect automatic volume recovery",() -> VolumeAutoUi.show(host));
        AlertDialog dialog=GlassSettings.panel(host,"Home & remote setup",rows);
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable refresh=new Runnable() { @Override public void run() {
            if (!dialog.isShowing() || !rows.isAttachedToWindow()) return;
            boolean grant=host.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;
            status.setText("Default Home: "+(HomeAutomation.isDefault(host)?"Personal Launcher":HomeAutomation.currentHome(host))
                    +"\nAutomatic repair: "+(HomeAutomation.enabled(host)?"On":"Off")
                    +"\nSaved pairing: "+(VolumeAutoStart.paired(host)?"Ready":"Required")
                    +"\nSettings permission: "+(grant?"Granted":"Required")
                    +"\nHome permission: "+(HomeAutomation.helperEnabled(host,HomeKeyService.class)?"On":"Off")
                    +" · Settings permission: "+(HomeAutomation.helperEnabled(host,InputKeyService.class)?"On":"Off")
                    +"\nHome listener: "+(HomeKeyService.isConnected()?"Connected":"Waiting")
                    +"\nHome result: "+HomeAutomation.status(host)+"\nVolume: "+VolumeAutoStart.status(host));
            handler.postDelayed(this,1000);
        }};
        rows.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View view) { handler.removeCallbacks(refresh); handler.post(refresh); }
            @Override public void onViewDetachedFromWindow(android.view.View view) { handler.removeCallbacks(refresh); }
        });
        dialog.show(); handler.post(refresh);
    }
    private static void row(Activity host,LinearLayout rows,String title,String hint,Runnable click) {
        rows.addView(GlassSettings.row(host,title,hint,R.drawable.ic_settings,GlassSettings.themeAccent(host),click));
    }
    private static void open(Activity host,Intent intent) {
        try { host.startActivity(intent); }
        catch (RuntimeException unavailable) { android.widget.Toast.makeText(host,"Open this option from TV settings",android.widget.Toast.LENGTH_LONG).show(); }
    }
}
