package com.sadique.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.Set;

/** Alternative to the key helpers: observes stock Home windows and never requests key filtering. */
public final class PassiveHomeService extends AccessibilityService {
    private final Set<String> homeActivities = new HashSet<>();
    private String stockPackage = "";
    private long lastRedirect;
    private BroadcastReceiver unlock;

    @Override protected void onServiceConnected() {
        if (getSystemService(UserManager.class).isUserUnlocked()) configure();
        else {
            unlock = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    stopWaiting(); configure();
                }
            };
            registerReceiver(unlock, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            if (getSystemService(UserManager.class).isUserUnlocked()) { stopWaiting(); configure(); }
        }
    }

    private void configure() {
        Config config = Config.load(this);
        stockPackage = config.stockHomeRedirect;
        homeActivities.clear();
        if (stockPackage.isEmpty()) return;
        Intent stock = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(stockPackage);
        for (ResolveInfo entry : getPackageManager().queryIntentActivities(stock, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (entry.activityInfo != null) homeActivities.add(entry.activityInfo.name);
        }
        Log.d("PersonalVolume", "Passive Home connected; no key-filter capability, stock activities=" + homeActivities.size());
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !matchesHome(stockPackage, homeActivities, event.getPackageName(), event.getClassName())) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirect < 1500) return;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            lastRedirect = now;
        } catch (RuntimeException unavailable) { Log.w("PersonalVolume", "Passive Home redirect failed", unavailable); }
    }

    static boolean matchesHome(String stockPackage, Set<String> activities, CharSequence pkg, CharSequence cls) {
        return !stockPackage.isEmpty() && pkg != null && cls != null
                && stockPackage.contentEquals(pkg) && activities.contains(cls.toString());
    }
    static boolean enabled(Context context) {
        String services = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName mine = new ComponentName(context, PassiveHomeService.class);
        if (services != null) for (String service : services.split(":")) {
            if (mine.equals(ComponentName.unflattenFromString(service))) return true;
        }
        return false;
    }
    private void stopWaiting() {
        if (unlock != null) { unregisterReceiver(unlock); unlock = null; }
    }
    @Override public void onDestroy() { stopWaiting(); super.onDestroy(); }
    @Override public void onInterrupt() { }
}
