package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassDockLayoutTest {
    @Test public void cardAndItsFullReflectionFitTheAllocatedDockAtTvSizes(){
        for(int height:new int[]{480,540,600,720,1080}){
            int card=GlassDockLayout.cardHeight(height);assertTrue(card>=96&&card<=160);
            assertTrue(GlassDockLayout.dockHeight(height)>=card*2+46);
        }
        assertEquals(114,GlassDockLayout.cardHeight(540));assertEquals(160,GlassDockLayout.cardHeight(1080));
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
