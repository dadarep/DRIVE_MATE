package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class MapCameraPolicyTest {
    @Test fun recenterRestoresFollowAfterOverviewAndManualPan() {
        val camera=MapCameraPolicy()
        camera.overview(); assertFalse(camera.following)
        camera.recenter(); assertTrue(camera.following)
        camera.gesture(); assertFalse(camera.following)
        camera.recenter(); assertTrue(camera.following)
    }
    @Test fun routeFitIncludesActualDestinationAndGpsBeyondSnappedRoadEnds() {
        val start=GeoPoint(37.49,127.02)
        val end=GeoPoint(37.50,127.03)
        val current=GeoPoint(37.48,127.01)
        val destination=GeoPoint(37.51,127.04)
        assertEquals(listOf(start,end,current,destination),overviewPoints(listOf(start,end),current,destination))
    }
    @Test fun invalidCoordinatesCannotSendCameraToNullOrInfiniteWorld() {
        assertEquals(listOf(GeoPoint(37.5,127.0)),overviewPoints(listOf(GeoPoint(Double.NaN,127.0),GeoPoint(91.0,0.0)),GeoPoint(37.5,127.0),GeoPoint(0.0,181.0)))
    }
    @Test fun distantTripKeepsBothEndsInsteadOfSilentlyCenteringOnlyDestination() {
        val seoul=GeoPoint(37.5,127.0)
        val busan=GeoPoint(35.1,129.0)
        assertEquals(listOf(seoul,busan),overviewPoints(emptyList(),seoul,busan))
    }
}
