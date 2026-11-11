package com.sadique.launcher;

import org.junit.Test;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashSet;
import static org.junit.Assert.*;

public class HarmonyDailyWordsTest {
    @Test public void dailyWordsAreOfflineStableAndChangeEveryLocalDay(){
        LocalDate first=LocalDate.of(2026,11,11);
        assertTrue(HarmonyDailyWords.count()>=90);
        assertEquals(HarmonyDailyWords.forDay(first),HarmonyDailyWords.forDay(first));
        for(int i=0;i<400;i++){
            String a=HarmonyDailyWords.forDay(first.plusDays(i));
            String b=HarmonyDailyWords.forDay(first.plusDays(i+1));
            assertNotEquals("Same text on consecutive days",a,b);
            assertTrue(a.length()<=64);
        }
    }
    @Test public void fullCycleHasNoDuplicateText(){
        HashSet<String> seen=new HashSet<>();
        LocalDate start=LocalDate.of(2026,1,1);
        for(int i=0;i<HarmonyDailyWords.count();i++)
            assertTrue(seen.add(HarmonyDailyWords.forDay(start.plusDays(i))));
    }
    @Test public void usesDeviceLocalCalendarDateAcrossYearBoundary(){
        Calendar c=Calendar.getInstance();
        c.clear();c.set(2026,Calendar.DECEMBER,31,23,59,0);
        assertEquals(HarmonyDailyWords.forDay(LocalDate.of(2026,12,31)),HarmonyDailyWords.forCalendar(c));
        c.add(Calendar.MINUTE,1);
        assertEquals(HarmonyDailyWords.forDay(LocalDate.of(2027,1,1)),HarmonyDailyWords.forCalendar(c));
    }
}
