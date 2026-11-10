package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/** A translucent task over the current app; opening the panel never raises the Home task. */
public final class QuickSettingsActivity extends Activity {
    private AlertDialog panel, gate;
    private boolean authorized;
    private GlassBluetooth bluetooth;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0,0);
        bluetooth=new GlassBluetooth(this,()->{});
        FrameLayout scrim=new FrameLayout(this); scrim.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scrim.setOnClickListener(v->finish()); setContentView(scrim);
        gate=LauncherPin.require(this,()-> {
            if(isFinishing() || isDestroyed()) return;
            authorized=true; showPanel();
        });
        if(gate!=null) gate.setOnDismissListener(d->main.post(()-> { if(!authorized) finish(); }));
        else if(!authorized) finish();
    }
    private void showPanel() {
        panel=HomeDashboard.show(this,network(),()->{},new HomeDashboard.Actions() {
            @Override public boolean keepPanel(int action) {
                return (action==HomeDashboard.BLUETOOTH && ThemeStyle.id(ThemePreferences.get(QuickSettingsActivity.this))==ThemeStyle.GLASS) || action==HomeDashboard.INPUTS || action==HomeDashboard.DISPLAY || action==HomeDashboard.SOUND;
            }
            @Override public void open(int action) {
                switch(action) {
                    case HomeDashboard.SETTINGS:
                        openActivity(new Intent(QuickSettingsActivity.this,MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS)); break;
                    case HomeDashboard.WIFI: openActivity(SystemSettings.intent(QuickSettingsActivity.this,Settings.ACTION_WIFI_SETTINGS)); break;
                    case HomeDashboard.BLUETOOTH: if(ThemeStyle.id(ThemePreferences.get(QuickSettingsActivity.this))==ThemeStyle.GLASS)bluetooth.show();else openActivity(SystemSettings.bluetoothAccessoriesIntent(QuickSettingsActivity.this)); break;
                    case HomeDashboard.INPUTS: inputs(); break;
                    case HomeDashboard.DISPLAY: TvControlPreview.show(QuickSettingsActivity.this,true); break;
                    case HomeDashboard.SOUND: TvControlPreview.show(QuickSettingsActivity.this,false); break;
                    case HomeDashboard.SCREENSAVER: openActivity(new Intent(QuickSettingsActivity.this,LandscapeActivity.class)); break;
                    case HomeDashboard.STORE:
                        Intent store=getPackageManager().getLaunchIntentForPackage("com.android.vending");
                        openActivity(store==null?new Intent(Intent.ACTION_VIEW,Uri.parse("market://apps")):store); break;
                    default: openActivity(SystemSettings.intent(QuickSettingsActivity.this,Settings.ACTION_SETTINGS)); break;
                }
            }
        },true);
        panel.setOnDismissListener(d->finish());
    }
    private String network() {
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        android.net.Network active=manager==null?null:manager.getActiveNetwork();
        NetworkCapabilities caps=active==null?null:manager.getNetworkCapabilities(active);
        if(caps==null) return "Disconnected";
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Connected":"Network connected · internet not verified";
    }
    private void inputs() {
        LinearLayout rows=HomeLibraryUi.rows(this);
        TvInputManager manager=getSystemService(TvInputManager.class);
        java.util.List<TvInputInfo> sources=manager==null?java.util.Collections.emptyList():manager.getTvInputList();
        for(TvInputInfo input:sources) {
            if(input.isHidden(this)) continue;
            Uri uri=input.isPassthroughInput()?TvContract.buildChannelUriForPassthroughInput(input.getId()):TvContract.Channels.CONTENT_URI;
            rows.addView(GlassSettings.row(this,input.loadLabel(this).toString(),null,R.drawable.ic_input,
                    GlassSettings.themeAccent(this),()->openActivity(new Intent(Intent.ACTION_VIEW,uri))));
        }
        if(rows.getChildCount()==0) rows.addView(GlassSettings.row(this,"Open TV input settings",null,R.drawable.ic_input,
                GlassSettings.themeAccent(this),()->openActivity(SystemSettings.intent(this,Settings.ACTION_SETTINGS))));
        GlassSettings.panel(this,getString(R.string.h_inputs),rows).show();
    }
    private void openActivity(Intent intent) {
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch(ActivityNotFoundException | SecurityException unavailable) { Toast.makeText(this,R.string.not_available,Toast.LENGTH_SHORT).show(); }
        finish();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); finish(); }
    @Override protected void onPause() { if(bluetooth!=null)bluetooth.stop();super.onPause();if(bluetooth==null||!bluetooth.requestingPermission){finish();overridePendingTransition(0,0);} }
    @Override protected void onDestroy() {
        if(gate!=null) { gate.setOnDismissListener(null); gate.dismiss(); }
        if(panel!=null) { panel.setOnDismissListener(null); panel.dismiss(); }
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override protected void onResume(){super.onResume();if(bluetooth!=null)bluetooth.start();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==GlassBluetooth.PERMISSION)bluetooth.requestingPermission=false;
        if(code==GlassBluetooth.PERMISSION && results.length>0 && results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){bluetooth.stop();bluetooth.start();bluetooth.show();}
    }

}
