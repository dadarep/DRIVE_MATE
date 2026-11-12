package com.drivemate.app

import kotlin.math.*

/** A provider callback may describe an older destination, even when its name is identical. */
internal fun matchesNativeDestination(selected: Destination?, latitude: Double?, longitude: Double?): Boolean {
    val target = selected?.point ?: return false
    val provider = if(latitude != null && longitude != null) GeoPoint(latitude,longitude) else return false
    if(!validMapPoint(target) || !validMapPoint(provider)) return false
    val dy=Math.toRadians(provider.lat-target.lat)
    val dx=Math.toRadians(provider.lon-target.lon)
    val a=sin(dy/2).pow(2)+cos(Math.toRadians(target.lat))*cos(Math.toRadians(provider.lat))*sin(dx/2).pow(2)
    return 12742000.0*asin(sqrt(a.coerceIn(0.0,1.0))) <= 25.0
}
