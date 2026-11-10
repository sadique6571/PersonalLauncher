package com.sadique.launcher;

import java.io.IOException;

/** Prefer real system counters; a process-only fallback is explicitly identified as Launcher CPU. */
final class CpuUsageSampler {
    interface Source {
        String procStat() throws IOException;
        long[] idleMicros() throws IOException; // {summed idle microseconds, sampled core count}
        long elapsedMillis();
        long processCpuMillis();
        int processors();
    }
    static final class Sample {
        final double percent;
        final boolean system;
        Sample(double percent,boolean system){this.percent=percent;this.system=system;}
        boolean ready(){return percent>=0;}
    }
    private final Source source;
    private boolean procBlocked,idleBlocked;
    private long totalPrev=-1,idlePrev,wallPrev=-1,processPrev=-1;
    private int coresPrev;
    CpuUsageSampler(Source source){this.source=source;}
    Sample sample(){
        if(!procBlocked){
            try{
                long[] ticks=GlassOsMetrics.cpuTicks(source.procStat());if(ticks==null)throw new IOException("No counters");
                long total=ticks[0],idle=ticks[1];double value=-1;
                if(totalPrev>=0&&total>totalPrev&&idle>=idlePrev&&idle-idlePrev<=total-totalPrev)value=clamp(100d*(total-totalPrev-(idle-idlePrev))/(total-totalPrev));
                totalPrev=total;idlePrev=idle;return new Sample(value,true);
            }catch(IOException|SecurityException blocked){procBlocked=true;totalPrev=-1;wallPrev=-1;}
        }
        if(!idleBlocked){
            try{
                long[] idle=source.idleMicros();if(idle==null||idle.length<2||idle[0]<0||idle[1]<=0)throw new IOException("No idle counters");
                long wall=source.elapsedMillis();int cores=(int)idle[1];double value=-1;
                if(wallPrev>=0&&wall>wallPrev&&cores==coresPrev&&idle[0]>=idlePrev){double capacity=(wall-wallPrev)*1000d*cores;double elapsedIdle=idle[0]-idlePrev;if(elapsedIdle<=capacity)value=clamp(100d*(capacity-elapsedIdle)/capacity);}
                idlePrev=idle[0];wallPrev=wall;coresPrev=cores;return new Sample(value,true);
            }catch(IOException|SecurityException blocked){idleBlocked=true;wallPrev=-1;processPrev=-1;}
        }
        long wall=source.elapsedMillis(),cpu=source.processCpuMillis();int cores=Math.max(1,source.processors());double value=-1;
        if(wallPrev>=0&&wall>wallPrev&&processPrev>=0&&cpu>=processPrev&&cores==coresPrev)value=clamp(100d*(cpu-processPrev)/((wall-wallPrev)*(double)cores));
        wallPrev=wall;processPrev=cpu;coresPrev=cores;return new Sample(value,false);
    }
    private static double clamp(double value){return Math.max(0,Math.min(100,value));}
}
