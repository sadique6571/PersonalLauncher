package com.sadique.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Passive media-volume display. Existing remote/helper handling remains the only volume controller. */
final class GlassVolumeCapsule extends LinearLayout {
    private final Activity host;
    private final AudioManager audio;
    private final TextView percent;
    private final FrameLayout track;
    private final View fill;
    private final ImageView icon;
    private int accent=0xffe50914;
    private boolean registered;
    private float shown;
    private ValueAnimator animator;
    private final BroadcastReceiver changes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){update(true);}};
    GlassVolumeCapsule(Activity host) {
        super(host);this.host=host;audio=host.getSystemService(AudioManager.class);setId(R.id.volumeCapsule);
        setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(10),dp(10),dp(10),dp(10));
        setBackground(GlassOsUi.panel(host,999));
        percent=GlassSettings.label(host,"—",12,0xffffffff);percent.setId(R.id.volPercent);percent.setGravity(Gravity.CENTER);addView(percent,new LayoutParams(-1,dp(24)));
        track=new FrameLayout(host);track.setId(R.id.volTrack);GradientDrawable cavity=new GradientDrawable();cavity.setColor(0x18ffffff);cavity.setCornerRadius(dp(999));track.setBackground(cavity);track.setClipToOutline(true);
        fill=new View(host);fill.setId(R.id.volFill);track.addView(fill,new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM));
        icon=new ImageView(host);icon.setId(R.id.volIcon);icon.setImageDrawable(TvSymbols.icon(host,R.drawable.ic_volume));icon.setColorFilter(0xff64748b);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);track.addView(icon,ip);
        addView(track,new LayoutParams(dp(36),0,1));
        TextView label=GlassSettings.label(host,"VOL",9,0xffa5b4cf);label.setGravity(Gravity.CENTER);addView(label,new LayoutParams(-1,dp(22)));
        track.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->height(shown));accent(accent);update(false);
    }
    private int dp(float n){return GlassSettings.dp(host,n);}
    void accent(int color){accent=color;GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xffffffff,color});d.setCornerRadius(dp(999));fill.setBackground(d);}
    void start(){
        if(!registered)try{IntentFilter f=new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            if(Build.VERSION.SDK_INT>=33)host.registerReceiver(changes,f,Context.RECEIVER_NOT_EXPORTED);else host.registerReceiver(changes,f);registered=true;
        }catch(RuntimeException ignored){}update(false);
    }
    void stop(){if(registered){host.unregisterReceiver(changes);registered=false;}if(animator!=null)animator.cancel();animate().cancel();setScaleX(1);setScaleY(1);}
    private void height(float fraction){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)fill.getLayoutParams();int h=Math.round(track.getHeight()*fraction);if(p.height!=h){p.height=h;fill.setLayoutParams(p);}}
    private void update(boolean animate){
        if(audio==null){percent.setText("—");return;}
        int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),value=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean mute=audio.isStreamMute(AudioManager.STREAM_MUSIC)||value==0;
        float target=mute||max<=0?0:value/(float)max;
        percent.setText(mute?"MUTE":Math.round(target*100)+"%");
        icon.setImageDrawable(TvSymbols.icon(host,mute?R.drawable.ic_volume_muted:R.drawable.ic_volume_speaker));
        icon.setAlpha(mute?.45f:1);setContentDescription("Media volume "+percent.getText());
        if(animator!=null)animator.cancel();
        if(animate&&isShown()) {
            animator=ValueAnimator.ofFloat(shown,target);animator.setDuration(160);animator.addUpdateListener(a->{shown=(float)a.getAnimatedValue();height(shown);});animator.start();
            animate().cancel();setScaleX(1.04f);setScaleY(1.04f);animate().scaleX(1).scaleY(1).setDuration(180).start();
        }else{shown=target;height(shown);}
    }
}
