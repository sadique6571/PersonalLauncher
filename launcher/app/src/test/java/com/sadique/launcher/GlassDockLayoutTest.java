package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassDockLayoutTest {
    @Test public void iconDockLeavesRoomForCaptionAndShortReflection(){
        for(int screen:new int[]{480,540,600,720,1080})for(int width:new int[]{100,128,160,192,256,400}){
            int tile=GlassDockLayout.iconTileHeight(width,screen,GlassDockLayout.captionHeight(16,1));
            int reflection=GlassDockLayout.reflectionHeight(tile);
            assertTrue(reflection>=16&&reflection<=32);assertTrue(reflection<tile/2);
            assertEquals(tile+reflection+60,GlassDockLayout.dockHeight(width,screen));
            if(screen==540)assertTrue(GlassDockLayout.dockHeight(width,screen)<=244);
        }
    }
    @Test public void wideIconsFitTheirViewportAndKeepExternalCaptions(){
        for(int screen:new int[]{480,540,600,720,1080})for(int width:new int[]{96,128,160,192,256,400}){
            int side=GlassDockLayout.iconSide(width,screen),caption=GlassDockLayout.captionHeight(16,1);
            assertTrue(side>=48&&side<=128);assertTrue(side<=width);
            int body=GlassDockLayout.wideBodyHeight(width,screen),bodyWidth=GlassDockLayout.wideBodyWidth(width,body);
            assertEquals(body+caption,GlassDockLayout.iconTileHeight(width,screen,caption));
            assertTrue(bodyWidth<=width);assertEquals(1.60,bodyWidth/(double)body,.025);
            assertTrue(GlassDockLayout.reflectionHeight(side+caption)<side/2);
        }
        assertTrue(GlassDockLayout.captionHeight(20,1.5f)>GlassDockLayout.captionHeight(16,1));
        assertTrue(GlassDockLayout.iconSide(160,540)>GlassDockLayout.iconSide(160,480));
    }
    @Test public void compactWidthFitsEvenTheSmallestNeighbourDuringExpansion(){
        for(int columns=2;columns<=6;columns++)for(int rowWidth:new int[]{640,897,1216,1856})for(int selected=0;selected<columns;selected++){
            int safe=GlassDockLayout.compactCellWidth(rowWidth,columns);
            for(int column=0;column<columns;column++)if(column!=selected){int actual=rowWidth*GlassDockLayout.span(column,columns,-1,selected,1)/GlassDockLayout.SPANS;assertTrue(safe<=actual);}
        }
    }
    @Test public void expandingAcrossRowsNeverChangesRowMembershipOrOverflows(){
        for(int columns=2;columns<=6;columns++)for(int from=-1;from<columns*3;from++)for(int to=-1;to<columns*3;to++)for(int tick=0;tick<=10;tick++)for(int row=0;row<3;row++){
            int sum=0;for(int col=0;col<columns;col++){int span=GlassDockLayout.span(row*columns+col,columns,from,to,tick/10f);assertTrue(span>0);sum+=span;}
            assertEquals(GlassDockLayout.SPANS,sum);
        }
    }
    @Test public void selectedCardIsNearlyTwiceItsNeighbourWithoutScalingText(){
        for(int columns=2;columns<=6;columns++){
            int large=GlassDockLayout.span(0,columns,-1,0,1),normal=GlassDockLayout.span(1,columns,-1,0,1);
            assertEquals(1.98,large/(double)normal,.04);
            assertEquals(GlassDockLayout.SPANS/columns,GlassDockLayout.span(columns,columns,-1,0,1));
        }
    }
    @Test public void clearingFocusRestoresEqualColumns(){
        for(int columns=2;columns<=6;columns++)for(int p=0;p<columns;p++)assertEquals(GlassDockLayout.SPANS/columns,GlassDockLayout.span(p,columns,0,-1,1));
    }
}
