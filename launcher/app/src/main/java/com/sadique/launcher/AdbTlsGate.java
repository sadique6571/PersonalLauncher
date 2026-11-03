package com.sadique.launcher;

import java.io.*;
import java.nio.*;

/** Refuse legacy AUTH on an mDNS endpoint: the saved identity is used only after STLS. */
final class AdbTlsGate {
    static final int CNXN=0x4e584e43, STLS=0x534c5453;
    static void packet(InputStream input,OutputStream output,int expected) throws IOException {
        byte[] header=new byte[24]; new DataInputStream(input).readFully(header);
        ByteBuffer data=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command=data.getInt(), length=data.getInt(12), magic=data.getInt(20);
        if (command!=expected || magic!=(command^0xffffffff) || length<0 || length>4096
                || (expected==STLS && length!=0)) throw new IOException("Local endpoint did not negotiate ADB TLS");
        byte[] payload=new byte[length]; new DataInputStream(input).readFully(payload);
        output.write(header); output.write(payload); output.flush();
    }
    private AdbTlsGate() { }
}
