package com.sadique.launcher;

import android.os.Process;
import android.os.SystemClock;
import java.io.*;
import java.util.*;

/** Invoked only on the telemetry worker. Discover bounded idle-counter paths once per visible session. */
final class AndroidCpuSource implements CpuUsageSampler.Source {
    private static final class Core {
        final File online;
        final List<File> counters=new ArrayList<>();
        Core(File online){this.online=online;}
    }
    private List<Core> paths;
    @Override public String procStat()throws IOException{return line(new File("/proc/stat"));}
    @Override public long[] idleMicros()throws IOException{
        if(paths==null){
            paths=new ArrayList<>();
            for(int cpu=0;cpu<32;cpu++){
                File directory=new File("/sys/devices/system/cpu/cpu"+cpu+"/cpuidle");
                File[] states=directory.listFiles(f->f.isDirectory()&&f.getName().matches("state[0-9]+"));
                if(states==null||states.length==0)continue;
                Core core=new Core(new File(directory.getParentFile(),"online"));
                for(int state=0;state<Math.min(16,states.length);state++){File counter=new File(states[state],"time");line(counter);core.counters.add(counter);}
                paths.add(core);
            }
            if(paths.isEmpty())throw new IOException("Idle counters restricted");
        }
        long idle=0;int online=0;
        try{for(Core core:paths){
            if(core.online.exists()&&"0".equals(line(core.online).trim()))continue;
            for(File path:core.counters)idle+=Long.parseLong(line(path).trim());online++;
        }}catch(NumberFormatException invalid){throw new IOException("Invalid idle counters",invalid);}
        if(online<processors())throw new IOException("Partial CPU counters");
        return new long[]{idle,online};
    }
    @Override public long elapsedMillis(){return SystemClock.elapsedRealtime();}
    @Override public long processCpuMillis(){return Process.getElapsedCpuTime();}
    @Override public int processors(){return Runtime.getRuntime().availableProcessors();}
    private static String line(File file)throws IOException{
        try(BufferedReader reader=new BufferedReader(new FileReader(file))){String value=reader.readLine();if(value==null)throw new IOException("Empty counter");return value;}
    }
}
