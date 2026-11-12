package com.sadique.launcher;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Shows the user's exact self-contained V17 HTML/SVG watch as the fourth Harmony clock face.
 * The original canvas/SVG, gears, fork, hairspring, hands and physics scripts are unchanged;
 * only the external demo controls are hidden by asset CSS.
 *
 * WebView is created only when this face is selected. Content is packaged offline;
 * it cannot load remote network resources.
 */
final class HarmonyMechanicalHtmlView extends FrameLayout {
    private WebView watch;
    private boolean destroyed;

    HarmonyMechanicalHtmlView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Harmony Mechanical V17 clock. Press OK for watch face settings.");
        setClipChildren(false);
        setClipToPadding(false);
        watch=new WebView(context);
        watch.setBackgroundColor(Color.TRANSPARENT);
        watch.setFocusable(false);
        watch.setFocusableInTouchMode(false);
        watch.setVerticalScrollBarEnabled(false);
        watch.setHorizontalScrollBarEnabled(false);
        WebSettings settings=watch.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true); // required to load the app's bundled android_asset
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        watch.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                return !"file:///android_asset/harmony_mechanical_v17.html".equals(url);
            }
        });
        addView(watch,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        watch.loadUrl("file:///android_asset/harmony_mechanical_v17.html");
    }

    void active(boolean enabled) {
        if (destroyed || watch==null) return;
        if (enabled) watch.onResume();
        else watch.onPause();
    }

    void close() {
        if (destroyed) return;
        destroyed=true;
        if (watch!=null) {
            watch.onPause();
            watch.stopLoading();
            watch.loadUrl("about:blank");
            removeView(watch);
            watch.destroy();
            watch=null;
        }
    }
}
