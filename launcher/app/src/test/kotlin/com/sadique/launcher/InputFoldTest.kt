package com.sadique.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFoldTest {
    // TvInputManager's INPUT_STATE_* values: CONNECTED 0, CONNECTED_STANDBY 1, DISCONNECTED 2.
    @Test fun aConnectedHdmiPortStays() {
        assertFalse(InputFold.folds(true, 0))
    }

    @Test fun anEmptyPortReportingStandbyFolds() {
        assertTrue(InputFold.folds(true, 1))
    }

    @Test fun aDisconnectedPortFolds() {
        assertTrue(InputFold.folds(true, 2))
    }

    @Test fun anHdmiRowWithNoStateStays() {
        assertFalse(InputFold.folds(true, -1))
    }

    @Test fun anAvJackAlwaysFolds() {
        assertTrue(InputFold.folds(false, -1))
        assertTrue(InputFold.folds(false, 0))
    }
}
