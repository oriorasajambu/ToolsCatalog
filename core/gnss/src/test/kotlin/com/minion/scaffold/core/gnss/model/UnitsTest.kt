package com.minion.scaffold.core.gnss.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    /**
     * The conversion factors are exact by definition, so they are asserted exactly.
     *
     * A knot is exactly 1852 m/h, an international mile is exactly 1609.344 m, and a foot is exactly
     * 0.3048 m — these are definitions rather than measurements, so a tolerance would only hide a
     * typo. 100 km/h is 62.137 mph and 53.996 knots, which are the numbers on any conversion chart.
     */
    @Test
    fun `speed conversions match their definitions`() {
        val hundredKilometersPerHour = 100.0 / 3.6

        assertEquals(100.0, SpeedUnit.KilometersPerHour.fromMetersPerSecond(hundredKilometersPerHour), 1e-12)
        assertEquals(62.1371192, SpeedUnit.MilesPerHour.fromMetersPerSecond(hundredKilometersPerHour), 1e-6)
        assertEquals(53.9956803, SpeedUnit.Knots.fromMetersPerSecond(hundredKilometersPerHour), 1e-6)

        // One knot is one nautical mile per hour, which is 1.852 km/h exactly.
        val oneKnot = 1852.0 / 3600.0
        assertEquals(1.0, SpeedUnit.Knots.fromMetersPerSecond(oneKnot), 1e-12)
        assertEquals(1.852, SpeedUnit.KilometersPerHour.fromMetersPerSecond(oneKnot), 1e-12)
    }

    @Test
    fun `altitude and journey conversions match their definitions`() {
        assertEquals(1000.0, DistanceUnit.Metric.altitudeFromMeters(1000.0), 1e-12)
        assertEquals(3280.839895, DistanceUnit.Imperial.altitudeFromMeters(1000.0), 1e-6)

        assertEquals(1.0, DistanceUnit.Metric.journeyFromMeters(1000.0), 1e-12)
        assertEquals(1.0, DistanceUnit.Imperial.journeyFromMeters(1609.344), 1e-12)
    }

    /**
     * Altitude and journey are two conversions, not one scale factor.
     *
     * Nobody measures a mountain in miles or a drive in feet, so imperial altitude is feet while
     * imperial distance is miles. Worth pinning, because collapsing them into one factor is the
     * obvious simplification and gives an altitude in miles.
     */
    @Test
    fun `imperial altitude is feet and imperial journey is miles`() {
        assertTrue(DistanceUnit.Imperial.altitudeFromMeters(100.0) > 300.0)
        assertTrue(DistanceUnit.Imperial.journeyFromMeters(100.0) < 1.0)
    }

    @Test
    fun `decimal coordinates carry their sign`() {
        val formatted = CoordinateFormatter.format(-33.8688, 151.2093, CoordinateFormat.Decimal)

        assertEquals("-33.868800°, 151.209300°", formatted)
    }

    /**
     * **The hemisphere is the thing worth pinning.**
     *
     * Decimal degrees carry the sign and DMS carries a letter, and converting between them is where a
     * southern latitude quietly becomes a northern one. The EXIF tool met the same class of bug from
     * the other direction, where GPS magnitudes are stored unsigned with a separate reference byte.
     */
    @Test
    fun `degrees minutes seconds put each hemisphere on the right side`() {
        assertEquals(
            "3°35'42.7\"N 98°40'19.9\"E",
            CoordinateFormatter.format(3.5952, 98.6722, CoordinateFormat.DegreesMinutesSeconds),
        )

        // Southern and western, which a magnitude-only implementation would place in the wrong
        // quarter of the planet while still looking entirely well-formed.
        assertEquals(
            "33°52'07.7\"S 151°12'33.5\"W",
            CoordinateFormatter.format(-33.8688, -151.2093, CoordinateFormat.DegreesMinutesSeconds),
        )
    }

    /**
     * Rounding the seconds carries into the minutes, and the minutes into the degrees.
     *
     * A latitude a hair under a whole degree rounds its seconds to 60.0, which is not a value anyone
     * writes — `2°59'60.0"N` instead of `3°00'00.0"N`. It happens on roughly one position in nine
     * hundred, which is often enough to be noticed and rare enough to survive review.
     */
    @Test
    fun `seconds rounding carries rather than printing sixty`() {
        val formatted = CoordinateFormatter.format(2.9999999, 0.0, CoordinateFormat.DegreesMinutesSeconds)

        assertTrue("carried wrongly: $formatted", "60.0" !in formatted)
        assertEquals("3°00'00.0\"N 0°00'00.0\"E", formatted)
    }

    @Test
    fun `the equator and prime meridian format without a sign`() {
        assertEquals(
            "0°00'00.0\"N 0°00'00.0\"E",
            CoordinateFormatter.format(0.0, 0.0, CoordinateFormat.DegreesMinutesSeconds),
        )
    }
}
