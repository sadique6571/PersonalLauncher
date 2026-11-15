package com.sadique.launcher

import org.junit.Test
import org.junit.Assert.*

class PersonalHomeModelTest {
    @Test fun twelveHourTimeHandlesMidnightNoonAndEvening() {
        assertEquals("12:00 AM", PersonalHomeModel.time(0, 0))
        assertEquals("09:05 AM", PersonalHomeModel.time(9, 5))
        assertEquals("12:00 PM", PersonalHomeModel.time(12, 0))
        assertEquals("09:41 PM", PersonalHomeModel.time(21, 41))
        assertEquals("11:59 PM", PersonalHomeModel.time(23, 59))
    }

    @Test fun midnightHasMoonAndNoDaylight() {
        assertEquals(0f, PersonalHomeModel.daylight(0), 0f)
        assertEquals(0f, PersonalHomeModel.daylight(23 * 60), 0f)
    }
    @Test fun dawnAndDuskFadeInsteadOfJumping() {
        assertEquals(0f, PersonalHomeModel.daylight(330), 0f)
        assertEquals(0.5f, PersonalHomeModel.daylight(390), 0.001f)
        assertEquals(1f, PersonalHomeModel.daylight(450), 0f)
        assertEquals(0.5f, PersonalHomeModel.daylight(1110), 0.001f)
        assertEquals(0f, PersonalHomeModel.daylight(1170), 0f)
    }
    @Test fun noonIsDayAndTimesWrap() {
        assertEquals(1f, PersonalHomeModel.daylight(720), 0f)
        assertEquals(PersonalHomeModel.daylight(720), PersonalHomeModel.daylight(2160), 0f)
    }
    @Test fun narrowPhonesDoNotOverflowWithTvColumnPreference() {
        assertEquals(2, PersonalHomeModel.columns(320, 6))
        assertEquals(3, PersonalHomeModel.columns(393, 6))
        assertEquals(6, PersonalHomeModel.columns(960, 6))
        assertEquals(4, PersonalHomeModel.columns(960, 4))
        assertEquals(2, PersonalHomeModel.columns(379, 0, true))
        assertEquals(3, PersonalHomeModel.columns(379, 0, false))
        assertEquals(5, PersonalHomeModel.columns(896, 0, true))
    }
    @Test fun profileNamesTrimAndLimitWholeUnicodeCharacters() {
        assertEquals("", PersonalHomeModel.name(null))
        assertEquals("Sadique", PersonalHomeModel.name("  Sadique  "))
        val emoji = "\uD83D\uDE00"
        val longName = String(CharArray(40)).replace("\u0000", emoji)
        assertEquals(64, PersonalHomeModel.name(longName).length)
    }
}
