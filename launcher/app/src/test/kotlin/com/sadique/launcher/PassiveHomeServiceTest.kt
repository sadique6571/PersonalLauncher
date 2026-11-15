package com.sadique.launcher

import java.util.Collections
import org.junit.Test
import org.junit.Assert.*

class PassiveHomeServiceTest {
    @Test fun redirectsOnlyConfiguredHomeActivity() {
        assertTrue(PassiveHomeService.matchesHome("stock", Collections.singleton("stock.Home"), "stock", "stock.Home"))
        assertFalse(PassiveHomeService.matchesHome("stock", Collections.singleton("stock.Home"), "stock", "stock.Settings"))
        assertFalse(PassiveHomeService.matchesHome("stock", Collections.singleton("stock.Home"), "other", "stock.Home"))
        assertFalse(PassiveHomeService.matchesHome("", Collections.singleton("stock.Home"), "stock", "stock.Home"))
        assertFalse(PassiveHomeService.matchesHome("stock", Collections.singleton("stock.Home"), "stock", null))
    }
}
