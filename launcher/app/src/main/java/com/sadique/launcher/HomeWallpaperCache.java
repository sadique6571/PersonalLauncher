package com.sadique.launcher;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** One atomic Home image snapshot. Screensaver images are never written here. */
final class HomeWallpaperCache {
    private static final int MAGIC=0x504c4231;
    private static final int LIMIT=6*1024*1024;
    private HomeWallpaperCache() { }
    static final class Entry {
        final String url;
        final long checkedAt;
        final byte[] image;
        Entry(String url,long checkedAt,byte[] image) { this.url=url; this.checkedAt=checkedAt; this.image=image; }
    }
    static synchronized Entry read(File file) {
        if (!file.isFile() || file.length()>LIMIT+8192L) return null;
        try (DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt()!=MAGIC) return null;
            String url=in.readUTF(); long checkedAt=in.readLong(); int length=in.readInt();
            if (url.isEmpty() || url.length()>4096 || length<=0 || length>LIMIT) return null;
            byte[] bytes=new byte[length]; in.readFully(bytes);
            if (in.read()!=-1) return null;
            return new Entry(url,checkedAt,bytes);
        } catch (IOException failure) { return null; }
    }
    static synchronized void write(File file,String url,long checkedAt,byte[] image) throws IOException {
        if (url==null || url.isEmpty() || url.length()>4096 || image.length==0 || image.length>LIMIT)
            throw new IOException("Invalid Home wallpaper snapshot");
        File next=new File(file.getPath()+".new");
        try {
            try (FileOutputStream stream=new FileOutputStream(next);
                 DataOutputStream out=new DataOutputStream(new BufferedOutputStream(stream))) {
                out.writeInt(MAGIC); out.writeUTF(url); out.writeLong(checkedAt);
                out.writeInt(image.length); out.write(image); out.flush(); stream.getFD().sync();
            }
            try { Files.move(next.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(next.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { if (next.exists()) next.delete(); }
    }
}
