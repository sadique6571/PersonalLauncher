package com.sadique.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Confirm readiness without waiting for EOF on libadb's shell stream. */
final class VolumeStartupResponse {
    private VolumeStartupResponse() { }

    static void awaitReady(InputStream input,int limit) throws IOException {
        await(input,limit,new String[]{"PLV_AUTO_START_OK"},"Helper did not confirm readiness");
    }
    static void awaitMarker(InputStream input,int limit,String marker) throws IOException {
        await(input,limit,new String[]{marker},"Setup command did not confirm readiness");
    }
    static String awaitResult(InputStream input,int limit,String success,String failed) throws IOException {
        return await(input,limit,new String[]{success,failed},"Setup command did not confirm readiness");
    }
    private static String await(InputStream input,int limit,String[] markers,String failure) throws IOException {
        ByteArrayOutputStream line=new ByteArrayOutputStream();
        byte[] buffer=new byte[1024]; int total=0,count;
        while ((count=input.read(buffer))!=-1) {
            for (int i=0;i<count;i++) {
                if (++total>limit) throw new IOException("Response too large");
                int value=buffer[i]&255;
                if (value=='\n') {
                    String text=new String(line.toByteArray(),StandardCharsets.UTF_8);
                    if (text.endsWith("\r")) text=text.substring(0,text.length()-1);
                    for (String marker:markers) if (text.equals(marker)) return marker;
                    line.reset();
                } else line.write(value);
            }
        }
        throw new IOException(failure);
    }
}
