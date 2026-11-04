package com.sadique.launcher;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeSetupResponseTest {
    private InputStream body(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
    @Test public void failureMarkerDoesNotBecomeSuccess() throws Exception {
        assertEquals(HomeSetupCommand.FAILED,VolumeStartupResponse.awaitResult(body("Success\n"+HomeSetupCommand.FAILED+"\n"),32768,HomeSetupCommand.MARKER,HomeSetupCommand.FAILED));
    }
    @Test public void confirmedHomeUsesItsOwnMarker() throws Exception {
        assertEquals(HomeSetupCommand.MARKER,VolumeStartupResponse.awaitResult(body(HomeSetupCommand.MARKER+"\r\n"),32768,HomeSetupCommand.MARKER,HomeSetupCommand.FAILED));
    }
    @Test(expected=IOException.class) public void volumeMarkerDoesNotConfirmHome() throws Exception {
        VolumeStartupResponse.awaitResult(body("PLV_AUTO_START_OK\n"),32768,HomeSetupCommand.MARKER,HomeSetupCommand.FAILED);
    }
}
