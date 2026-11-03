package com.sadique.launcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeStartupResponseTest {
    private static InputStream response(String text,int chunk,boolean failAfter) {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            int offset;
            @Override public int read() { throw new AssertionError("Use bulk reads"); }
            @Override public int read(byte[] out,int start,int length) throws IOException {
                if (offset==bytes.length) {
                    if (failAfter) throw new IOException("Stream closed.");
                    return -1;
                }
                int count=Math.min(Math.min(chunk,length),bytes.length-offset);
                System.arraycopy(bytes,offset,out,start,count); offset+=count; return count;
            }
        };
    }
    @Test public void acceptsMarkerWithoutReadingClosedStreamAgain() throws Exception {
        VolumeStartupResponse.awaitReady(response("Fast helper running\nPLV_AUTO_START_OK\n",1024,true),32768);
    }
    @Test public void acceptsFragmentedCrLfMarker() throws Exception {
        VolumeStartupResponse.awaitReady(response("status\r\nPLV_AUTO_START_OK\r\n",1,true),32768);
    }
    @Test public void missingOrPartialMarkerNeverConfirms() throws Exception {
        for (String text:new String[]{"","helper failed\n","PLV_AUTO_START_OK","prefix PLV_AUTO_START_OK\n","PLV_AUTO_START_OK_suffix\n"}) {
            try { VolumeStartupResponse.awaitReady(response(text,3,false),32768); fail(text); }
            catch (IOException expected) { assertEquals("Helper did not confirm readiness",expected.getMessage()); }
        }
    }
    @Test public void disconnectBeforeConfirmationRemainsFailure() throws Exception {
        try { VolumeStartupResponse.awaitReady(response("helper failed\n",2,true),32768); fail(); }
        catch (IOException expected) { assertEquals("Stream closed.",expected.getMessage()); }
    }
    @Test public void outputLimitAppliesBeforeMarker() throws Exception {
        try { VolumeStartupResponse.awaitReady(response("123456\nPLV_AUTO_START_OK\n",1024,false),6); fail(); }
        catch (IOException expected) { assertEquals("Response too large",expected.getMessage()); }
    }
    @Test public void outputAfterConfirmationIsNotRequired() throws Exception {
        VolumeStartupResponse.awaitReady(response("PLV_AUTO_START_OK\nignored",1024,true),18);
    }
    @Test public void shellWaitsForClientCloseAfterConfirming() throws Exception {
        org.junit.Assume.assumeTrue(new File("/bin/sh").canExecute());
        Process child=new ProcessBuilder("/bin/sh","-c","echo PLV_AUTO_START_OK && read -r plv_ack").start();
        try {
            VolumeStartupResponse.awaitReady(child.getInputStream(),32768);
            assertTrue(child.isAlive());
            child.getOutputStream().close();
            assertTrue(child.waitFor(3,java.util.concurrent.TimeUnit.SECONDS));
        } finally { child.destroyForcibly(); }
    }
}
