package com.sadique.launcher;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;
public class HijriCalendarTest {
    @Test public void islamicDatesUseHijriMonthsAndYears(){
        assertEquals("1 Ramadan 1445 AH",HijriCalendar.format(LocalDate.of(2024,3,11),0));
        assertEquals("1 Shawwal 1445 AH",HijriCalendar.format(LocalDate.of(2024,4,10),0));
    }
    @Test public void localAdjustmentCrossesMonthBoundariesAndIsBounded(){
        LocalDate date=LocalDate.of(2024,3,11);
        assertEquals(HijriCalendar.format(date.minusDays(1),0),HijriCalendar.format(date,-1));
        assertEquals("2 Ramadan 1445 AH",HijriCalendar.format(date,1));
        assertEquals(HijriCalendar.format(date,2),HijriCalendar.format(date,100));
        assertEquals(HijriCalendar.format(date,-2),HijriCalendar.format(date,-100));
    }
}
