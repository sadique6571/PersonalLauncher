package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRepairPolicyTest {
    @Test fun correctDefaultDoesNotConsumeTheRepairBudget() {
        val policy = HomeRepairPolicy()
        assertFalse(policy.allow(0, true))
        assertTrue(policy.allow(0, false))
    }

    @Test fun repeatedCallbacksAreBoundedAndCooldownIsEnforced() {
        val policy = HomeRepairPolicy()
        assertTrue(policy.allow(100, false))
        assertFalse(policy.allow(200, false))
        assertTrue(policy.allow(60_100, false))
        assertFalse(policy.allow(120_100, false))
    }
}
