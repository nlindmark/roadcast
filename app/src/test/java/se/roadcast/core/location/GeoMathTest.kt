package se.roadcast.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.GeoPoint

class GeoMathTest {
    @Test
    fun `haversine returns known distance`() {
        val distance = GeoMath.haversineMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertEquals(111_195.0, distance, 100.0)
    }

    @Test
    fun `bearing and normalized difference handle north crossing`() {
        assertEquals(90.0, GeoMath.initialBearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 0.1)
        assertEquals(20.0, GeoMath.angularDifferenceDegrees(350.0, 10.0), 0.001)
    }

    @Test
    fun `ahead distinguishes forward and passed points`() {
        val origin = GeoPoint(57.70, 12.00)
        assertTrue(GeoMath.isAhead(origin, 0.0, GeoPoint(57.71, 12.00)))
        assertFalse(GeoMath.isAhead(origin, 0.0, GeoPoint(57.69, 12.00)))
    }

    @Test
    fun `corridor distance projects to finite segment`() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(0.0, 0.1)
        assertTrue(GeoMath.distanceToFiniteCorridorMeters(GeoPoint(0.01, 0.05), start, end) in 1_100.0..1_125.0)
        assertTrue(GeoMath.distanceToFiniteCorridorMeters(GeoPoint(0.0, 0.2), start, end) > 11_000.0)
    }

    @Test
    fun `eta is based on forward projection and speed`() {
        val eta = GeoMath.etaUntilPassingSeconds(
            GeoPoint(0.0, 0.0),
            90.0,
            10.0,
            GeoPoint(0.0, 0.01),
        )
        assertEquals(111.2, eta ?: error("Expected ETA"), 1.0)
        assertEquals(0.0, GeoMath.etaUntilPassingSeconds(
            GeoPoint(0.0, 0.0), 90.0, 10.0, GeoPoint(0.0, -0.01),
        ) ?: error("Expected passed ETA"), 0.001)
    }
}
