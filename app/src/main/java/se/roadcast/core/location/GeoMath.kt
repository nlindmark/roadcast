package se.roadcast.core.location

import se.roadcast.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EarthRadiusMeters = 6_371_000.0

    fun haversineMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val dLat = lat2 - lat1
        val dLon = (to.longitude - from.longitude).toRadians()
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EarthRadiusMeters * asin(min(1.0, sqrt(a)))
    }

    fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val dLon = (to.longitude - from.longitude).toRadians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeDegrees(atan2(y, x).toDegrees())
    }

    fun angularDifferenceDegrees(first: Double, second: Double): Double =
        abs((normalizeDegrees(first - second + 180.0) - 180.0))

    fun isAhead(origin: GeoPoint, headingDegrees: Double, point: GeoPoint, toleranceDegrees: Double = 90.0): Boolean =
        angularDifferenceDegrees(headingDegrees, initialBearingDegrees(origin, point)) <= toleranceDegrees

    fun project(origin: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angular = distanceMeters / EarthRadiusMeters
        val bearing = bearingDegrees.toRadians()
        val lat1 = origin.latitude.toRadians()
        val lon1 = origin.longitude.toRadians()
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return GeoPoint(lat2.toDegrees(), normalizeLongitude(lon2.toDegrees()))
    }

    fun distanceToFiniteCorridorMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val referenceLatitude = ((start.latitude + end.latitude + point.latitude) / 3).toRadians()
        fun xy(value: GeoPoint): Pair<Double, Double> = Pair(
            value.longitude.toRadians() * EarthRadiusMeters * cos(referenceLatitude),
            value.latitude.toRadians() * EarthRadiusMeters,
        )
        val (px, py) = xy(point)
        val (ax, ay) = xy(start)
        val (bx, by) = xy(end)
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return haversineMeters(point, start)
        val t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / lengthSquared))
        return sqrt((px - (ax + t * dx)).let { it * it } + (py - (ay + t * dy)).let { it * it })
    }

    fun etaUntilPassingSeconds(
        origin: GeoPoint,
        headingDegrees: Double?,
        speedMetersPerSecond: Double?,
        point: GeoPoint,
    ): Double? {
        if (headingDegrees == null || speedMetersPerSecond == null || speedMetersPerSecond <= 0.0) return null
        val distance = haversineMeters(origin, point)
        val difference = angularDifferenceDegrees(headingDegrees, initialBearingDegrees(origin, point))
        val alongTrack = distance * cos(difference.toRadians())
        return max(0.0, alongTrack / speedMetersPerSecond)
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    private fun normalizeLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0
    private fun Double.toRadians(): Double = this * PI / 180.0
    private fun Double.toDegrees(): Double = this * 180.0 / PI
}
