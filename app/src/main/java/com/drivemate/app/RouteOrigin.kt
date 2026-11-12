package com.drivemate.app

/** A recent fix can seed a clearly labelled preview, but is never presented as live GPS. */
internal fun recentRouteOrigin(latitude: String?, longitude: String?, fixAt: Long, now: Long): GeoPoint? {
    if(fixAt <= 0 || now-fixAt !in 0..1800000L) return null
    val point=GeoPoint(latitude?.toDoubleOrNull() ?: return null,longitude?.toDoubleOrNull() ?: return null)
    return point.takeIf(::validMapPoint)
}
