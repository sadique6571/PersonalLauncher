package com.sadique.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Native, scalable Harmony Vision analog watch face. No WebView, bitmap frame allocation,
 * network request or animation service. Repaints only while Home is resumed and visible.
 */
final class HarmonyVisionClockView extends View {
    static final int HERITAGE=0, CHRONOGRAPH=1, SKELETON=2, MECHANICAL=3;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF arc=new RectF();
    private final Path hand=new Path();
    private final Path mechanismPath=new Path();
    private long physicsNano;
    private double balancePosition=.34, balanceSpeed, lastBalanceSign=1;
    private long escrowSteps;
    private final Calendar now=Calendar.getInstance();
    private final SimpleDateFormat date=new SimpleDateFormat("EEE, MMM d",Locale.ENGLISH);
    private final Runnable clockFrame=new Runnable() {
        @Override public void run() {
            if(!running||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE||!isShown())return;
            invalidate();
            schedule();
        }
    };
    private Shader faceGradient,bezelGradient;
    private int face,finish,accentIndex,ink,muted,accent,faceCenter,faceEdge;
    private boolean smooth=true,hour24;
    private boolean running,light;
    private String digitalTime="";
    private int lastDisplayMinute=-1;
    private long lastHour=-1;
    private String shortDate="";
    HarmonyVisionClockView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Harmony Vision analog clock. Open clock customization.");
        configure(ThemePreferences.get(context));
    }
    void configure(SharedPreferences prefs) {
        face=HarmonyVisionClockMath.clampFace(prefs.getInt("harmony_clock_face",HERITAGE));
        finish=Math.max(0,Math.min(3,prefs.getInt("harmony_clock_finish",0)));
        accentIndex=Math.max(0,Math.min(2,prefs.getInt("harmony_clock_accent",0)));
        smooth=prefs.getBoolean("harmony_clock_sweep",true);
        hour24=prefs.getBoolean("time_24h",false);
        lastDisplayMinute=-1;
        physicsNano=0L;
        light=finish==3;
        ink=light?0xff1c3046:0xfff6fbff;
        muted=light?0xff6a7d90:0xff97b1c6;
        accent=new int[]{0xff8cddff,0xffe8c278,0xffef98ae}[accentIndex];
        int[] center={0xff173d5d,0xff0b0d14,0xff242c37,0xfff8fbff};
        int[] edge={0xff051426,0xff020305,0xff080c15,0xffd9e5ef};
        faceCenter=center[finish];faceEdge=edge[finish];
        int bezelTop=new int[]{0xff7cadca,0xff777b83,0xffe0e5ec,0xffffffff}[finish];
        int bezelEnd=new int[]{0xff122f4c,0xff11151e,0xff4c596b,0xff9db0c0}[finish];
        bezelGradient=new LinearGradient(-180,-180,160,180,bezelTop,bezelEnd,Shader.TileMode.CLAMP);
        faceGradient=new RadialGradient(-58,-72,290,faceCenter,faceEdge,Shader.TileMode.CLAMP);
        invalidate();schedule();
    }
    void active(boolean active) {
        running=active;
        removeCallbacks(clockFrame);
        if(active){invalidate();schedule();}
    }
    private void schedule(){
        removeCallbacks(clockFrame);
        if(!running||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE||!isShown())return;
        // 25 fps looks smooth on low-memory TVs without an always-on 60 fps renderer.
        long delay=smooth?40:Math.max(30,1000-System.currentTimeMillis()%1000);
        postDelayed(clockFrame,delay);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedule();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(clockFrame);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);schedule();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side=Math.min(getWidth(),getHeight())-getResources().getDisplayMetrics().density*2;
        if(side<=0)return;
        canvas.save();
        canvas.translate(getWidth()/2f,getHeight()/2f);
        canvas.scale(side/400f,side/400f);
        disk(canvas);
        marks(canvas);
        now.setTimeInMillis(System.currentTimeMillis());
        int h=now.get(Calendar.HOUR_OF_DAY),m=now.get(Calendar.MINUTE),
                s=now.get(Calendar.SECOND),ms=now.get(Calendar.MILLISECOND);
        if(lastHour!=now.get(Calendar.DAY_OF_YEAR)) {
            lastHour=now.get(Calendar.DAY_OF_YEAR);
            shortDate=date.format(now.getTime()).toUpperCase(Locale.ENGLISH);
        }
        if(face==HERITAGE) heritage(canvas);
        else if(face==CHRONOGRAPH) chronograph(canvas);
        else if(face==SKELETON) skeleton(canvas);
        else mechanical(canvas);
        int displayMinute=h*60+m;
        if(lastDisplayMinute!=displayMinute){
            lastDisplayMinute=displayMinute;
            int displayHour=hour24?h:((h+11)%12+1);
            digitalTime=String.format(Locale.ENGLISH,"%02d:%02d",displayHour,m);
            if(!hour24)digitalTime+=h<12?" AM":" PM";
        }
        label(canvas,digitalTime,0,120,10,accent,true);
        double minutes=m+(s+ms/1000d)/60d;
        drawHand(canvas,(float)HarmonyVisionClockMath.hourAngle(h,m,s,ms),104,8,light?0xff243a50:0xffe5f2ff);
        drawHand(canvas,(float)HarmonyVisionClockMath.minuteAngle(m,s,ms),145,5,light?0xff233c53:0xfff7fbff);
        drawSecond(canvas,(float)HarmonyVisionClockMath.secondAngle(s,ms,smooth));
        fill(canvas,0xff061523);canvas.drawCircle(0,0,10,p);
        fill(canvas,accent);canvas.drawCircle(0,0,6.3f,p);
        fill(canvas,ink);canvas.drawCircle(0,0,2.3f,p);
        canvas.restore();
    }
    private void fill(Canvas c,int color){p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);p.setAlpha(255);}
    private void stroke(int color,float width) {
        p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
    }
    private void disk(Canvas c) {
        p.setShader(null);fill(c,0x90000000);c.drawCircle(0,5,198,p);
        p.setShader(bezelGradient);c.drawCircle(0,0,196,p);p.setShader(null);
        stroke(light?0x66ffffff:0xff78b9d5,1.8f);c.drawCircle(0,0,194,p);
        fill(c,light?0xffe2e9ef:0xff07101b);c.drawCircle(0,0,187,p);
        p.setShader(faceGradient);p.setStyle(Paint.Style.FILL);c.drawCircle(0,0,181,p);p.setShader(null);
        stroke(light?0x708199a8:0x888dbed7,1.25f);c.drawCircle(0,0,175,p);
        stroke(light?0x33647f95:0x335a91b4,1);c.drawCircle(0,0,139,p);
        if(isFocused()) {stroke(accent,3);c.drawCircle(0,0,196,p);}
    }
    private void marks(Canvas c) {
        for(int i=0;i<60;i++){
            float a=(float)Math.toRadians(i*6-90);
            boolean hour=i%5==0,quarter=i%15==0;
            float inner=quarter?158:hour?163:170,outer=176;
            stroke(quarter?accent:hour?ink:muted,quarter?3.1f:hour?1.8f:.75f);
            c.drawLine((float)Math.cos(a)*inner,(float)Math.sin(a)*inner,
                    (float)Math.cos(a)*outer,(float)Math.sin(a)*outer,p);
        }
        for(int i=1;i<=12;i++){
            float angle=(float)Math.toRadians(i*30-90);
            label(c,Integer.toString(i),Math.round(Math.cos(angle)*146),
                    Math.round(Math.sin(angle)*146+6),i%3==0?21:17,ink,true);
        }
    }
    private void label(Canvas c,String text,float x,float y,float size,int color,boolean bold){
        fill(c,color);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(text,x,y,p);
    }
    private void heritage(Canvas c) {
        label(c,"H A R M O N Y",0,-76,12,ink,true);
        label(c,"V I S I O N   C H R O N O M E T E R",0,-55,6.2f,muted,false);
        stroke(accent,1.6f);c.drawLine(-36,-45,36,-45,p);
        label(c,shortDate,0,89,10,muted,true);
        label(c,"AUTOMATIC  ·  24 JEWELS",0,106,7,muted,false);
    }
    private void chronograph(Canvas c) {
        label(c,"HARMONY VISION",0,-80,11,ink,true);
        subDial(c,-65,53,"24 H",now.get(Calendar.HOUR_OF_DAY),24);
        subDial(c,65,53,"SEC",now.get(Calendar.SECOND),60);
        label(c,"CHRONOGRAPH",0,101,8,accent,true);
    }
    private void subDial(Canvas c,float x,float y,String title,int value,int total) {
        stroke(light?0x77476887:0x997198b4,1.3f);c.drawCircle(x,y,37,p);
        stroke(accent,2.2f);arc.set(x-33,y-33,x+33,y+33);
        c.drawArc(arc,-90,(Math.max(0,value)*360f)/total,false,p);
        label(c,title,x,y-12,8,muted,true);
        label(c,String.format(Locale.ENGLISH,"%02d",value),x,y+13,20,ink,true);
    }
    private void skeleton(Canvas c) {
        // Stationary mechanical-inspired geometry. No fictional active mechanism or sensor data.
        for(int j=0;j<3;j++){
            float x=j==0?-66:j==1?68:0,y=j==0?45:j==1?45:-83;
            int teeth=j==2?12:16;float radius=j==2?32:43;
            stroke(light?0x5560798e:0x8869b8ce,1.3f);c.drawCircle(x,y,radius,p);
            for(int i=0;i<teeth;i++){
                double a=i*Math.PI*2/teeth;
                stroke(i%2==0?accent:muted,2);
                c.drawLine(x+(float)Math.cos(a)*(radius-6),y+(float)Math.sin(a)*(radius-6),
                    x+(float)Math.cos(a)*(radius+3),y+(float)Math.sin(a)*(radius+3),p);
            }
            for(int i=0;i<4;i++){
                double a=i*Math.PI/2+j*.3;
                stroke(light?0x66708798:0x8865c1dd,3);
                c.drawLine(x,y,x+(float)Math.cos(a)*(radius-8),y+(float)Math.sin(a)*(radius-8),p);
            }
            fill(c,accent);c.drawCircle(x,y,5,p);
        }
        label(c,"SKELETON",0,-121,11,ink,true);
        label(c,shortDate,0,103,10,muted,true);
    }
    /**
     * Dedicated low-overhead mechanical face for TVs. The balance oscillates as a damped
     * spring with bounded discrete drive impulses. Escape-wheel and train angles derive
     * from its crossings, never from unrelated spin animations. This is a watch-inspired
     * engineering approximation, not a manufacturable escapement or a WebView.
     */
    private void mechanical(Canvas c) {
        updateMechanism();
        // Warm brushed-brass open-heart plate inside the numbered Harmony dial.
        fill(c,0xff07131e);c.drawCircle(0,24,113,p);
        stroke(0xff946e41,2);c.drawCircle(0,24,111,p);
        stroke(0xffe3c68d,.9f);c.drawCircle(0,24,105,p);
        stroke(0x779c784a,1);c.drawCircle(0,24,95,p);
        for(int n=0;n<24;n++) {
            float a=(float)(n*Math.PI/12);
            stroke(0x449c8055,1);
            c.drawLine((float)Math.cos(a)*99,(float)Math.sin(a)*99+24,
                    (float)Math.cos(a)*104,(float)Math.sin(a)*104+24,p);
        }
        // Gear train. Center 1 turn/hour, third 6 turns/hour, fourth 1 turn/minute.
        double fourth=escrowSteps*1.5;
        double third=-fourth/10d;
        double centre=-third/6d;
        gear(c,-45,-3,30,36,(float)centre,0xffc49b58);
        gear(c,20,-13,34,40,(float)third,0xffd9b16c);
        gear(c,69,22,20,28,(float)-fourth,0xffa98c63);
        gear(c,-57,48,21,28,(float)(-centre/6d),0xffb08249);
        // Faster escape wheel under the pallet fork.
        gear(c,-8,64,15,15,(float)(escrowSteps*12d),0xffaeb8bd);
        // A restrained stainless Swiss-lever style fork with jewel pallets and horns.
        c.save();
        c.translate(14,64);
        c.rotate((float)(Math.signum(balancePosition)*5.2));
        stroke(0xffaebcc6,3.7f);
        c.drawLine(0,0,-25,-11,p);c.drawLine(0,0,-23,13,p);
        stroke(0xffe6eef3,1.5f);
        c.drawLine(0,0,24,0,p);
        c.drawLine(24,0,30,-5,p);c.drawLine(24,0,30,5,p);
        fill(c,0xffc94757);c.drawCircle(-25,-11,3.2f,p);c.drawCircle(-23,13,3.2f,p);
        fill(c,0xffd2a865);c.drawCircle(0,0,4.2f,p);
        fill(c,0xff671c2b);c.drawCircle(0,0,2,p);
        c.restore();
        // Balance wheel: a single uninterrupted lower window with moving oscillator.
        fill(c,0xff091824);c.drawCircle(62,67,36,p);
        stroke(0xffab8550,1.4f);c.drawCircle(62,67,35,p);
        c.save();c.translate(62,67);
        c.rotate((float)Math.toDegrees(balancePosition));
        stroke(0xffe4c18a,4.2f);c.drawCircle(0,0,27,p);
        for(int a=0;a<4;a++){
            c.save();c.rotate(a*90);
            stroke(0xffb99157,3);c.drawLine(0,0,0,-27,p);
            fill(c,0xffe8c98b);c.drawCircle(0,-25,2.7f,p);
            c.restore();
        }
        fill(c,0xff802c42);c.drawCircle(0,0,5,p);
        c.restore();
        // Stationary gold spiral hints at the hairspring and remains cheap at 25 fps.
        mechanismPath.reset();
        for(int i=0;i<=60;i++){
            double t=i/60d,rad=3+t*17,angle=t*Math.PI*7;
            float x=62+(float)(Math.cos(angle)*rad),y=67+(float)(Math.sin(angle)*rad);
            if(i==0)mechanismPath.moveTo(x,y);else mechanismPath.lineTo(x,y);
        }
        stroke(0xffdfc68c,.85f);c.drawPath(mechanismPath,p);
        fill(c,0xffd8b47a);c.drawCircle(62,67,2.5f,p);
        label(c,"MECHANICAL",0,-74,11,0xfff4dfb5,true);
        label(c,"HARMONY VISION  ·  OPEN HEART",0,-61,5.7f,0xffcdb690,true);
    }

    private void updateMechanism() {
        long t=System.nanoTime();
        if(physicsNano==0L){physicsNano=t;return;}
        double duration=Math.min(.08,Math.max(0,(t-physicsNano)/1e9d));
        physicsNano=t;
        // Semimplicit Euler; bounded substeps keep the 2 Hz balance stable after TV stalls.
        while(duration>0) {
            double dt=Math.min(.005,duration);
            duration-=dt;
            balanceSpeed+=(-Math.pow(4*Math.PI,2)*balancePosition
                    - .55*balanceSpeed)*dt;
            balancePosition+=balanceSpeed*dt;
            if(balancePosition*lastBalanceSign<0) {
                escrowSteps++;
                // Coarse impulse replaces the mainspring torque otherwise lost to damping.
                balanceSpeed+=Math.signum(balanceSpeed)*.028;
            }
            if(Math.abs(balancePosition)>.03)
                lastBalanceSign=Math.signum(balancePosition);
        }
    }

    private void gear(Canvas c,float x,float y,float r,int teeth,float angle,int brass){
        c.save();c.translate(x,y);c.rotate(angle);
        mechanismPath.reset();
        for(int i=0;i<teeth;i++){
            double theta=i*2*Math.PI/teeth;
            for(int k=0;k<4;k++){
                double a=theta+k*(Math.PI*2/teeth/4);
                float rad=r+(k==1||k==2?2.2f:-1.9f);
                float gx=(float)Math.cos(a)*rad,gy=(float)Math.sin(a)*rad;
                if(i==0&&k==0)mechanismPath.moveTo(gx,gy);
                else mechanismPath.lineTo(gx,gy);
            }
        }
        mechanismPath.close();
        fill(c,brass);c.drawPath(mechanismPath,p);
        stroke(0xff5f4729,.9f);c.drawPath(mechanismPath,p);
        stroke(0xfff0d4a2,1);c.drawCircle(0,0,r-3,p);
        fill(c,0xff071925);c.drawCircle(0,0,r*.65f,p);
        for(int i=0;i<5;i++){
            c.save();c.rotate(i*72);
            stroke(0xffcaa56c,3.6f);c.drawLine(0,0,0,-r+3,p);
            c.restore();
        }
        fill(c,0xffe2c78e);c.drawCircle(0,0,4,p);
        fill(c,0xff9d3248);c.drawCircle(0,0,2.1f,p);
        c.restore();
    }
    private void drawHand(Canvas c,float angle,float length,float width,int color){
        c.save();c.rotate(angle);
        // Tail and pointed tip remain proportional at every widget size.
        hand.reset();hand.moveTo(-width/2f,13);hand.lineTo(-width/2f,-length+14);
        hand.lineTo(0,-length);hand.lineTo(width/2f,-length+14);
        hand.lineTo(width/2f,13);hand.close();
        fill(c,0x77000000);c.save();c.translate(1.5f,2);c.drawPath(hand,p);c.restore();
        fill(c,color);c.drawPath(hand,p);
        stroke(light?0x5599a7b6:0x99ffffff,1);c.drawLine(0,7,0,-length+17,p);
        c.restore();
    }
    private void drawSecond(Canvas c,float angle){
        c.save();c.rotate(angle);
        stroke(accent,2.5f);c.drawLine(0,29,0,-165,p);
        fill(c,accent);c.drawCircle(0,-165,3.8f,p);
        c.restore();
    }
}
