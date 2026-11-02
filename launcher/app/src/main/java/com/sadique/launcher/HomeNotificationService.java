package com.sadique.launcher;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Optional user-approved notification access. A bounded in-memory snapshot, never persisted or logged. */
public final class HomeNotificationService extends NotificationListenerService {
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static HomeNotificationService connected;
    static final class Entry {
        final String key, pkg, title, text;
        final android.app.PendingIntent open;
        Entry(StatusBarNotification sbn) {
            Notification n = sbn.getNotification(); key = sbn.getKey(); pkg = sbn.getPackageName();
            title = HomeLibraryModel.title(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE, pkg)));
            CharSequence value = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
            text = HomeLibraryModel.title(value == null ? "" : value.toString()); open = n.contentIntent;
        }
    }
    static synchronized List<Entry> snapshot() { return new ArrayList<>(ENTRIES.values()); }
    private static synchronized void put(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null || "com.sadique.launcher".equals(sbn.getPackageName())
                || (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        ENTRIES.remove(sbn.getKey()); ENTRIES.put(sbn.getKey(), new Entry(sbn));
        while (ENTRIES.size() > 20) ENTRIES.remove(ENTRIES.keySet().iterator().next());
    }
    @Override public void onListenerConnected() {
        synchronized (HomeNotificationService.class) { connected = this; ENTRIES.clear(); }
        try { for (StatusBarNotification item : getActiveNotifications()) put(item); }
        catch (RuntimeException unavailable) { /* Access may be revoked while connecting. */ }
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { put(sbn); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (HomeNotificationService.class) { if (sbn != null) ENTRIES.remove(sbn.getKey()); }
    }
    @Override public void onListenerDisconnected() { clear(); }
    @Override public void onDestroy() { clear(); super.onDestroy(); }
    private void clear() {
        synchronized (HomeNotificationService.class) {
            if (connected == this) { connected = null; ENTRIES.clear(); }
        }
    }
}
