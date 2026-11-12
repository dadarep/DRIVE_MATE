package com.drivemate.app

import kotlin.math.*

/** Course over ground, never a guessed north heading or a route-to-destination bearing. */
internal class MovementHeading {
    private var anchor: GeoPoint? = null
    private var anchorAt = 0L
    var heading: Float? = null; private set

    fun sample(point: GeoPoint, at: Long, accuracy: Float, speed: Float?, providerBearing: Float?): Float? {
        if (!validMapPoint(point) || !accuracy.isFinite() || accuracy > 50 || at <= anchorAt) return heading
        val previous = anchor
        val dt = (at-anchorAt)/1000.0
        val stationary = speed != null && speed < 0.8f
        if (previous == null || dt > 10 || stationary) {
            anchor=point; anchorAt=at
            if (!stationary && validBearing(providerBearing) && speed != null && speed >= 1.5f) heading=normalize(providerBearing!!)
            return heading
        }
        val meters=distance(previous,point)
        val minimum=max(if(speed == null) 8.0 else 3.0, min(10.0,accuracy*0.5))
        val maximum=max(50.0,(speed ?: 15f)*dt*3+accuracy*2)
        if(meters > maximum) { anchor=point;anchorAt=at;return heading }
        if(meters >= minimum) {
            val a=Math.toRadians(previous.lat);val b=Math.toRadians(point.lat)
            val lon=Math.toRadians(point.lon-previous.lon)
            heading=normalize(Math.toDegrees(atan2(sin(lon)*cos(b),cos(a)*sin(b)-sin(a)*cos(b)*cos(lon))).toFloat())
            anchor=point;anchorAt=at
        } else if(heading == null && validBearing(providerBearing) && speed != null && speed >= 1.5f) {
            heading=normalize(providerBearing!!)
        }
        return heading
    }
    private fun validBearing(value: Float?) = value != null && value.isFinite()
    private fun normalize(value: Float) = ((value%360)+360)%360
    private fun distance(a: GeoPoint,b: GeoPoint): Double {
        val y=Math.toRadians(b.lat-a.lat);val x=Math.toRadians(b.lon-a.lon)
        val h=sin(y/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(x/2).pow(2)
        return 12742000*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
}
