package com.sadique.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.concurrent.Executors;

/**
 * BOOT_COMPLETED pre-warm: compose every app's icon card into the disk cache while the system
 * finishes booting, so the home screen opens with icons ready instead of composing them cold.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent == null ? null : intent.getAction())) return;
        final PendingResult result = goAsync();
        // Use a scheduled executor so we can enforce a time bound.
        // Respect BroadcastReceiver time limits: goAsync() gives us ~10s.
        java.util.concurrent.ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.execute(() -> {
            try {
                IconCache.warm(context);
            } finally {
                try {
                    result.finish();
                } finally {
                    exec.shutdown(); // Don't leak the thread.
                }
            }
        });
        // Safety: force shutdown after 8s to respect receiver time limits.
        exec.schedule(() -> {
            if (!exec.isShutdown()) exec.shutdownNow();
        }, 8, java.util.concurrent.TimeUnit.SECONDS);
    }
}
