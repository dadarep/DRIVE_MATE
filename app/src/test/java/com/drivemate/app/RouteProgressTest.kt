package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class RouteProgressTest {
    private val route = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.002), GeoPoint(37.502, 127.002))

    @Test fun drivenRoadIsRemovedAndLineStartsAtTheCar() {
        val ahead = RouteProgress().ahead(route, GeoPoint(37.50001, 127.001))
        assertEquals(3, ahead.size)
        assertEquals(37.5, ahead[0].lat, 1e-7)
        assertEquals(127.001, ahead[0].lon, 1e-7)
        assertEquals(route.drop(1), ahead.drop(1))
    }

    @Test fun progressNeverMovesBackAlongTheRoute() {
        val p = RouteProgress()
        p.ahead(route, GeoPoint(37.501, 127.002))
        val ahead = p.ahead(route, GeoPoint(37.5, 127.0005))
        assertEquals(route.last(), ahead.last())
        assertTrue(ahead.none { it == route[0] })
    }

    @Test fun offRouteOrUnknownPositionKeepsTheRemainingRoute() {
        assertEquals(route, RouteProgress().ahead(route, GeoPoint(37.51, 127.01)))
        assertEquals(route, RouteProgress().ahead(route, null))
    }
}
