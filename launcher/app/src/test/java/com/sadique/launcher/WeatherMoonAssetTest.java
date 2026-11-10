package com.sadique.launcher;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;
public class WeatherMoonAssetTest {
    @Test public void bundledSurfaceHasRealContrastAndExpectedDecodeBounds()throws Exception{
        String asset="src/main/res/drawable-nodpi/weather_moon_nasa.jpg";
        File file=new File(asset);if(!file.isFile())file=new File("app/"+asset);if(!file.isFile())file=new File("launcher/app/"+asset);
        assertTrue("Bundled moon JPEG missing",file.isFile());assertTrue(file.length()<300_000);
        BufferedImage image=ImageIO.read(file);assertNotNull(image);assertEquals(1024,image.getWidth());assertEquals(1024,image.getHeight());
        double sum=0,squared=0;int count=0,white=0;
        for(int y=112;y<912;y+=8)for(int x=112;x<912;x+=8)if((x-512)*(x-512)+(y-512)*(y-512)<400*400){
            int pixel=image.getRGB(x,y);double v=(((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255))/3.0;sum+=v;squared+=v*v;count++;if(v>230)white++;
        }
        assertTrue(Math.sqrt(squared/count-Math.pow(sum/count,2))>20);assertTrue(white<count/10);assertEquals(0,image.getRGB(0,0)&0xffffff);
    }
}
