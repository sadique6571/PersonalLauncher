package com.sadique.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Launcher UI gate only, deliberately separate from Android's device/account security. */
final class LauncherPin {
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor();
    private LauncherPin() { }
    static boolean valid(String pin) { return pin != null && pin.matches("[0-9]{4,8}"); }
    static String encode(String pin) throws Exception {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derive(pin, salt));
    }
    static boolean matches(String pin, String stored) {
        try {
            if (!valid(pin) || stored == null) return false;
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]), expected = Base64.getDecoder().decode(parts[1]);
            return salt.length == 16 && expected.length == 32 && MessageDigest.isEqual(expected, derive(pin, salt));
        } catch (Exception malformed) { return false; }
    }
    private static byte[] derive(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    private static SharedPreferences prefs(Activity host) { return host.getSharedPreferences("launcher_pin", 0); }
    static AlertDialog require(Activity host, Runnable allowed) {
        SharedPreferences prefs = prefs(host);
        String saved = prefs.getString("hash", "");
        if (saved.isEmpty()) { allowed.run(); return null; }
        // Persisted wall time keeps the short cooldown across process death; clock changes cannot create an indefinite lock.
        long wait = prefs.getLong("retry_at", 0) - System.currentTimeMillis();
        if (wait > 0 && wait <= 30000) { Toast.makeText(host, R.string.h_pin_wait, Toast.LENGTH_LONG).show(); return null; }
        return prompt(host, R.string.h_pin_enter, (pin, dialog, input) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            WORKER.execute(() -> {
                boolean correct = matches(pin, saved);
                host.runOnUiThread(() -> {
                    if (host.isDestroyed() || !dialog.isShowing()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    if (correct) {
                        prefs.edit().remove("failures").remove("retry_at").apply(); dialog.dismiss(); allowed.run();
                    } else {
                        int count = prefs.getInt("failures", 0) + 1;
                        prefs.edit().putInt("failures", count >= 5 ? 0 : count)
                                .putLong("retry_at", count >= 5 ? System.currentTimeMillis() + 30000 : 0).apply();
                        if (count >= 5) { dialog.dismiss(); Toast.makeText(host, R.string.h_pin_wait, Toast.LENGTH_LONG).show(); }
                        else { input.setText(""); input.setError(host.getString(R.string.h_pin_wrong)); }
                    }
                });
            });
        });
    }
    static void settings(Activity host) {
        require(host, () -> {
            String[] choices = prefs(host).getString("hash", "").isEmpty()
                    ? new String[]{host.getString(R.string.h_pin_new)}
                    : new String[]{host.getString(R.string.h_pin_new), host.getString(R.string.h_pin_remove)};
            new ThemeDialogBuilder(host).setTitle(R.string.h_local_pin).setMessage(R.string.h_pin_note)
                    .setPositiveButton(R.string.h_pin_new, (d, w) -> set(host))
                    .setNeutralButton(choices.length == 2 ? host.getString(R.string.h_pin_remove) : host.getString(android.R.string.cancel),
                            (d, w) -> { if (choices.length == 2) prefs(host).edit().clear().apply(); }).show();
        });
    }
    private static void set(Activity host) {
        prompt(host, R.string.h_pin_new, (first, dialog, input) -> {
            if (!valid(first)) { input.setError(host.getString(R.string.h_pin_invalid)); return; }
            dialog.dismiss();
            prompt(host, R.string.h_pin_confirm, (second, confirm, field) -> {
                if (!first.equals(second)) { field.setError(host.getString(R.string.h_pin_mismatch)); return; }
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                WORKER.execute(() -> {
                    String hash;
                    try { hash = encode(second); } catch (Exception unavailable) { hash = null; }
                    final String encoded = hash;
                    host.runOnUiThread(() -> {
                        if (host.isDestroyed() || !confirm.isShowing()) return;
                        if (encoded != null) { prefs(host).edit().putString("hash", encoded).remove("failures").remove("retry_at").apply(); confirm.dismiss(); }
                        else { field.setError(host.getString(R.string.h_unavailable)); confirm.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); }
                    });
                });
            });
        });
    }
    private interface Submission { void accept(String pin, AlertDialog dialog, EditText input); }
    private static AlertDialog prompt(Activity host, int title, Submission submit) {
        EditText input = new EditText(host); input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        AlertDialog dialog = new ThemeDialogBuilder(host).setTitle(title).setView(input)
                .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> submit.accept(input.getText().toString(), dialog, input)));
        dialog.show();
        if(ThemeStyle.custom(ThemePreferences.get(host))) GlassSettings.styleDialog(host,dialog);
        return dialog;
    }
}
