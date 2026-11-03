package com.sadique.launcher;

import java.io.*;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeWallpaperCacheTest {
    @Test public void snapshotSurvivesASecondReadAndAtomicReplacement() throws Exception {
        File directory=Files.createTempDirectory("home-photo").toFile(); File file=new File(directory,"photo.bin");
        try {
            HomeWallpaperCache.write(file,"https://www.bing.com/first",123,new byte[]{1,2,3});
            HomeWallpaperCache.Entry first=HomeWallpaperCache.read(file);
            assertEquals("https://www.bing.com/first",first.url); assertEquals(123,first.checkedAt);
            assertArrayEquals(new byte[]{1,2,3},first.image);
            HomeWallpaperCache.write(file,"https://www.bing.com/second",456,new byte[]{4,5});
            HomeWallpaperCache.Entry second=HomeWallpaperCache.read(file);
            assertEquals("https://www.bing.com/second",second.url); assertArrayEquals(new byte[]{4,5},second.image);
            assertFalse(new File(file+".new").exists());
        } finally { file.delete(); directory.delete(); }
    }
    @Test public void interruptedNewWriteDoesNotDamageLastGoodSnapshot() throws Exception {
        File directory=Files.createTempDirectory("home-photo").toFile(); File file=new File(directory,"photo.bin"); File next=new File(file+".new");
        try {
            HomeWallpaperCache.write(file,"https://www.bing.com/last",100,new byte[]{7});
            Files.write(next.toPath(),new byte[]{1,2});
            assertArrayEquals(new byte[]{7},HomeWallpaperCache.read(file).image);
        } finally { next.delete(); file.delete(); directory.delete(); }
    }
    @Test public void missingTruncatedAndMalformedSnapshotsAreIgnored() throws Exception {
        File file=File.createTempFile("home-photo",".bin");
        try {
            assertNull(HomeWallpaperCache.read(file));
            Files.write(file.toPath(),new byte[]{1,2,3}); assertNull(HomeWallpaperCache.read(file));
            HomeWallpaperCache.write(file,"https://www.bing.com/last",100,new byte[]{1,2,3});
            try (RandomAccessFile out=new RandomAccessFile(file,"rw")) { out.setLength(file.length()-1); }
            assertNull(HomeWallpaperCache.read(file));
        } finally { file.delete(); }
    }
    @Test public void invalidReplacementKeepsLastGoodSnapshot() throws Exception {
        File file=File.createTempFile("home-photo",".bin");
        try {
            HomeWallpaperCache.write(file,"https://www.bing.com/last",100,new byte[]{7});
            try { HomeWallpaperCache.write(file,"",200,new byte[]{9}); fail(); } catch (IOException expected) { }
            assertArrayEquals(new byte[]{7},HomeWallpaperCache.read(file).image);
        } finally { file.delete(); }
    }
}
