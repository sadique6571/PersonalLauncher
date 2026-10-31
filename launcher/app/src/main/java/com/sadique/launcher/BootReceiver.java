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
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                IconCache.warm(context);
            } finally {
                result.finish();
            }
        });
    }
}
