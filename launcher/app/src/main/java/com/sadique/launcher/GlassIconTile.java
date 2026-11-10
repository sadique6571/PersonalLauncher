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

/** Clean Icons: square artwork with an external caption; only selection becomes a wide card. */
final class GlassIconTile extends FrameLayout {
    private final FrameLayout body;
    private final ImageView logo;
    private final TextView caption,title;
    private final int preferredSide,captionHeight,radius;
    private final boolean moving;
    private boolean expanded;
    private int accent;
    GlassIconTile(Context context,String name,Drawable artwork,int accent,int side,int labelHeight,int radius,boolean moving){
        super(context);this.preferredSide=side;captionHeight=labelHeight;this.radius=radius;this.moving=moving;this.accent=accent;
        setClipChildren(false);setClipToPadding(false);setBackground(null);setForeground(null);
        body=new FrameLayout(context);body.setClipToOutline(true);body.setId(R.id.card);body.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(body,new LayoutParams(side,side));
        logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);body.addView(logo,new LayoutParams(-1,-1));
        SharedPreferences prefs=ThemePreferences.get(context);int size=Math.max(12,Math.min(22,prefs.getInt("app_label_size",16)));
        caption=label(context,name,size);caption.setSingleLine(true);caption.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);addView(caption,new LayoutParams(-1,labelHeight));
        title=label(context,name,size+4);title.setGravity(Gravity.CENTER_VERTICAL);title.setMaxLines(2);title.setAutoSizeTextTypeUniformWithConfiguration(12,Math.min(26,size+4),1,android.util.TypedValue.COMPLEX_UNIT_SP);body.addView(title,new LayoutParams(-1,-1));
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline out){out.setRoundRect(body.getLeft(),body.getTop(),body.getRight(),body.getBottom(),radius);}});
        artwork(artwork,accent);appearance(accent,moving);
    }
    private TextView label(Context context,String value,int size){TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setTypeface(Typeface.create(ThemeStyle.fontFamily(ThemePreferences.get(context)),Typeface.BOLD));t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setShadowLayer(dp(3),0,dp(1),0xee000000);return t;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void artwork(Drawable artwork,int tint){logo.setImageDrawable(artwork==null?TvSymbols.icon(getContext(),R.drawable.ic_apps):artwork);appearance(tint,isFocused()||moving);}
    void appearance(int tint,boolean focused){
        accent=tint;boolean next=focused&&!moving,changed=expanded!=next;expanded=next;
        body.setBackground(GlassAppCard.surface(getContext(),accent,focused||moving,radius));
        caption.setVisibility(expanded?INVISIBLE:VISIBLE);title.setVisibility(expanded?VISIBLE:GONE);
        if(changed)requestLayout();invalidate();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=resolveSize(preferredSide,widthSpec),height=resolveSize(preferredSide+captionHeight,heightSpec);
        setMeasuredDimension(width,height);int side=Math.min(preferredSide,Math.max(1,width));
        int bodyWidth=expanded?width:side;
        LayoutParams lp=(LayoutParams)logo.getLayoutParams();int inset=dp(expanded?10:7);
        int logoSide=expanded?Math.max(1,Math.round(side*.72f)):Math.max(1,side-inset*2);
        lp.width=logoSide;lp.height=logoSide;lp.gravity=expanded?Gravity.START|Gravity.CENTER_VERTICAL:Gravity.CENTER;lp.setMarginStart(expanded?inset:0);lp.resolveLayoutDirection(getLayoutDirection());
        LayoutParams tp=(LayoutParams)title.getLayoutParams();tp.width=-1;tp.height=-1;tp.setMarginStart(logoSide+inset+dp(12));tp.setMarginEnd(dp(12));tp.topMargin=dp(8);tp.bottomMargin=dp(8);tp.resolveLayoutDirection(getLayoutDirection());
        body.measure(MeasureSpec.makeMeasureSpec(bodyWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(side,MeasureSpec.EXACTLY));
        caption.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,captionHeight-dp(6)),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int x=expanded?0:(getWidth()-body.getMeasuredWidth())/2,y=(preferredSide-body.getMeasuredHeight())/2;
        body.layout(x,y,x+body.getMeasuredWidth(),y+body.getMeasuredHeight());
        caption.layout(0,preferredSide+dp(6),getWidth(),Math.min(getHeight(),preferredSide+captionHeight));invalidateOutline();
    }
}
