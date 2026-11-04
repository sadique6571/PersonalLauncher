package com.sadique.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;

/** Opt-in Home repair using the already-authorized local ADB identity. */
final class HomeAutomation {
    private static final HomeRepairPolicy POLICY=new HomeRepairPolicy();
    private HomeAutomation() { }
    private static synchronized SharedPreferences prefs(Context c) {
        SharedPreferences p=c.getSharedPreferences("home_automation",0);
        if (!p.getBoolean("initialized",false)) {
            // This upgrade was requested for an existing paired, automatic-volume installation.
            // Fresh installs remain off and enable this explicitly from the setup guide.
            boolean migrate=VolumeAutoStart.paired(c) && VolumeAutoStart.enabled(c);
            p.edit().putBoolean("initialized",true).putBoolean("enabled",migrate).apply();
        }
        return p;
    }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled",false); }
    static void enable(Context c) {
        prefs(c).edit().putBoolean("enabled",true).apply();
        state(c,VolumeAutoStart.paired(c)?"Home setup queued":"One-time local pairing is required");
        VolumeAutoStart.requestHome(c);
    }
    static void disable(Context c) {
        prefs(c).edit().putBoolean("enabled",false).apply();
        state(c,"Automatic Home repair off");
    }
    static String status(Context c) { return prefs(c).getString("status",enabled(c)?"Waiting for automatic setup":"Automatic Home repair off"); }
    static void state(Context c,String value) { prefs(c).edit().putString("status",value).apply(); }
    static String currentHome(Context c) {
        ResolveInfo resolved=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        return resolved==null || resolved.activityInfo==null?"Unavailable":resolved.activityInfo.packageName;
    }
    static boolean isDefault(Context c) { return c.getPackageName().equals(currentHome(c)); }
    static boolean helperEnabled(Context c,Class<?> helper) {
        String list=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName expected=new ComponentName(c,helper);
        if (list!=null) for (String entry:list.split(":")) if (expected.equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }
    static void onResume(Context c) {
        if (enabled(c) && VolumeAutoStart.paired(c) && !VolumeAutoStart.busy() && POLICY.allow(SystemClock.elapsedRealtime(),isDefault(c)))
            VolumeAutoStart.requestHome(c);
    }
}
