package com.sadique.launcher;

/** Deterministic shaded lunar artwork. Decorative terrain, not a claim about the current lunar phase. */
final class MoonTexture {
    private MoonTexture(){}
    private static final double[][] SEAS={{-.31,-.27,.31,.32},{.10,-.42,.23,.18},{.35,-.15,.22,.28},{-.43,.16,.17,.14},{.12,.11,.25,.15},{-.08,-.05,.21,.20},{.43,.28,.12,.11}};
    static int[] pixels(int size){
        if(size<16||size>256)throw new IllegalArgumentException("Moon texture size");
        int[] result=new int[size*size];double[][] craters=new double[28][3];
        for(int i=0;i<craters.length;i++){double angle=(i*2.399963),distance=.18+.68*hash(i,93);craters[i][0]=Math.cos(angle)*distance;craters[i][1]=Math.sin(angle)*distance;craters[i][2]=.025+.055*hash(i,31);}
        double radius=size*.455;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            double nx=(x+.5-size/2.0)/radius,ny=(y+.5-size/2.0)/radius,r2=nx*nx+ny*ny;if(r2>=1)continue;
            double z=Math.sqrt(1-r2),terrain=0;
            for(double[] sea:SEAS){double dx=(nx-sea[0])/sea[2],dy=(ny-sea[1])/sea[3];terrain=Math.max(terrain,Math.exp(-(dx*dx+dy*dy)*1.5));}
            double detail=(noise(nx*18+35,ny*18+31)-.5)*22+(noise(nx*43+57,ny*43+23)-.5)*10;
            for(double[] crater:craters){double dx=(nx-crater[0])/crater[2],dy=(ny-crater[1])/crater[2],d=dx*dx+dy*dy;if(d>1.4)continue;double ring=Math.sqrt(d);detail+=16*Math.exp(-Math.pow((ring-.90)*9,2));if(d<.7)detail-=9+dx*7;}
            double light=.62+.38*Math.max(0,-.43*nx-.28*ny+.858*z);
            int grey=Math.max(48,Math.min(226,(int)Math.round((215-91*terrain+detail)*light)));
            int alpha=Math.min(255,(int)Math.round((1-Math.sqrt(r2))*radius*255));
            result[y*size+x]=(alpha<<24)|(grey<<16)|(grey<<8)|Math.min(255,grey+3);
        }
        return result;
    }
    private static double hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0x7fffffff)/(double)0x7fffffff;}
    private static double noise(double x,double y){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);double a=hash(ix,iy)*(1-fx)+hash(ix+1,iy)*fx,b=hash(ix,iy+1)*(1-fx)+hash(ix+1,iy+1)*fx;return a*(1-fy)+b*fy;}
}
