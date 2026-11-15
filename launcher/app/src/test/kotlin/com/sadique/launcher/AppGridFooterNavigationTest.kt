package com.sadique.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppGridFooterNavigationTest {
    @Test fun footerIsReachableFromEveryAppInLastRowEvenWhenNotYetBound() {
        for (columns in 2..6) for (count in 1 until 30) for (pos in 0 until count) {
            val next = AppGridNavigation.verticalWithFooter(pos, count, columns, true, true)
            if (pos / columns == (count - 1) / columns) assertEquals(count, next)
            else assertEquals(AppGridNavigation.vertical(pos, count, columns, true), next)
        }
    }

    @Test fun disabledFooterAndUpwardNavigationKeepExistingGridBehaviour() {
        for (count in 1 until 30) for (pos in 0 until count) {
            assertEquals(AppGridNavigation.vertical(pos, count, 4, true), AppGridNavigation.verticalWithFooter(pos, count, 4, true, false))
            assertEquals(AppGridNavigation.vertical(pos, count, 4, false), AppGridNavigation.verticalWithFooter(pos, count, 4, false, true))
        }
    }
}
