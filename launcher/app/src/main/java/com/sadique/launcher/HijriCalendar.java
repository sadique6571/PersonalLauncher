package com.sadique.launcher;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/** Calculated Islamic Umm al-Qura date; local moon-sighting differences can be adjusted in Clock settings. */
final class HijriCalendar {
    private static final String[] MONTHS={"Muharram","Safar","Rabi al-Awwal","Rabi al-Thani","Jumada al-Awwal","Jumada al-Thani","Rajab","Sha'ban","Ramadan","Shawwal","Dhu al-Qi'dah","Dhu al-Hijjah"};
    private HijriCalendar(){}
    static String format(LocalDate day,int adjustment){
        HijrahDate date=HijrahDate.from(day.plusDays(Math.max(-2,Math.min(2,adjustment))));
        return date.get(ChronoField.DAY_OF_MONTH)+" "+MONTHS[date.get(ChronoField.MONTH_OF_YEAR)-1]+" "+date.get(ChronoField.YEAR_OF_ERA)+" AH";
    }
}
