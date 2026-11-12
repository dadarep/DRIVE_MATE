package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class TripQualityTest {
    @Test fun tooFewSamplesCannotConfirmZeroCandidates() {
        assertTrue(TripQuality.insufficient(5, 0, 60000))
        assertFalse(TripQuality.insufficient(200, 0, 60000))
    }
    @Test fun mostlyMissingTripIsInsufficient() {
        assertTrue(TripQuality.insufficient(200, 40000, 60000))
        assertFalse(TripQuality.insufficient(200, 10000, 60000))
    }
    @Test fun shortGapsAreNotShown() {
        assertNull(TripQuality.gapLabel(2500))
        assertEquals("12초", TripQuality.gapLabel(12400))
        assertEquals("1분 5초", TripQuality.gapLabel(65000))
    }
}
