package com.sadique.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Confirm readiness without waiting for EOF on libadb's shell stream. */
final class VolumeStartupResponse {
    private VolumeStartupResponse() { }

    static void awaitReady(InputStream input,int limit) throws IOException {
        ByteArrayOutputStream line=new ByteArrayOutputStream();
        byte[] buffer=new byte[1024]; int total=0,count;
        while ((count=input.read(buffer))!=-1) {
            for (int i=0;i<count;i++) {
                if (++total>limit) throw new IOException("Response too large");
                int value=buffer[i]&255;
                if (value=='\n') {
                    String text=new String(line.toByteArray(),StandardCharsets.UTF_8);
                    if (text.endsWith("\r")) text=text.substring(0,text.length()-1);
                    if (text.equals("PLV_AUTO_START_OK")) return;
                    line.reset();
                } else line.write(value);
            }
        }
        throw new IOException("Helper did not confirm readiness");
    }
}
