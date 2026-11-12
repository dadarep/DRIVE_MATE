package com.drivemate.app
import org.junit.Assert.*
import org.junit.Test

class RouteOriginTest {
    @Test fun recentStationaryFixCanSeedRouteWhileWaitingForFreshGps() {
        assertEquals(GeoPoint(37.5,127.0),recentRouteOrigin("37.5","127.0",1000,301000))
    }
    @Test fun oldFutureMissingOrInvalidFixesCannotBeUsed() {
        assertNull(recentRouteOrigin("37.5","127",1000,1801001))
        assertNull(recentRouteOrigin("37.5","127",1000,999))
        assertNull(recentRouteOrigin(null,"127",1000,1001))
        assertNull(recentRouteOrigin("NaN","127",1000,1001))
        assertNull(recentRouteOrigin("91","127",1000,1001))
    }
}
