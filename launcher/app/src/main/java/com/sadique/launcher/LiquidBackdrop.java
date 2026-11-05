package com.sadique.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.view.View;
import java.util.concurrent.Executor;

/** Captures only wallpaper, never app icons or profile data. One <=256px cached image for both panels. */
final class LiquidBackdrop {
    private final View source;
    private final java.util.ArrayList<java.lang.ref.WeakReference<View>> targets = new java.util.ArrayList<>();
    private final Executor worker;
    private final Handler main;
    private final int[] targetPoint = new int[2], rootPoint = new int[2];
    private Bitmap blurred;
    private boolean closed;
    private int epoch;
    private final View.OnLayoutChangeListener layout = (v,l,t,r,b,ol,ot,or,ob) -> align();
    private final View.OnLayoutChangeListener sourceLayout = (v,l,t,r,b,ol,ot,or,ob) -> {
        if (r-l != or-ol || b-t != ob-ot) refresh();
    };
    private final Runnable capture = this::capture;

    LiquidBackdrop(View source, Executor worker, Handler main, View... targets) {
        this.source=source; this.worker=worker; this.main=main;
        for (View target : targets) bind(target);
        source.addOnLayoutChangeListener(sourceLayout);
        refresh();
    }
    void bind(View target) {
        if (closed) return;
        targets.add(new java.lang.ref.WeakReference<>(target)); target.addOnLayoutChangeListener(layout);
        target.post(this::align);
    }
    void refresh() {
        if (closed) return;
        epoch++; main.removeCallbacks(capture); main.postDelayed(capture, 100);
    }
    private void capture() {
        if (closed || !source.isAttachedToWindow() || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        int generation=epoch;
        android.content.SharedPreferences prefs=source.getContext().getSharedPreferences("personal_home",0);
        int radius=GlassTransparency.blurRadius(TvGlassStyle.finish(prefs),TvGlassStyle.intensity(prefs));
        float scale=256f / Math.max(source.getWidth(), source.getHeight());
        int w=Math.max(1,Math.round(source.getWidth()*scale)), h=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap sample;
        try { sample=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); }
        catch (RuntimeException | OutOfMemoryError unavailable) { return; }
        try {
            Canvas canvas=new Canvas(sample); canvas.drawColor(0xff031126); canvas.scale(scale,scale);
            // Include Home's wallpaper overscan, without sampling any foreground UI.
            canvas.concat(source.getMatrix()); source.draw(canvas);
        } catch (RuntimeException | OutOfMemoryError unavailable) { sample.recycle(); return; }
        worker.execute(() -> {
            if (Thread.currentThread().isInterrupted()) { sample.recycle(); return; }
            try {
                int[] pixels=new int[w*h]; sample.getPixels(pixels,0,w,0,0,w,h);
                GlassBlur.blur(pixels,w,h,radius); sample.setPixels(pixels,0,w,0,0,w,h);
            } catch (RuntimeException | OutOfMemoryError unavailable) { sample.recycle(); return; }
            main.post(() -> {
                if (closed || generation!=epoch) { sample.recycle(); return; }
                blurred=sample; align();
            });
        });
    }
    private void align() {
        if (closed || blurred==null || !(source.getParent() instanceof View)) return;
        ((View) source.getParent()).getLocationOnScreen(rootPoint);
        for (java.lang.ref.WeakReference<View> reference : targets) {
            View target=reference.get(); if (target==null) continue;
            if (!(target.getBackground() instanceof GlassSurface)) continue;
            target.getLocationOnScreen(targetPoint);
            ((GlassSurface) target.getBackground()).backdrop(blurred, source.getWidth(), source.getHeight(),
                    targetPoint[0]-rootPoint[0], targetPoint[1]-rootPoint[1]);
        }
    }
    void close() {
        closed=true; epoch++; main.removeCallbacks(capture);
        source.removeOnLayoutChangeListener(sourceLayout);
        for (java.lang.ref.WeakReference<View> reference : targets) {
            View target=reference.get(); if (target!=null) target.removeOnLayoutChangeListener(layout);
        }
        targets.clear();
        // Existing drawables own the previous thumbnail until Android releases that view tree.
        blurred=null;
    }
}

