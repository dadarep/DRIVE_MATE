package com.drivemate.app

/** Camera state is separate from route data: recenter must keep following after a route is set. */
internal class MapCameraPolicy {
    var following = true; private set
    fun overview() { following = false }
    fun recenter() { following = true }
    fun gesture() { following = false }
    fun zoom() = if (following) 16.8 else 14.5
}

internal fun validMapPoint(point: GeoPoint) = point.lat.isFinite() && point.lon.isFinite() && point.lat in -85.0..85.0 && point.lon in -180.0..180.0

internal fun overviewPoints(route: List<GeoPoint>, current: GeoPoint?, destination: GeoPoint?): List<GeoPoint> =
    (route + listOfNotNull(current, destination)).filter(::validMapPoint).distinct()
