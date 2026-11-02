package com.sadique.launcher;

import android.app.Notification;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Optional notification alerts. Bounded memory only; ongoing service status is excluded. */
public final class HomeNotificationService extends NotificationListenerService {
    private static final LinkedHashMap<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Set<Runnable> OBSERVERS = new HashSet<>();
    private static HomeNotificationService connected;
    static final class Entry {
        final String key, pkg, title, text;
        final android.app.PendingIntent open;
        Entry(StatusBarNotification sbn, String title, String text) {
            key = sbn.getKey(); pkg = sbn.getPackageName(); this.title = title; this.text = text;
            open = sbn.getNotification().contentIntent;
        }
    }
    static synchronized List<Entry> snapshot() { return new ArrayList<>(ENTRIES.values()); }
    static synchronized void observe(Runnable observer) { OBSERVERS.add(observer); }
    static synchronized void unobserve(Runnable observer) { OBSERVERS.remove(observer); }
    private static void changed() {
        List<Runnable> listeners;
        synchronized (HomeNotificationService.class) { listeners = new ArrayList<>(OBSERVERS); }
        Handler main = new Handler(Looper.getMainLooper());
        for (Runnable listener : listeners) main.post(() -> {
            synchronized (HomeNotificationService.class) { if (!OBSERVERS.contains(listener)) return; }
            listener.run();
        });
    }
    private void put(StatusBarNotification sbn) {
        if (sbn == null) return;
        synchronized (HomeNotificationService.class) {
            // Remove first: a formerly visible notification can become ongoing/empty on update.
            ENTRIES.remove(sbn.getKey());
            Notification n = sbn.getNotification();
            if (n != null && !getPackageName().equals(sbn.getPackageName())) {
                try {
                    Bundle extras = n.extras == null ? Bundle.EMPTY : n.extras;
                    CharSequence rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
                    CharSequence rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    if (rawText == null) rawText = extras.getCharSequence(Notification.EXTRA_TEXT);
                    String title = HomeLibraryModel.title(rawTitle == null ? "" : rawTitle.toString());
                    String text = HomeLibraryModel.title(rawText == null ? "" : rawText.toString());
                    int max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0);
                    boolean progress = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
                            || (max > 0 && extras.getInt(Notification.EXTRA_PROGRESS, 0) < max);
                    if (NotificationPolicy.show(n.flags, n.category, !title.isEmpty() || !text.isEmpty(), progress)) {
                        ENTRIES.put(sbn.getKey(), new Entry(sbn, title.isEmpty() ? sbn.getPackageName() : title, text));
                    }
                } catch (RuntimeException malformed) { /* Bad extras cannot crash the listener. */ }
            }
            while (ENTRIES.size() > 20) ENTRIES.remove(ENTRIES.keySet().iterator().next());
        }
        changed();
    }
    @Override public void onListenerConnected() {
        synchronized (HomeNotificationService.class) { connected = this; ENTRIES.clear(); }
        try {
            StatusBarNotification[] items = getActiveNotifications();
            if (items != null) for (StatusBarNotification item : items) put(item);
        } catch (RuntimeException unavailable) { /* Access can be revoked while connecting. */ }
        changed();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { put(sbn); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (HomeNotificationService.class) { if (sbn != null) ENTRIES.remove(sbn.getKey()); }
        changed();
    }
    @Override public void onListenerDisconnected() { clear(); }
    @Override public void onDestroy() { clear(); super.onDestroy(); }
    private void clear() {
        synchronized (HomeNotificationService.class) {
            if (connected == this) { connected = null; ENTRIES.clear(); }
        }
        changed();
    }
}
