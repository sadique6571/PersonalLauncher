package com.sadique.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.*;
import android.widget.*;
import android.view.View;
import java.io.*;
import java.util.Locale;

/** Bounded measurements while Home is visible. No file reads or sampling sleeps on the UI thread. */
final class GlassTelemetry extends LinearLayout {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private volatile Handler sampling;
    private final TextView[] values=new TextView[3];
    private final ProgressBar[] bars=new ProgressBar[3];
    private long totalPrev=-1,idlePrev;
    private boolean cpuUnavailable;
    GlassTelemetry(Activity host) {
        super(host);setOrientation(VERTICAL);setId(R.id.statsCard);
        int pad=GlassSettings.dp(host,14);setPadding(pad,pad,pad,pad);
        setBackground(GlassOsUi.panel(host,22));
        LinearLayout heading=new LinearLayout(host);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        heading.addView(GlassSettings.label(host,"SYSTEM TELEMETRY",10,0xffa5b4cf),new LayoutParams(0,-2,1));
        heading.addView(GlassSettings.label(host,"● Live",10,0xff59e493));addView(heading);
        View rule=new View(host);rule.setBackgroundColor(0x18ffffff);LayoutParams rp=new LayoutParams(-1,GlassSettings.dp(host,1));rp.topMargin=GlassSettings.dp(host,8);addView(rule,rp);
        int[] ids={R.id.cpuVal,R.id.ramVal,R.id.memVal},progress={R.id.cpuProgress,R.id.ramProgress,R.id.memProgress};
        int[] colors={0xff38bdf8,0xff34d399,0xfff59e0b};String[] names={"CPU","RAM","MEMORY"};
        for(int i=0;i<3;i++) {
            LinearLayout row=new LinearLayout(host);row.setPadding(0,GlassSettings.dp(host,10),0,0);
            row.addView(GlassSettings.label(host,names[i],10,0xffc5cede),new LayoutParams(0,-2,1));
            values[i]=GlassSettings.label(host,"—",10,0xfff0f7ff);values[i].setId(ids[i]);row.addView(values[i]);addView(row);
            bars[i]=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bars[i].setId(progress[i]);bars[i].setMax(100);
            android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0x18ffffff);background.setCornerRadius(GlassSettings.dp(host,999));
            int[] ends={0xff818cf8,0xff10b981,0xffec4899};
            android.graphics.drawable.GradientDrawable gradient=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{colors[i],ends[i]});gradient.setCornerRadius(GlassSettings.dp(host,999));
            android.graphics.drawable.ClipDrawable fill=new android.graphics.drawable.ClipDrawable(gradient,android.view.Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL);
            android.graphics.drawable.LayerDrawable progress=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{background,fill});progress.setId(0,android.R.id.background);progress.setId(1,android.R.id.progress);bars[i].setProgressDrawable(progress);
            addView(bars[i],new LayoutParams(-1,GlassSettings.dp(host,7)));
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); // Measurements never steal remote focus.
    }
    void start() {
        if(thread!=null)return;
        totalPrev=-1;cpuUnavailable=false;
        thread=new HandlerThread("glass-stats");thread.start();Handler bg=new Handler(thread.getLooper());sampling=bg;
        bg.post(new Runnable(){@Override public void run(){
            if(sampling!=bg)return;
            int cpu=cpu();ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
            ActivityManager am=getContext().getSystemService(ActivityManager.class);if(am!=null)am.getMemoryInfo(memory);
            long total=Environment.getDataDirectory().getTotalSpace(),free=Environment.getDataDirectory().getFreeSpace();
            ui.post(()->{if(sampling!=bg)return;
                values[0].setText(cpu<0?"Unavailable":cpu+"%");bars[0].setProgress(Math.max(0,cpu));
                int ram=GlassOsMetrics.percent(memory.totalMem-memory.availMem,memory.totalMem);
                values[1].setText(memory.totalMem<=0?"Unavailable":String.format(Locale.getDefault(),"%d%% • %.1f / %.1f GB",ram,gb(memory.totalMem-memory.availMem),gb(memory.totalMem)));
                bars[1].setProgress(ram);
                int used=GlassOsMetrics.percent(total-free,total);values[2].setText(total<=0?"Unavailable":String.format(Locale.getDefault(),"%d%% • %.1f GB free",used,gb(free)));bars[2].setProgress(used);
            });
            if(sampling==bg)bg.postDelayed(this,3000);
        }});
    }
    void stop(){Handler bg=sampling;sampling=null;if(bg!=null)bg.removeCallbacksAndMessages(null);if(thread!=null){thread.quitSafely();thread=null;}ui.removeCallbacksAndMessages(null);}
    private static double gb(long n){return n/1073741824d;}
    private int cpu(){
        if(cpuUnavailable)return -1;
        try(BufferedReader r=new BufferedReader(new FileReader("/proc/stat"))){
            long[] ticks=GlassOsMetrics.cpuTicks(r.readLine());
            if(ticks==null){cpuUnavailable=true;return -1;}
            long total=ticks[0],idle=ticks[1];
            long delta=total-totalPrev,busy=delta-(idle-idlePrev);int value=totalPrev<0||delta<=0?-1:GlassOsMetrics.percent(busy,delta);
            totalPrev=total;idlePrev=idle;return value;
        }catch(IOException|SecurityException|NumberFormatException e){cpuUnavailable=true;return -1;}
    }
}
