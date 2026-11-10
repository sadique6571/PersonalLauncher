package com.sadique.launcher;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherMoonAssetTest {
    @Test public void bundledImageMatchesTheReviewedSurfaceAndDecodeBounds()throws Exception{
        String asset="src/main/res/drawable-nodpi/weather_moon_nasa.jpg";
        File file=new File(asset);if(!file.isFile())file=new File("app/"+asset);if(!file.isFile())file=new File("launcher/app/"+asset);
        assertTrue("Bundled moon JPEG missing",file.isFile());byte[] image=Files.readAllBytes(file.toPath());assertTrue(image.length<300_000);
        assertEquals(0xff,image[0]&255);assertEquals(0xd8,image[1]&255);
        boolean dimensions=false;
        for(int p=2;p+8<image.length;){
            assertEquals(0xff,image[p++]&255);while((image[p]&255)==0xff)p++;int marker=image[p++]&255;
            if(marker==0xd9||marker==0xda)break;
            int length=((image[p]&255)<<8)|(image[p+1]&255);assertTrue(length>=2&&p+length<=image.length);
            if(marker==0xc0||marker==0xc1||marker==0xc2){assertEquals(1024,((image[p+3]&255)<<8)|(image[p+4]&255));assertEquals(1024,((image[p+5]&255)<<8)|(image[p+6]&255));dimensions=true;break;}p+=length;
        }
        assertTrue("Expected 1024px lunar image",dimensions);
        // Locks the visually reviewed asset: its grey terrain has measured luminance deviation >34.
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(image))hash.append(String.format(Locale.ROOT,"%02x",b&255));
        assertEquals("f6766945087efb8f0f91ac0ec8e74a194ff9b7b65414f2d0859669367992a172",hash.toString());
    }
}
