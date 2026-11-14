package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputNamesTest {
    @Test fun philipsTypeBecomesAnEmoji() {
        assertEquals("🎮 Xbox", InputNames.tidy("Xbox&GAME_CONSOLE"))
        assertEquals("🎮 Switch", InputNames.tidy("Switch &GAME_CONSOLE"))
        assertEquals("💻 Ordinateur", InputNames.tidy("Ordinateur&COMPUTER"))
    }

    @Test fun anUnknownTypeLeavesTheNameAlone() {
        assertEquals("Box", InputNames.tidy("Box&SOMETHING_NEW"))
    }

    @Test fun aTypeWithNoNameIsJustItsEmoji() {
        assertEquals("🎮", InputNames.tidy("&GAME_CONSOLE"))
    }

    @Test fun theTextNullAndBlanksAreNoName() {
        assertNull(InputNames.tidy("null"))
        assertNull(InputNames.tidy("  "))
        assertNull(InputNames.tidy(null))
    }

    @Test fun otherNamesAreKept() {
        assertEquals("MacBook Pro", InputNames.tidy("MacBook Pro"))
        assertEquals("Tom & Jerry", InputNames.tidy("Tom & Jerry"))
        assertEquals("AT&T", InputNames.tidy("AT&T"))
        assertEquals("R&D", InputNames.tidy("R&D"))
    }
}
