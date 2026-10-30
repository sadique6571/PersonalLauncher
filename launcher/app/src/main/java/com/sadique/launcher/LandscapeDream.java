package com.sadique.launcher;

import android.service.dreams.DreamService;

/** System-managed online landscape screensaver. Any remote key returns to the previous app. */
public final class LandscapeDream extends DreamService {
    private LandscapeView landscape;
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); setFullscreen(true); setScreenBright(false);
        landscape = new LandscapeView(this);
        setContentView(landscape);
    }
    @Override public void onDreamingStarted() { super.onDreamingStarted(); landscape.start(); }
    @Override public void onDreamingStopped() { if (landscape != null) landscape.stop(); super.onDreamingStopped(); }
    @Override public void onDetachedFromWindow() { if (landscape != null) landscape.stop(); super.onDetachedFromWindow(); }
}
