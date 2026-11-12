package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class DestinationMatchTest {
    private val destination=Destination("강남역",GeoPoint(37.49795,127.02762))
    @Test fun oldNativeDestinationCannotSupplyGuidanceForTheNewAppDestination(){
        assertFalse(matchesNativeDestination(destination,37.48544722,127.03636666))
    }
    @Test fun matchingCoordinatesWorkWithoutDependingOnProviderSpelling(){
        assertTrue(matchesNativeDestination(destination,37.49796,127.02763))
    }
    @Test fun unknownOrInvalidCoordinatesCannotMatch(){
        assertFalse(matchesNativeDestination(destination,null,null))
        assertFalse(matchesNativeDestination(destination,Double.NaN,127.0))
        assertFalse(matchesNativeDestination(null,37.49795,127.02762))
    }
}
