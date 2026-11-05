package com.sadique.launcher;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeWeatherPolicyTest {
    @Test public void unvalidatedInternetRouteStillAllowsProviderRequest() {
        assertTrue(HomeWeatherPolicy.canAttempt(true,true,false));
        assertTrue(HomeWeatherPolicy.canAttempt(true,true,true));
        assertFalse(HomeWeatherPolicy.canAttempt(false,true,true));
        assertFalse(HomeWeatherPolicy.canAttempt(true,false,false));
    }
    @Test public void cacheMustMatchCityUnitsAndAValidRecentTimestamp() {
        assertTrue(HomeWeatherPolicy.fresh("24°C","a,C","a,C",1000,2000,2000));
        assertFalse(HomeWeatherPolicy.fresh("24°C","a,C","b,C",1000,2000,2000));
        assertFalse(HomeWeatherPolicy.fresh("24°C","a,C","a,F",1000,2000,2000));
        assertFalse(HomeWeatherPolicy.fresh("24°C","a,C","a,C",2000,1000,2000));
        assertFalse(HomeWeatherPolicy.fresh("24°C","a,C","a,C",1000,3000,2000));
        assertFalse(HomeWeatherPolicy.fresh("","a,C","a,C",1000,2000,2000));
    }
}
