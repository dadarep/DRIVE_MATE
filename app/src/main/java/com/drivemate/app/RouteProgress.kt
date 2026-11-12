package com.drivemate.app

import kotlin.math.cos
import kotlin.math.hypot

/** Drops the road already driven. Progress only moves forward, so a route that doubles back is not cut early. */
internal class RouteProgress {
    private var passed = 0
    fun reset() { passed = 0 }

    fun ahead(route: List<GeoPoint>, car: GeoPoint?): List<GeoPoint> {
        if (passed > route.size - 2) passed = 0
        val rest = route.drop(passed)
        if (car == null || route.size < 2) return rest
        var best = -1; var bestDistance = Double.MAX_VALUE; var bestPoint: GeoPoint = car
        var searched = 0.0
        for (i in passed until route.size - 1) {
            val (point, distance) = project(route[i], route[i + 1], car)
            if (distance < bestDistance) { best = i; bestDistance = distance; bestPoint = point }
            searched += meters(route[i], route[i + 1])
            if (searched > SEARCH_METERS) break
        }
        if (best < 0 || bestDistance > ON_ROUTE_METERS) return rest
        passed = best
        return listOf(bestPoint) + route.drop(best + 1)
    }

    private fun project(a: GeoPoint, b: GeoPoint, p: GeoPoint): Pair<GeoPoint, Double> {
        val k = cos(Math.toRadians(p.lat)) * METERS_PER_DEGREE
        val ax = (a.lon - p.lon) * k; val ay = (a.lat - p.lat) * METERS_PER_DEGREE
        val bx = (b.lon - p.lon) * k; val by = (b.lat - p.lat) * METERS_PER_DEGREE
        val dx = bx - ax; val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val x = ax + t * dx; val y = ay + t * dy
        return GeoPoint(p.lat + y / METERS_PER_DEGREE, p.lon + x / k) to hypot(x, y)
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_320.0
        private const val ON_ROUTE_METERS = 40.0
        private const val SEARCH_METERS = 600.0
        fun meters(a: GeoPoint, b: GeoPoint) =
            hypot((b.lon - a.lon) * cos(Math.toRadians(a.lat)) * METERS_PER_DEGREE, (b.lat - a.lat) * METERS_PER_DEGREE)
    }
}
