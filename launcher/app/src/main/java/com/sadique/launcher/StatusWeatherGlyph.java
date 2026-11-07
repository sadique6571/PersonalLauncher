package com.sadique.launcher;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Small factual condition glyph; unavailable weather never shows a guessed condition. */
final class StatusWeatherGlyph extends Drawable {
    private final Paint ink=new Paint(3); private final Path moon=new Path(); private final int kind,color;
    StatusWeatherGlyph(String weather,int hour,int color) {
        String s=weather.toLowerCase(java.util.Locale.ROOT); this.color=color;
        kind=s.contains("cloud")||s.contains("rain")||s.contains("snow")||s.contains("fog")||s.contains("overcast")?2:(hour<6||hour>=18?1:0);
        moon.addCircle(12,12,8,Path.Direction.CW); Path cut=new Path(); cut.addCircle(16,8,8,Path.Direction.CW); moon.op(cut,Path.Op.DIFFERENCE);
    }
    @Override public void draw(Canvas c) {
        int save=c.save(); Rect b=getBounds(); c.translate(b.left,b.top); c.scale(b.width()/24f,b.height()/24f);
        ink.setColor(color); ink.setStyle(Paint.Style.FILL);
        if(kind==1) c.drawPath(moon,ink);
        else if(kind==2) { c.drawCircle(8,13,5,ink); c.drawCircle(13,10,6,ink); c.drawCircle(18,14,4,ink); c.drawRect(7,13,19,18,ink); }
        else { c.drawCircle(12,12,5,ink); ink.setStrokeWidth(1.7f); for(int i=0;i<8;i++) { double a=i*Math.PI/4; c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*11,12+(float)Math.sin(a)*11,ink); } }
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { ink.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { ink.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
