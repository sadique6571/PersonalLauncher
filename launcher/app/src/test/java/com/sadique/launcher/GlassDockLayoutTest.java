package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassDockLayoutTest {
    @Test public void cardHeightFollowsWidthAndStillLeavesSpaceForItsFullReflection(){
        for(int screen:new int[]{480,540,600,720,1080})for(int width:new int[]{100,128,160,192,256,400}){
            int card=GlassDockLayout.cardHeight(width,screen);assertTrue(card>=80&&card<=136);
            assertTrue(GlassDockLayout.dockHeight(width,screen)>=card*2+46);
        }
        assertEquals(80,GlassDockLayout.cardHeight(128,720));
        assertEquals(120,GlassDockLayout.cardHeight(192,720));
        assertTrue(GlassDockLayout.cardHeight(128,720)<GlassDockLayout.cardHeight(192,720));
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
