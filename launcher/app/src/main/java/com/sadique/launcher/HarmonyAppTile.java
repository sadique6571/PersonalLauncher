package com.sadique.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Harmony Vision-specific TV dock tile. Uses the application's untouched native icon
 * in a restrained square plate. No 16:9 generated branding, reflection, stretched icon,
 * widening span, or layout-changing focus animation.
 */
final class HarmonyAppTile extends FrameLayout {
    private final FrameLayout plate;
    private final ImageView icon;
    private final TextView name;
    private int accent;
    private final boolean dock;
    private boolean selected, launching;
    private final int plateSize;
    HarmonyAppTile(Context context,String label,Drawable artwork,int accent,int plateSizeDp,boolean dock){
        super(context);
        this.accent=accent;
        this.dock=dock;
        plateSize=dp(plateSizeDp);
        setClipChildren(false);setClipToPadding(false);
        setBackground(null);setForeground(null);
        setTooltipText(label);
        plate=new FrameLayout(context);
        plate.setId(R.id.card);
        plate.setClipToOutline(true);
        FrameLayout.LayoutParams pl=new FrameLayout.LayoutParams(plateSize,plateSize,
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        if (dock) pl.topMargin = dp(HarmonyVisionLayout.DOCK_HEADROOM_DP);
        addView(plate,pl);
        icon=new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad=dp(9);
        icon.setPadding(pad,pad,pad,pad);
        plate.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        name=new TextView(context);
        name.setText(label);
        name.setTextSize(11);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setShadowLayer(dp(3),0,dp(1),0xbf071a29);
        name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-1,dp(21),Gravity.BOTTOM);
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

    @Override protected void onDetachedFromWindow() {
        animate().cancel();
        resetLaunch();
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
    void appearance(boolean selected){
        plate.setBackground(HarmonyVisionSurface.app(getContext(),accent,selected,dp(19)));
        name.setVisibility(selected?VISIBLE:INVISIBLE);
        plate.setElevation(selected?dp(7):dp(2));
        if (dock && this.selected != selected) {
            plate.animate().cancel();
            float scale = selected ? HarmonyVisionLayout.DOCK_FOCUS_SCALE : 1f;
            float lift = selected ? -dp(HarmonyVisionLayout.DOCK_FOCUS_LIFT_DP) : 0f;
            plate.animate().scaleX(scale).scaleY(scale).translationY(lift).setDuration(160).start();
        }
        this.selected = selected;
        invalidate();
    }
}
