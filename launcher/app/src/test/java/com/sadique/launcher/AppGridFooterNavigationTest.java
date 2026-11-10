package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppGridFooterNavigationTest {
    @Test public void footerIsReachableFromEveryAppInLastRowEvenWhenNotYetBound(){
        for(int columns=2;columns<=6;columns++)for(int count=1;count<30;count++)for(int pos=0;pos<count;pos++){
            int next=AppGridNavigation.verticalWithFooter(pos,count,columns,true,true);
            if(pos/columns==(count-1)/columns)assertEquals(count,next);
            else assertEquals(AppGridNavigation.vertical(pos,count,columns,true),next);
        }
    }
    @Test public void disabledFooterAndUpwardNavigationKeepExistingGridBehaviour(){
        for(int count=1;count<30;count++)for(int pos=0;pos<count;pos++){
            assertEquals(AppGridNavigation.vertical(pos,count,4,true),AppGridNavigation.verticalWithFooter(pos,count,4,true,false));
            assertEquals(AppGridNavigation.vertical(pos,count,4,false),AppGridNavigation.verticalWithFooter(pos,count,4,false,true));
        }
    }
}
