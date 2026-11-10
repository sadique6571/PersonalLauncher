package com.sadique.launcher;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Bounded atmospheric weather scene: feathered cloud banks, mist and quiet day/night light. */
final class WeatherConditionView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds=new RectF(0,0,320,220);
    private final Runnable frame=this::invalidate;
    private final int frameMs;
    private Bitmap backdrop;
    private int code=-1;private boolean day=true,active,animation=true;
    private final Shader cloud=new RadialGradient(0,0,1,new int[]{0x88d9e6ef,0x55aabfcf,0x00aabfcf},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
    private final Shader mist=new LinearGradient(0,90,0,210,new int[]{0x00afc2d1,0x55afc2d1,0x00afc2d1},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
    WeatherConditionView(Context context){super(context);frameMs=MemoryProfile.effectiveTier(context,ThemePreferences.get(context))==MemoryProfile.TIER_LIGHT?125:66;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void condition(int next,boolean daytime){if(code!=next||day!=daytime){code=next;day=daytime;backdrop=null;}invalidate();}
    void active(boolean value,boolean animation){active=value;this.animation=animation;removeCallbacks(frame);invalidate();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)removeCallbacks(frame);else invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);backdrop=null;}
    private void makeBackdrop(){
        int width=Math.max(1,Math.min(512,getWidth())),height=Math.max(1,Math.round(width*220f/320));
        backdrop=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(backdrop);c.scale(width/320f,height/220f);
        int top=code<0?0xff152131:!day?0xff071529:code>=3?0xff294657:0xff206491;
        int bottom=code<0?0xff07111f:!day?0xff14283a:code>=3?0xff50646b:0xff8eafb7;
        paint.setShader(new LinearGradient(0,0,0,220,top,bottom,Shader.TileMode.CLAMP));c.drawRect(bounds,paint);paint.setShader(null);
        if(code>=0&&code<=3){
            if(day){
                paint.setShader(new RadialGradient(259,47,70,new int[]{0xffffe8a5,0x55ffdf9b,0x00ffdf9b},new float[]{0,.2f,1},Shader.TileMode.CLAMP));c.drawCircle(259,47,70,paint);
                paint.setShader(new RadialGradient(256,44,14,0xfffffff4,0xffffe9b6,Shader.TileMode.CLAMP));c.drawCircle(256,44,14,paint);
            }else{
                paint.setShader(new RadialGradient(258,44,37,0x55bddeff,0x00bddeff,Shader.TileMode.CLAMP));c.drawCircle(258,44,37,paint);
                paint.setShader(new RadialGradient(252,39,24,0xfff1f4ec,0xff9bb4bc,Shader.TileMode.CLAMP));c.drawCircle(257,44,17,paint);paint.setShader(null);
                paint.setColor(0x2262717e);c.drawCircle(251,40,4,paint);c.drawCircle(263,48,5,paint);c.drawCircle(255,52,2.5f,paint);
                paint.setColor(0x778ea7bf);for(int i=0;i<18;i++)c.drawCircle(12+(i*47)%300,8+(i*23)%94,i%4==0?.8f:.4f,paint);
            }
        }
        paint.setShader(null);paint.setColor(0xff162b37);Path ridge=new Path();ridge.moveTo(0,185);ridge.cubicTo(45,153,68,174,109,155);ridge.cubicTo(159,128,198,172,239,152);ridge.cubicTo(285,136,309,154,320,148);ridge.lineTo(320,220);ridge.lineTo(0,220);ridge.close();c.drawPath(ridge,paint);
        paint.setShader(new LinearGradient(0,0,0,220,new int[]{0x18030b17,0x55030b17,0xee030b17},new float[]{0,.48f,1},Shader.TileMode.CLAMP));c.drawRect(bounds,paint);paint.setShader(null);
    }
    private void cloud(Canvas c,float x,float y,float w,float h,int opacity){int save=c.save();c.translate(x,y);c.scale(w,h);paint.setShader(cloud);paint.setAlpha(opacity);c.drawCircle(0,0,1,paint);paint.setAlpha(255);paint.setShader(null);c.restoreToCount(save);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()<=0||getHeight()<=0)return;if(backdrop==null)makeBackdrop();
        int save=c.save();c.scale(getWidth()/320f,getHeight()/220f);paint.setAlpha(255);c.drawBitmap(backdrop,null,bounds,paint);
        float seconds=active&&animation?(SystemClock.uptimeMillis()%60000)/1000f:0;
        if(code>=1){
            float drift=(float)Math.sin(seconds*.1f)*15;int opacity=day?230:105;
            cloud(c,218+drift,72,117,25,opacity);cloud(c,266+drift,65,59,29,opacity);cloud(c,164+drift,82,80,18,opacity);
            if(code>=3){cloud(c,72-drift,51,127,30,opacity);cloud(c,36-drift,61,78,22,opacity);cloud(c,160-drift,35,88,26,opacity);}
        }
        if(code==45||code==48){paint.setShader(mist);c.drawRect(0,75,320,220,paint);paint.setShader(null);for(int i=0;i<3;i++)cloud(c,80+i*85+(float)Math.sin(seconds*.15f+i)*12,119+i*19,135,17,210);}
        if((code>=51&&code<=67)||(code>=80&&code<=82)||code>=95){paint.setColor(0x779cc4da);paint.setStrokeWidth(.65f);for(int i=0;i<24;i++){float x=(i*43)%330,y=((i*31+seconds*85)%190);c.drawLine(x,y,x-4,y+12,paint);}}
        if((code>=71&&code<=77)||code==85||code==86){paint.setColor(0xaaddedef);for(int i=0;i<20;i++){float x=(i*59)%320+(float)Math.sin(seconds*.4f+i)*5,y=(i*29+seconds*12)%210;c.drawCircle(x,y,i%3==0?1.4f:.7f,paint);}}
        if(code>=95){float flash=(float)Math.max(0,Math.sin(seconds*.7f)-.985f);paint.setColor(Color.WHITE);paint.setAlpha(Math.round(flash*1400));c.drawRect(bounds,paint);paint.setAlpha(255);}
        c.restoreToCount(save);
        if(active&&animation&&code>=0&&getWindowVisibility()==VISIBLE&&isShown()){removeCallbacks(frame);postDelayed(frame,frameMs);}
    }
}
