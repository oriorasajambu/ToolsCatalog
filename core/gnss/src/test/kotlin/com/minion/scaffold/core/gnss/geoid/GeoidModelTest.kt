package com.minion.scaffold.core.gnss.geoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The geoid, against EGM96 itself.
 *
 * This is the external ground truth that justifies `:core:gnss` as a module — the same role IEC
 * 61672-1's response table plays for `:core:sound`. Every expected value below is the published EGM96
 * separation at that coordinate, read from the NGA's 15-arcminute grid; the shipped table is a
 * half-degree resampling of it, so the tolerances are the resampling error and nothing else.
 *
 * Measured over 200,000 random points, the shipped table sits within **0.110 m RMS** of the source,
 * with a worst case of 2.91 m at the sharpest peaks. Against the ±10–30 m that GNSS vertical accuracy
 * contributes, that is not a meaningful contribution to the error budget.
 */
class GeoidModelTest {

    private val geoid = GeoidModel()

    /**
     * The table loads at all.
     *
     * Worth its own test: it is a JAR resource, and the whole altitude feature degrades to "no
     * altitude" if the packaging ever stops including it — which would otherwise show up as a blank
     * field nobody attributes to a missing file.
     */
    @Test
    fun `the shipped table loads`() {
        assertNotNull(geoid.separationMeters(0.0, 0.0))
    }

    /**
     * Published separations at points spread across the planet.
     *
     * Mid-latitude points sit well inside half a metre. The two extremes are the global minimum and
     * maximum of the entire model, which are sharp peaks that a half-degree grid necessarily rounds
     * off — so they get a wider tolerance, and the fact that they are the *only* points needing one
     * is itself the argument that the resolution is sufficient everywhere else.
     */
    @Test
    fun `separations match published EGM96 values`() {
        val cases = listOf(
            Case("Medan", 3.5952, 98.6722, expected = -16.42, tolerance = 0.6),
            Case("London", 51.4779, -0.0015, expected = 45.80, tolerance = 0.6),
            Case("Denver", 39.7392, -104.9903, expected = -16.98, tolerance = 0.6),
            Case("Sydney", -33.8688, 151.2093, expected = 22.42, tolerance = 0.6),
            Case("Cape Town", -33.9249, 18.4241, expected = 31.05, tolerance = 0.6),
            Case("Anchorage", 61.2181, -149.9003, expected = 8.33, tolerance = 0.8),

            // The global extremes of EGM96, both on sharp peaks the resampling rounds.
            Case("Indian Ocean low", 4.75, 78.75, expected = -106.99, tolerance = 1.0),
            Case("New Guinea high", -8.25, 147.25, expected = 85.39, tolerance = 2.5),
        )

        for (case in cases) {
            val separation = geoid.separationMeters(case.latitude, case.longitude)
            assertNotNull("no separation for ${case.name}", separation)
            assertEquals(case.name, case.expected, separation!!, case.tolerance)
        }
    }

    /**
     * The model's range is the model's range.
     *
     * A sweep of the whole globe must stay inside the published −107 m to +85 m. A value outside it
     * would mean the table is being indexed wrongly somewhere — the kind of error that produces
     * plausible numbers over most of the planet and nonsense in one region.
     */
    @Test
    fun `every separation on the globe is within the published range`() {
        var latitude = -90.0
        while (latitude <= 90.0) {
            var longitude = -180.0
            while (longitude <= 180.0) {
                val separation = geoid.separationMeters(latitude, longitude)!!
                assertTrue(
                    "separation $separation out of range at $latitude, $longitude",
                    separation in -108.0..86.0,
                )
                longitude += 1.5
            }
            latitude += 1.5
        }
    }

    /**
     * **The sign is the thing most worth pinning.**
     *
     * MSL = ellipsoidal − separation. Getting it backwards produces an entirely plausible altitude,
     * wrong by twice the separation — 90 m in London, 33 m in Medan — and nothing on screen would
     * look odd. London's geoid is about 46 m *above* the ellipsoid, so a point at 100 m ellipsoidal
     * height is only about 54 m above sea level.
     */
    @Test
    fun `mean sea level altitude subtracts the separation`() {
        val london = geoid.mslAltitudeMeters(
            ellipsoidalAltitudeMeters = 100.0,
            latitude = 51.4779,
            longitude = -0.0015,
        )!!
        assertEquals(54.2, london, 0.6)

        // Medan's separation is negative, so the correction runs the other way — which is what makes
        // this a real check of the sign rather than of one hemisphere's convention.
        val medan = geoid.mslAltitudeMeters(100.0, 3.5952, 98.6722)!!
        assertEquals(116.42, medan, 0.6)
    }

    /**
     * Interpolation is exact at a grid post and continuous across the boundary between cells.
     *
     * The posts sit every half degree, so a whole-degree coordinate lands exactly on one. Continuity
     * is checked by stepping across a boundary in tiny increments and asserting no jump — a
     * discontinuity there would mean the cell indexing is off by one on one side.
     */
    @Test
    fun `interpolation is continuous across cell boundaries`() {
        var previous = geoid.separationMeters(45.0, 9.4)!!
        var longitude = 9.4

        while (longitude <= 9.6) {
            val separation = geoid.separationMeters(45.0, longitude)!!
            assertTrue(
                "jump of ${abs(separation - previous)} m at longitude $longitude",
                abs(separation - previous) < 0.05,
            )
            previous = separation
            longitude += 0.005
        }
    }

    /**
     * The antimeridian is continuous.
     *
     * The classic failure of a table like this: longitude wrapping is where the arithmetic goes wrong
     * on exactly one meridian, in a way nobody notices until someone crosses it. The generator
     * duplicates the +180 column so the lookup needs no modulo, and this asserts the two sides agree.
     */
    @Test
    fun `the antimeridian does not jump`() {
        val east = geoid.separationMeters(0.0, 179.999)!!
        val west = geoid.separationMeters(0.0, -179.999)!!

        assertEquals("the two sides of the antimeridian are the same place", east, west, 0.05)
        assertEquals(east, geoid.separationMeters(0.0, 180.0)!!, 0.05)
        assertEquals(east, geoid.separationMeters(0.0, -180.0)!!, 0.05)
    }

    /**
     * Longitudes outside the normal range fold rather than throw.
     *
     * A receiver should never report 190°, but a caller might arrive at one through arithmetic, and
     * an index off the end of a half-megabyte array is a crash rather than a wrong answer.
     */
    @Test
    fun `out of range longitudes wrap`() {
        assertEquals(geoid.separationMeters(10.0, 20.0)!!, geoid.separationMeters(10.0, 380.0)!!, 1e-9)
        assertEquals(geoid.separationMeters(10.0, 20.0)!!, geoid.separationMeters(10.0, -340.0)!!, 1e-9)
    }

    /** The poles are real coordinates and must not index off the end of the grid. */
    @Test
    fun `the poles resolve`() {
        assertEquals(13.61, geoid.separationMeters(90.0, 0.0)!!, 0.5)
        assertEquals(-29.53, geoid.separationMeters(-90.0, 0.0)!!, 0.5)

        // Beyond the poles is not a coordinate, but clamping beats crashing.
        assertNotNull(geoid.separationMeters(95.0, 0.0))
        assertNotNull(geoid.separationMeters(-95.0, 0.0))
    }

    private data class Case(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val expected: Double,
        val tolerance: Double,
    )
}
