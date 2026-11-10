package com.sadique.launcher;

/** Measurement parsing is independent of views, sampling and platform availability. */
final class GlassOsMetrics {
    private GlassOsMetrics() { }
    static int percent(long used,long total){return total<=0?0:(int)Math.max(0,Math.min(100,Math.round(100d*used/total)));}
    static long[] cpuTicks(String line){
        if(line==null)return null;
        String[] f=line.trim().split("\\s+");if(f.length<5||!"cpu".equals(f[0]))return null;
        try{long total=0;for(int i=1;i<Math.min(f.length,9);i++){long n=Long.parseLong(f[i]);if(n<0)return null;total+=n;}
            long idle=Long.parseLong(f[4])+(f.length>5?Long.parseLong(f[5]):0);return new long[]{total,idle};
        }catch(NumberFormatException e){return null;}
    }
}
