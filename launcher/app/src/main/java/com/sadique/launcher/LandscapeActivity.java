package com.sadique.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** Manual preview; Android's screensaver picker uses LandscapeDream. */
public final class LandscapeActivity extends Activity {
    private LandscapeView landscape;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        landscape = new LandscapeView(this);
        landscape.setOnClickListener(view -> finish());
        setContentView(landscape);
    }
    @Override protected void onResume() { super.onResume(); landscape.start(); }
    @Override protected void onPause() { landscape.stop(); super.onPause(); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_ENTER) {
            finish(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
