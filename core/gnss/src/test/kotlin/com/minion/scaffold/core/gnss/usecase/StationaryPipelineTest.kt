package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.geoid.GeoidModel
import com.minion.scaffold.core.gnss.model.GnssFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The whole pipeline, end to end, on synthetic fixes.
 *
 * The individual use-case tests each hand the next stage a value chosen by hand, which is right for
 * pinning one behaviour but means the *seams* go untested — and the seams are where a speed gate that
 * works and an accumulator that works can still combine into a tool that reports a kilometre of
 * travel from a desk.
 *
 * These two tests are the ones the on-device checks mirror directly: a phone left on a table for five
 * minutes must accumulate nothing at all, and a real journey must be counted.
 */
class StationaryPipelineTest {

    private val resolveSpeed = ResolveSpeedUseCase()
    private val accumulateTrip = AccumulateTripUseCase()
    private val geoid = GeoidModel()

    private val metersPerDegree = 111_320.0

    /**
     * **A phone on a table records nothing.**
     *
     * Position wanders within its stated 8 m accuracy, the Doppler speed reports a few centimetres a
     * second of noise against a stated 0.4 m/s uncertainty, and the altitude drifts by tens of metres
     * — all of which is what a real stationary consumer receiver does. After five minutes: no
     * distance, no climb, no maximum.
     *
     * This is the single most valuable test in the module. Every gate has to hold simultaneously, and
     * a failure anywhere shows up here.
     */
    @Test
    fun `a stationary receiver accumulates nothing over five minutes`() {
        val random = Random(2024)
        var speedState = SpeedState()
        var trip = TripState()

        repeat(300) { index ->
            val fix = GnssFix(
                latitude = 3.5952 + (random.nextDouble() - 0.5) * 16.0 / metersPerDegree,
                longitude = 98.6722 + (random.nextDouble() - 0.5) * 16.0 / metersPerDegree,
                ellipsoidalAltitudeMeters = 25.0 + (random.nextDouble() - 0.5) * 30.0,
                // A real receiver at rest reports small non-zero speeds, not zero.
                speedMetersPerSecond = random.nextDouble() * 0.35,
                speedAccuracyMetersPerSecond = 0.4,
                horizontalAccuracyMeters = 8.0,
                verticalAccuracyMeters = 15.0,
                elapsedRealtimeNanos = index * 1_000_000_000L,
                fromMockProvider = false,
            )

            val (nextSpeedState, speed) = resolveSpeed(speedState, fix)
            speedState = nextSpeedState

            trip = accumulateTrip(
                trip,
                fix,
                speed.metersPerSecond,
                geoid.mslAltitudeMeters(fix.ellipsoidalAltitudeMeters!!, fix.latitude, fix.longitude),
            )
        }

        assertEquals("distance", 0.0, trip.distanceMeters, 0.0)
        assertEquals("elevation gain", 0.0, trip.elevationGainMeters, 0.0)
        assertNull("maximum speed", trip.maxSpeedMetersPerSecond)
        assertTrue("time should still pass", trip.durationSeconds > 290.0)
    }

    /**
     * A real drive is counted, and its altitude is corrected to sea level.
     *
     * Ten minutes at 25 m/s should come to about 15 km. The altitude check is the geoid arriving at
     * the far end of the pipeline: Medan's separation is about −16.4 m, so an ellipsoidal 25 m is
     * roughly 41 m above sea level — and getting the sign backwards would give 9 m instead, which
     * looks just as plausible.
     */
    @Test
    fun `a real drive accumulates distance and a sea-level altitude`() {
        var speedState = SpeedState()
        var trip = TripState()
        var altitude = 0.0

        repeat(600) { index ->
            val fix = GnssFix(
                latitude = 3.5952 + index * 25.0 / metersPerDegree,
                longitude = 98.6722,
                ellipsoidalAltitudeMeters = 25.0,
                speedMetersPerSecond = 25.0,
                speedAccuracyMetersPerSecond = 0.3,
                horizontalAccuracyMeters = 6.0,
                verticalAccuracyMeters = 12.0,
                elapsedRealtimeNanos = index * 1_000_000_000L,
                fromMockProvider = false,
            )

            val (nextSpeedState, speed) = resolveSpeed(speedState, fix)
            speedState = nextSpeedState

            altitude = geoid.mslAltitudeMeters(
                fix.ellipsoidalAltitudeMeters!!, fix.latitude, fix.longitude,
            )!!
            trip = accumulateTrip(trip, fix, speed.metersPerSecond, altitude)
        }

        assertEquals("about 15 km", 15_000.0, trip.distanceMeters, 100.0)
        assertEquals("average", 25.0, trip.averageSpeedMetersPerSecond!!, 0.2)
        assertEquals("maximum", 25.0, trip.maxSpeedMetersPerSecond!!, 0.01)
        assertEquals("sea level altitude", 41.4, altitude, 1.0)
        assertEquals("flat road climbs nothing", 0.0, trip.elevationGainMeters, 0.0)
    }
}
