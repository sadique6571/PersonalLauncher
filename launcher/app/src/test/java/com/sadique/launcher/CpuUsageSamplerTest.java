package com.sadique.launcher;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;
public class CpuUsageSamplerTest {
    private static final class Fake implements CpuUsageSampler.Source {
        String stat="cpu 60 0 0 40";boolean blockProc,blockIdle;int procCalls,idleCalls,cores=4;
        long idle=1000000,wall,cpu;
        public String procStat()throws IOException{procCalls++;if(blockProc)throw new IOException();return stat;}
        public long[] idleMicros()throws IOException{idleCalls++;if(blockIdle)throw new IOException();return new long[]{idle,cores};}
        public long elapsedMillis(){return wall;}
        public long processCpuMillis(){return cpu;}
        public int processors(){return cores;}
    }
    @Test public void systemCountersProduceActualDeltaAndResetWithoutInventingBusyTime(){
        Fake source=new Fake();CpuUsageSampler sampler=new CpuUsageSampler(source);assertFalse(sampler.sample().ready());
        source.stat="cpu 80 0 0 60";CpuUsageSampler.Sample sample=sampler.sample();assertTrue(sample.system);assertEquals(50,sample.percent,.001);
        source.stat="cpu 30 0 0 20";assertFalse(sampler.sample().ready());
        source.stat="cpu 80 0 0 70";assertEquals(50,sampler.sample().percent,.001);
    }
    @Test public void restrictedProcFallsBackToRealIdleCounters(){
        Fake source=new Fake();source.blockProc=true;CpuUsageSampler sampler=new CpuUsageSampler(source);assertFalse(sampler.sample().ready());
        source.wall=1000;source.idle+=2000000;CpuUsageSampler.Sample sample=sampler.sample();
        assertTrue(sample.system);assertEquals(50,sample.percent,.001);assertEquals(1,source.procCalls);
    }
    @Test public void restrictedSystemSourcesUseAnExplicitlyLabelledProcessMeasurement(){
        Fake source=new Fake();source.blockProc=true;source.blockIdle=true;CpuUsageSampler sampler=new CpuUsageSampler(source);
        CpuUsageSampler.Sample first=sampler.sample();assertFalse(first.ready());assertFalse(first.system);
        source.wall=1000;source.cpu=400;CpuUsageSampler.Sample sample=sampler.sample();assertFalse(sample.system);assertEquals(10,sample.percent,.001);
        source.wall=2000;source.cpu=800;assertEquals(10,sampler.sample().percent,.001);
        assertEquals(1,source.procCalls);assertEquals(1,source.idleCalls);
    }
    @Test public void cpuHotplugResetsTheIdleWindow(){
        Fake source=new Fake();source.blockProc=true;CpuUsageSampler sampler=new CpuUsageSampler(source);sampler.sample();
        source.wall=1000;source.cores=2;source.idle+=500000;assertFalse(sampler.sample().ready());
        source.wall=2000;source.idle+=1000000;assertEquals(50,sampler.sample().percent,.001);
    }
}
