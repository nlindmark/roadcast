package se.roadcast.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.GeoPoint

class TravelMotionFilterTest {
    private val filter = TravelMotionFilter(
        minMovingSpeedMps = 1.4,
        minMoveMetersForDerivedBearing = 8.0,
        bearingSmoothingAlpha = 1.0,
    )

    @Test
    fun `stationary samples clear bearing`() {
        val first = filter.ingest(
            position = GeoPoint(57.70, 11.97),
            timestampEpochMillis = 1_000L,
            accuracyMeters = 5.0,
            reportedSpeedMps = 0.2,
            reportedBearingDegrees = 90.0,
            hasReportedBearing = true,
        )
        assertNull(first.bearingDegrees)
        assertEquals(0.2, first.speedMetersPerSecond!!, 0.001)
    }

    @Test
    fun `moving samples keep and smooth bearing`() {
        val a = filter.ingest(
            position = GeoPoint(57.7000, 11.9700),
            timestampEpochMillis = 1_000L,
            accuracyMeters = 4.0,
            reportedSpeedMps = 12.0,
            reportedBearingDegrees = 10.0,
            hasReportedBearing = true,
        )
        val b = filter.ingest(
            position = GeoPoint(57.7010, 11.9700),
            timestampEpochMillis = 2_500L,
            accuracyMeters = 4.0,
            reportedSpeedMps = 12.0,
            reportedBearingDegrees = 20.0,
            hasReportedBearing = true,
        )
        assertEquals(10.0, a.bearingDegrees!!, 0.1)
        assertEquals(20.0, b.bearingDegrees!!, 0.1)
    }

    @Test
    fun `derived bearing appears after meaningful movement`() {
        filter.ingest(
            position = GeoPoint(57.7000, 11.9700),
            timestampEpochMillis = 1_000L,
            accuracyMeters = 5.0,
            reportedSpeedMps = 10.0,
            reportedBearingDegrees = null,
            hasReportedBearing = false,
        )
        val second = filter.ingest(
            position = GeoPoint(57.7010, 11.9700),
            timestampEpochMillis = 2_000L,
            accuracyMeters = 5.0,
            reportedSpeedMps = 10.0,
            reportedBearingDegrees = null,
            hasReportedBearing = false,
        )
        assertNotNull(second.bearingDegrees)
        assertTrue(second.bearingDegrees!! in 0.0..20.0)
    }
}
