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

/** Mildly wide at rest, wider on focus. Every child is measured inside the actual current bounds. */
final class GlassIconTile extends FrameLayout {
    private final View panel;
    private final ImageView logo;
    private final TextView caption,title;
    private final int preferredHeight,captionHeight,radius;
    private final boolean moving;
    private boolean focused,inline;
    private int inset;
    GlassIconTile(Context context,String name,Drawable artwork,int accent,int height,int labelHeight,int radius,boolean moving){
        super(context);preferredHeight=height;captionHeight=labelHeight;this.radius=radius;this.moving=moving;
        setClipChildren(false);setClipToPadding(false);setBackground(null);setForeground(null);setTooltipText(name);
        panel=new View(context);panel.setId(R.id.card);panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(panel,new LayoutParams(-1,height));
        logo=new ImageView(context);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(logo,new LayoutParams(-1,height));
        SharedPreferences prefs=ThemePreferences.get(context);int size=Math.max(12,Math.min(22,prefs.getInt("app_label_size",16)));
        caption=label(context,name,size);caption.setSingleLine(true);caption.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);addView(caption,new LayoutParams(-1,labelHeight));
        title=label(context,name,size+3);title.setMaxLines(2);title.setGravity(Gravity.CENTER_VERTICAL);title.setAutoSizeTextTypeUniformWithConfiguration(12,Math.min(25,size+3),1,android.util.TypedValue.COMPLEX_UNIT_SP);addView(title,new LayoutParams(-1,height));
        setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline out){out.setRoundRect(panel.getLeft(),panel.getTop(),panel.getRight(),panel.getBottom(),radius);}});
        artwork(artwork,accent);
    }
    private TextView label(Context c,String name,int size){TextView t=new TextView(c);t.setText(name);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setTypeface(Typeface.create(ThemeStyle.fontFamily(ThemePreferences.get(c)),Typeface.BOLD));t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setShadowLayer(dp(3),0,dp(1),0xee000000);t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);return t;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void artwork(Drawable artwork,int accent){logo.setImageDrawable(artwork==null?TvSymbols.icon(getContext(),R.drawable.ic_apps):artwork);appearance(accent,isFocused()||moving);}
    void appearance(int accent,boolean selected){boolean next=selected&&!moving;if(focused!=next){focused=next;requestLayout();}panel.setBackground(GlassAppCard.surface(getContext(),accent,selected||moving,radius));invalidate();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=resolveSize(Math.round(preferredHeight*1.45f),widthSpec),height=resolveSize(preferredHeight+captionHeight,heightSpec);setMeasuredDimension(width,height);
        int bodyHeight=Math.min(preferredHeight,Math.max(1,height-captionHeight));
        int compactWidth=GlassDockLayout.wideBodyWidth(width,bodyHeight),bodyWidth=focused?width:compactWidth;
        inset=Math.min(dp(12),Math.max(dp(8),radius/2+1));inset=Math.min(inset,Math.max(0,(Math.min(bodyWidth,bodyHeight)-1)/3));
        inline=focused&&bodyWidth>=Math.round(bodyHeight*1.45f*1.32f)&&bodyWidth>=dp(130);
        int logoWidth=inline?Math.min(bodyHeight-inset*2,Math.round(bodyHeight*.78f)):bodyWidth-inset*2;
        int logoHeight=Math.max(1,bodyHeight-inset*2);logoWidth=Math.max(1,logoWidth);
        panel.measure(MeasureSpec.makeMeasureSpec(bodyWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(bodyHeight,MeasureSpec.EXACTLY));
        logo.measure(MeasureSpec.makeMeasureSpec(logoWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(logoHeight,MeasureSpec.EXACTLY));
        title.setVisibility(inline?VISIBLE:GONE);caption.setVisibility(inline?INVISIBLE:VISIBLE);
        title.measure(MeasureSpec.makeMeasureSpec(Math.max(1,bodyWidth-logoWidth-inset*3),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(logoHeight,MeasureSpec.EXACTLY));
        caption.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,captionHeight-dp(6)),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int x=(getWidth()-panel.getMeasuredWidth())/2,y=(preferredHeight-panel.getMeasuredHeight())/2;panel.layout(x,y,x+panel.getMeasuredWidth(),y+panel.getMeasuredHeight());
        int logoX=inline?x+inset:x+(panel.getMeasuredWidth()-logo.getMeasuredWidth())/2;
        logo.layout(logoX,y+inset,logoX+logo.getMeasuredWidth(),y+inset+logo.getMeasuredHeight());
        int textX=logoX+logo.getMeasuredWidth()+inset;title.layout(textX,y+inset,textX+title.getMeasuredWidth(),y+inset+title.getMeasuredHeight());
        caption.layout(0,preferredHeight+dp(6),getWidth(),Math.min(getHeight(),preferredHeight+captionHeight));invalidateOutline();
    }
}
