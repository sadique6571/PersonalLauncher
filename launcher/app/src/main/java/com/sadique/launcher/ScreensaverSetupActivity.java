package com.sadique.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Android owns the selected dream; normal app permissions cannot replace it silently. */
public final class ScreensaverSetupActivity extends Activity {
    private TextView state;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        int padding = GlassSettings.dp(this, 24); page.setPadding(padding, padding, padding, padding);
        page.setBackgroundColor(0xff071426);
        page.addView(GlassSettings.label(this, "Automatic landscape screensaver", 24, 0xffffffff));
        state = GlassSettings.label(this, "", 15, 0xffa9cfe2); page.addView(state);
        page.addView(GlassSettings.label(this,
                "In Android screensaver settings, choose Personal Launcher Landscapes, enable automatic start, and choose the idle timeout. The old screensaver stays active until Android's selection changes. This is a system selection, not a normal permission prompt.", 16, 0xffd9e8f4));
        page.addView(GlassSettings.row(this, "Select in Android settings", "Choose Personal Launcher Landscapes", R.drawable.ic_settings, 0xff40d9ff, () -> {
            try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
            catch (RuntimeException unavailable) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (RuntimeException missing) { Toast.makeText(this, "This device does not expose screensaver settings", Toast.LENGTH_LONG).show(); }
            }
        }));
        page.addView(GlassSettings.row(this, "Start after 5 minutes on Home", "Home-only fallback if your TV restricts system screensavers", R.drawable.ic_picture, 0xff40d9ff, () -> {
            getSharedPreferences("personal_home", 0).edit().putInt("idle_screensaver_minutes", 5).apply();
            Toast.makeText(this, "Home idle slideshow enabled after 5 minutes", Toast.LENGTH_LONG).show();
        }));
        page.addView(GlassSettings.row(this, "Preview landscapes", "Preview does not change Android's selection", R.drawable.ic_picture, 0xff40d9ff,
                () -> startActivity(new Intent(this, LandscapeActivity.class))));
        setContentView(page);
    }
    @Override protected void onResume() {
        super.onResume();
        try {
            String components = Settings.Secure.getString(getContentResolver(), "screensaver_components");
            boolean selected = false;
            if (components != null) for (String value : components.split(",")) {
                ComponentName component = ComponentName.unflattenFromString(value.trim());
                if (new ComponentName(this, LandscapeDream.class).equals(component)) selected = true;
            }
            int enabled = Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0);
            state.setText(selected ? enabled != 0 ? "Android selection: Personal Launcher Landscapes · enabled"
                    : "Landscapes selected · enable screensaver in Android settings" : "Android has not selected Personal Launcher Landscapes");
        } catch (RuntimeException restricted) { state.setText("Check the selected screensaver in Android settings; this TV restricts reading its selection."); }
    }
}
