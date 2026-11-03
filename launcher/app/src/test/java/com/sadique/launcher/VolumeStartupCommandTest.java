package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class VolumeStartupCommandTest {
    @Test public void shellQuoteKeepsCommandSubstitutionLiteral() throws Exception {
        org.junit.Assume.assumeTrue(new java.io.File("/bin/sh").canExecute());
        String value="a'$(printf unsafe)`printf unsafe`$HOME";
        Process child=new ProcessBuilder("/bin/sh","-c","printf %s "+VolumeStartupCommand.quote(value)).start();
        String actual=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,child.waitFor()); assertEquals(value,actual);
    }
    @Test public void helperStopsBeforePayloadIsReplacedAndUsesAutomaticInput() {
        String command=VolumeStartupCommand.build("YWJj","/data/app/example/base.apk");
        assertTrue(command.indexOf(" stop")<command.indexOf(" && cp "));
        assertTrue(command.indexOf(" && cp ")<command.indexOf(" start auto 50 launcher"));
        assertTrue(command.endsWith(" && echo PLV_AUTO_START_OK"));
        assertFalse(command.contains("tcpip:"));
    }
    @Test public void untrustedScriptEncodingAndApkLocationsAreRefused() {
        try { VolumeStartupCommand.build("YWJj; id","/data/app/x/base.apk"); fail(); } catch (IllegalArgumentException expected) { }
        try { VolumeStartupCommand.build("YWJj","/sdcard/random.apk"); fail(); } catch (IllegalArgumentException expected) { }
    }
}
