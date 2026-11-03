package com.sadique.launcher;

import java.io.*;
import java.nio.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdbTlsGateTest {
    byte[] header(int command,int length) { return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(0).putInt(0).putInt(length).putInt(0).putInt(command^0xffffffff).array(); }
    @Test public void tlsUpgradeIsForwarded() throws Exception {
        byte[] packet=header(AdbTlsGate.STLS,0); ByteArrayOutputStream output=new ByteArrayOutputStream();
        AdbTlsGate.packet(new ByteArrayInputStream(packet),output,AdbTlsGate.STLS);
        assertArrayEquals(packet,output.toByteArray());
    }
    @Test public void legacyAuthCannotUseTheSavedKeyAsSigningOracle() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try { AdbTlsGate.packet(new ByteArrayInputStream(header(0x48545541,20)),output,AdbTlsGate.STLS); fail(); }
        catch (IOException expected) { assertEquals(0,output.size()); }
    }
    @Test public void oversizedOrTruncatedFramesAreRefused() throws Exception {
        for (byte[] bytes:new byte[][]{header(AdbTlsGate.CNXN,5000),new byte[3]}) {
            try { AdbTlsGate.packet(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),AdbTlsGate.CNXN); fail(); }
            catch (IOException expected) { }
        }
    }
}
