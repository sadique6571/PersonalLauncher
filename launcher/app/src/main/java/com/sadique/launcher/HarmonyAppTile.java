package com.sadique.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Harmony Vision-specific TV dock tile. Uses the application's untouched native icon
 * in a restrained square plate. No 16:9 generated branding, reflection, stretched icon,
 * widening span, or layout-changing focus animation.
 */
final class HarmonyAppTile extends FrameLayout {
    private static final PathInterpolator FOCUS_EASE = new PathInterpolator(.4f, 0f, .2f, 1f);
    private final FrameLayout plate;
    private final GradientDrawable dockPlate;
    private final ImageView icon;
    private final TextView name;
    private int accent;
    private final boolean dock;
    private boolean selected, launching, selectHeld;
    private final int plateSize;
    HarmonyAppTile(Context context,String label,Drawable artwork,int accent,int plateSizeDp,boolean dock){
        super(context);
        this.accent=accent;
        this.dock=dock;
        plateSize=dp(plateSizeDp);
        setClipChildren(false);setClipToPadding(false);
        setBackground(null);setForeground(null);
        setTooltipText(label);
        setContentDescription(label);
        if (dock) setDefaultFocusHighlightEnabled(false);
        plate=new FrameLayout(context);
        plate.setId(R.id.card);
        dockPlate = dock ? new GradientDrawable() : null;
        if (dockPlate != null) {
            dockPlate.setCornerRadius(dp(12));
            plate.setBackground(dockPlate);
        }
        plate.setClipToOutline(true);
        FrameLayout.LayoutParams pl=new FrameLayout.LayoutParams(plateSize,plateSize,
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        if (dock) pl.topMargin = dp(HarmonyVisionLayout.DOCK_HEADROOM_DP);
        addView(plate,pl);
        icon=new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad = dock ? Math.round(plateSize * .15f) : dp(9);
        icon.setPadding(pad,pad,pad,pad);
        plate.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        name=new TextView(context);
        name.setText(label);
        name.setTextSize(dock ? 14 : 11);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setShadowLayer(dp(3),0,dp(1),0xbf071a29);
        name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-1,dp(dock ? HarmonyVisionLayout.DOCK_CAPTION_HEIGHT_DP : 21),Gravity.BOTTOM);
        caption.leftMargin=dp(3);caption.rightMargin=dp(3);
        addView(name,caption);
        artwork(artwork,accent);
    }
    void launchEffect(Runnable open) {
        if (launching) return;
        launching = true;
        animate().scaleX(.94f).scaleY(.94f).setDuration(55).withEndAction(() ->
            animate().scaleX(1.045f).scaleY(1.045f).alpha(.76f).setDuration(105).withEndAction(() -> {
                resetLaunch();
                open.run();
            }).start()).start();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (dock && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BUTTON_A)) {
            boolean held = event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled();
            if (held != selectHeld) {
                selectHeld = held;
                updatePlateTransform(held ? 90 : 140);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDetachedFromWindow() {
        animate().cancel();
        resetLaunch();
        if (dock) {
            selectHeld = false;
            updatePlateTransform(0);
        }
        super.onDetachedFromWindow();
    }

    private void resetLaunch() {
        launching = false;
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
    }

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void artwork(Drawable image,int accent){
        this.accent=accent;
        icon.setImageDrawable(image==null?TvSymbols.icon(getContext(),R.drawable.ic_apps):image);
        appearance(isFocused());
    }
    void appearance(boolean focused) {
        if (!dock) {
            plate.setBackground(HarmonyVisionSurface.app(getContext(), accent, focused, dp(19)));
            name.setVisibility(focused ? VISIBLE : INVISIBLE);
            plate.setElevation(focused ? dp(7) : dp(2));
            return;
        }
        HarmonyVisionSurface.applyDockPlate(dockPlate, getContext(), focused);
        name.setTextColor(focused ? Color.WHITE : 0x99ffffff);
        plate.setElevation(focused ? dp(7) : dp(2));
        if (selected != focused) {
            selected = focused;
            if (!focused) selectHeld = false;
            updatePlateTransform(focused ? 220 : 160);
        }
    }

    private void updatePlateTransform(int duration) {
        float scale = selected ? HarmonyVisionLayout.DOCK_FOCUS_SCALE : 1f;
        if (selectHeld) scale *= .96f;
        float lift = selected ? -dp(HarmonyVisionLayout.DOCK_FOCUS_LIFT_DP) : 0f;
        plate.animate().cancel();
        if (duration == 0 || MemoryProfile.effectiveTier(getContext(), ThemePreferences.get(getContext()))
                == MemoryProfile.TIER_LIGHT) {
            plate.setScaleX(scale);
            plate.setScaleY(scale);
            plate.setTranslationY(lift);
            return;
        }
        plate.animate().scaleX(scale).scaleY(scale).translationY(lift)
                .setDuration(duration).setInterpolator(FOCUS_EASE).start();
    }
}
