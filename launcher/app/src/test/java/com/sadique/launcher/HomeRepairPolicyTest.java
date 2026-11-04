package com.sadique.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeRepairPolicyTest {
    @Test public void correctDefaultDoesNotConsumeTheRepairBudget() {
        HomeRepairPolicy policy=new HomeRepairPolicy();
        assertFalse(policy.allow(0,true)); assertTrue(policy.allow(0,false));
    }
    @Test public void repeatedCallbacksAreBoundedAndCooldownIsEnforced() {
        HomeRepairPolicy policy=new HomeRepairPolicy();
        assertTrue(policy.allow(100,false)); assertFalse(policy.allow(200,false));
        assertTrue(policy.allow(60_100,false)); assertFalse(policy.allow(120_100,false));
    }
}
