package com.sadique.launcher;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Clean proportional marks for demo apps; other installed apps retain their own icon. */
final class DemoBrandMark extends Drawable {
    private final int kind; private final Paint ink=new Paint(3); private final Path path=new Path();
    private DemoBrandMark(int kind) { this.kind=kind; }
    static Drawable choose(String pkg,Drawable fallback) {
        int kind=pkg.equals("com.amazon.amazonvideo.livingroom")||pkg.equals("com.amazon.avod.thirdpartyclient")?1:
                pkg.equals("com.spotify.tv.android")||pkg.equals("com.spotify.music")?2:pkg.equals("org.videolan.vlc")?3:pkg.equals("com.android.vending")?4:0;
        return kind==0?fallback:new DemoBrandMark(kind);
    }
    @Override public int getIntrinsicWidth() { return 128; }
    @Override public int getIntrinsicHeight() { return 128; }
    private void polygon(Canvas c,int color,float... xy) {
        path.reset(); path.moveTo(xy[0],xy[1]); for(int i=2;i<xy.length;i+=2) path.lineTo(xy[i],xy[i+1]); path.close();
        ink.setColor(color); ink.setStyle(Paint.Style.FILL); c.drawPath(path,ink);
    }
    @Override public void draw(Canvas c) {
        Rect b=getBounds(); int save=c.save(); c.translate(b.left,b.top); c.scale(b.width()/128f,b.height()/128f);
        ink.setStyle(Paint.Style.FILL);
        if(kind==1) {
            ink.setColor(0xff087ccc); ink.setTypeface(Typeface.create("sans-serif",Typeface.BOLD)); ink.setTextAlign(Paint.Align.CENTER); ink.setTextSize(34);
            c.drawText("prime",64,44,ink); c.drawText("video",64,78,ink);
            ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(4); ink.setStrokeCap(Paint.Cap.ROUND);
            path.reset(); path.moveTo(22,91); path.quadTo(67,116,108,89); c.drawPath(path,ink);
            path.reset(); path.moveTo(96,90); path.lineTo(109,87); path.lineTo(106,100); c.drawPath(path,ink);
        } else if(kind==2) {
            ink.setColor(0xff1ed760); c.drawCircle(64,64,57,ink);
            ink.setColor(0xff10251c); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(8); ink.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<3;i++) { path.reset(); path.moveTo(28+i*4,43+i*20); path.quadTo(64,31+i*20,103-i*5,52+i*17); c.drawPath(path,ink); }
        } else if(kind==3) {
            polygon(c,0xffff8b00,20,108,56,15,71,15,108,108);
            polygon(c,0xfffffff5,49,33,79,33,85,49,43,49); polygon(c,0xfffffff5,36,67,92,67,99,86,29,86);
            ink.setColor(0xffff8b00); c.drawRoundRect(9,103,119,118,7,7,ink);
        } else {
            polygon(c,0xff33bff3,15,8,76,64,15,120);
            polygon(c,0xff31d58a,15,8,93,51,76,64);
            polygon(c,0xffffca3a,93,51,116,64,93,78,76,64);
            polygon(c,0xffff4c63,15,120,76,64,93,78);
        }
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
