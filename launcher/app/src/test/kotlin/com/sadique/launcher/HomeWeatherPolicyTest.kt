package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWeatherPolicyTest {
    @Test fun unvalidatedInternetRouteStillAllowsProviderRequest() {
        assertTrue(HomeWeatherPolicy.canAttempt(true, true, false))
        assertTrue(HomeWeatherPolicy.canAttempt(true, true, true))
        assertFalse(HomeWeatherPolicy.canAttempt(false, true, true))
        assertFalse(HomeWeatherPolicy.canAttempt(true, false, false))
    }

    @Test fun cacheMustMatchCityUnitsAndAValidRecentTimestamp() {
        assertTrue(HomeWeatherPolicy.fresh("24°C", "a,C", "a,C", 1000, 2000, 2000))
        assertFalse(HomeWeatherPolicy.fresh("24°C", "a,C", "b,C", 1000, 2000, 2000))
        assertFalse(HomeWeatherPolicy.fresh("24°C", "a,C", "a,F", 1000, 2000, 2000))
        assertFalse(HomeWeatherPolicy.fresh("24°C", "a,C", "a,C", 2000, 1000, 2000))
        assertFalse(HomeWeatherPolicy.fresh("24°C", "a,C", "a,C", 1000, 3000, 2000))
        assertFalse(HomeWeatherPolicy.fresh("", "a,C", "a,C", 1000, 2000, 2000))
    }
}
