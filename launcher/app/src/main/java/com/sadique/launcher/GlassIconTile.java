package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Fixed mildly wide artwork and an external caption. Focus changes only the edge. */
final class GlassIconTile extends FrameLayout {
    private final FrameLayout body;
    private final ImageView logo;
    private final TextView caption;
    private final int preferredHeight,captionHeight,radius;
    private final boolean moving;
    GlassIconTile(Context context,String name,Drawable artwork,int accent,int height,int labelHeight,int radius,boolean moving){
        super(context);preferredHeight=height;captionHeight=labelHeight;this.radius=radius;this.moving=moving;
        setClipChildren(false);setClipToPadding(false);setBackground(null);setForeground(null);setTooltipText(name);
        body=new FrameLayout(context);body.setClipToOutline(true);body.setId(R.id.card);body.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);addView(body,new LayoutParams(-1,height));
        logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);int inset=dp(8);logo.setPadding(inset,inset,inset,inset);body.addView(logo,new LayoutParams(-1,-1));
        SharedPreferences prefs=ThemePreferences.get(context);int size=Math.max(12,Math.min(22,prefs.getInt("app_label_size",16)));
        caption=new TextView(context);caption.setText(name);caption.setTextSize(size);caption.setTextColor(Color.WHITE);caption.setTypeface(Typeface.create(ThemeStyle.fontFamily(prefs),Typeface.BOLD));caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);caption.setShadowLayer(dp(3),0,dp(1),0xee000000);caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(caption,new LayoutParams(-1,labelHeight));
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline out){out.setRoundRect(body.getLeft(),body.getTop(),body.getRight(),body.getBottom(),radius);}});
        artwork(artwork,accent);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void artwork(Drawable artwork,int accent){logo.setImageDrawable(artwork==null?TvSymbols.icon(getContext(),R.drawable.ic_apps):artwork);appearance(accent,isFocused()||moving);}
    void appearance(int accent,boolean focused){body.setBackground(GlassAppCard.surface(getContext(),accent,focused||moving,radius));invalidate();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=resolveSize(Math.round(preferredHeight*1.45f),widthSpec),height=resolveSize(preferredHeight+captionHeight,heightSpec);setMeasuredDimension(width,height);
        int bodyHeight=Math.min(preferredHeight,Math.max(1,Math.round(width/1.45f))),bodyWidth=GlassDockLayout.wideBodyWidth(width,bodyHeight);
        body.measure(MeasureSpec.makeMeasureSpec(bodyWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(bodyHeight,MeasureSpec.EXACTLY));
        caption.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,captionHeight-dp(6)),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int x=(getWidth()-body.getMeasuredWidth())/2,y=(preferredHeight-body.getMeasuredHeight())/2;body.layout(x,y,x+body.getMeasuredWidth(),y+body.getMeasuredHeight());
        caption.layout(0,preferredHeight+dp(6),getWidth(),Math.min(getHeight(),preferredHeight+captionHeight));invalidateOutline();
    }
}
