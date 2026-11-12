package com.kevin.powercurve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCurveTest {
    private val curve = PowerCurve()

    @Test
    fun fourHourRideKeepsFullAverageBeyondTheRollingBuffer() {
        repeat(3600) { curve.addSample(400) }
        repeat(3 * 3600) { curve.addSample(200) }
        val result = curve.snapshot.value
        assertEquals(14400L, result.activeSeconds)
        assertEquals(250, result.rideAverage)
        assertEquals(PowerCurve.Point(14400, 250), result.points.last())
        assertEquals(List(PowerCurveConfig.defaults.size) { 400 }, result.best)
        assertEquals(PowerCurveConfig.defaults.size + 1, result.points.size)
        assertEquals("4h", PowerCurve.durationLabel(result.activeSeconds))
    }

    @Test
    fun endpointIsUniqueAtConfiguredDurationsAndContinuesGrowing() {
        repeat(3600) { curve.addSample(300) }
        val exact = curve.snapshot.value
        assertEquals(1, exact.points.count { it.seconds == 3600L })
        assertEquals(PowerCurveConfig.defaults.size, exact.points.size)
        curve.addSample(0)
        val extended = curve.snapshot.value
        assertEquals(3601L, extended.points.last().seconds)
        assertEquals(PowerCurveConfig.defaults.size + 1, extended.points.size)
        assertEquals("1h1s", PowerCurve.durationLabel(extended.activeSeconds))
    }

    @Test
    fun shortRideIncludesEndpointWithoutInventingLongerEfforts() {
        curve.addSample(100)
        assertEquals(listOf(PowerCurve.Point(1, 100)), curve.snapshot.value.points)
        repeat(14) { curve.addSample(200) }
        val result = curve.snapshot.value
        assertEquals(listOf(PowerCurve.Point(15, 193)), result.points)
        assertEquals(193, result.best.first())
        assertTrue(result.best.drop(1).all { it == null })
    }

    @Test
    fun resetClearsRideDurationTotalsAndCurve() {
        repeat(60) { curve.addSample(500) }
        curve.reset()
        val empty = curve.snapshot.value
        assertEquals(0L, empty.activeSeconds)
        assertNull(empty.rideAverage)
        assertTrue(empty.points.isEmpty())
        curve.addSample(100)
        assertEquals(PowerCurve.Point(1, 100), curve.snapshot.value.points.single())
    }

    @Test
    fun changingIntervalsRecalculatesPastEffortsWithoutResettingRide() {
        repeat(30) { curve.addSample(600) }
        repeat(90) { curve.addSample(200) }
        curve.configure(PowerCurveConfig.parse("10s, 45s, 90s, 3min"))
        var result = curve.snapshot.value
        assertEquals(listOf(600, 467, 333, null), result.best)
        assertEquals(120L, result.activeSeconds)
        assertEquals(300, result.rideAverage)
        repeat(60) { curve.addSample(100) }
        result = curve.snapshot.value
        assertEquals(listOf(600, 467, 333, 233), result.best)
        curve.configure(PowerCurveConfig.parse("5s, 2min"))
        assertEquals(listOf(600, 300), curve.snapshot.value.best)
        curve.reset()
        assertEquals(listOf(5, 120), curve.snapshot.value.intervals.map { it.seconds })
        assertTrue(curve.snapshot.value.best.all { it == null })
    }
}
