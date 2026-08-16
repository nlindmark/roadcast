package se.roadcast.core.location

import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.TravelState

/**
 * Turns raw GPS samples into [TravelState] with explicit unknown motion when the
 * device is effectively stationary, plus light bearing smoothing while moving.
 */
class TravelMotionFilter(
    private val minMovingSpeedMps: Double = 1.4,
    private val minMoveMetersForDerivedBearing: Double = 8.0,
    private val bearingSmoothingAlpha: Double = 0.35,
) {
    private var lastPosition: GeoPoint? = null
    private var lastTimestampMillis: Long = 0L
    private var smoothedBearing: Double? = null

    fun reset() {
        lastPosition = null
        lastTimestampMillis = 0L
        smoothedBearing = null
    }

    fun ingest(
        position: GeoPoint,
        timestampEpochMillis: Long,
        accuracyMeters: Double?,
        reportedSpeedMps: Double?,
        reportedBearingDegrees: Double?,
        hasReportedBearing: Boolean,
    ): TravelState {
        val previous = lastPosition
        val previousTimestamp = lastTimestampMillis
        val derivedSpeed = if (previous != null && timestampEpochMillis > previousTimestamp) {
            val distance = GeoMath.haversineMeters(previous, position)
            val dtSeconds = (timestampEpochMillis - previousTimestamp) / 1_000.0
            if (dtSeconds > 0.0) distance / dtSeconds else null
        } else {
            null
        }
        val speed = when {
            reportedSpeedMps != null && reportedSpeedMps >= 0.0 -> reportedSpeedMps
            derivedSpeed != null -> derivedSpeed
            else -> null
        }

        val derivedBearing = if (previous != null) {
            val moved = GeoMath.haversineMeters(previous, position)
            if (moved >= minMoveMetersForDerivedBearing) {
                GeoMath.initialBearingDegrees(previous, position)
            } else {
                null
            }
        } else {
            null
        }

        val rawBearing = when {
            hasReportedBearing && reportedBearingDegrees != null ->
                normalizeDegrees(reportedBearingDegrees)
            else -> derivedBearing
        }

        val moving = (speed ?: 0.0) >= minMovingSpeedMps
        val bearing = if (moving && rawBearing != null) {
            val blended = blendBearing(smoothedBearing, rawBearing)
            smoothedBearing = blended
            blended
        } else {
            // Stationary or unknown motion: do not invent a heading for ranking.
            null
        }

        lastPosition = position
        lastTimestampMillis = timestampEpochMillis

        return TravelState(
            currentPosition = position,
            bearingDegrees = bearing,
            speedMetersPerSecond = speed,
            accuracyMeters = accuracyMeters,
            timestampEpochMillis = timestampEpochMillis,
        )
    }

    private fun blendBearing(previous: Double?, next: Double): Double {
        if (previous == null) return next
        val delta = shortestSignedDelta(previous, next)
        return normalizeDegrees(previous + bearingSmoothingAlpha * delta)
    }

    private fun shortestSignedDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
}
