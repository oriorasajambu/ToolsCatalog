package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.model.GnssFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The three accumulators that go wrong slowly and invisibly.
 *
 * Each has a paired test: one proving it ignores noise, one proving it still counts the real thing. A
 * gate that only passed the first half would be a tool that always reads zero, which is easy to write
 * and useless.
 */
class AccumulateTripUseCaseTest {

    private val accumulate = AccumulateTripUseCase()

    /** Roughly metres per degree of latitude, near the equator. */
    private val metersPerDegree = 111_320.0

    @Test
    fun `an untouched trip has no statistics`() {
        val stats = TripState().toStats()

        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertNull(stats.maxSpeedMetersPerSecond)
        assertNull(stats.averageSpeedMetersPerSecond)
    }

    /**
     * **A stationary phone travels nowhere.**
     *
     * The defining test of the distance accumulator. A receiver at rest wanders by metres every
     * second; summing those steps naively walks a parked car about a kilometre an hour. Here the
     * position jitters randomly within its stated 8 m accuracy for five minutes.
     */
    @Test
    fun `a stationary jittering fix accumulates no distance`() {
        val random = Random(42)
        var state = TripState()

        repeat(300) { index ->
            val jitter = { (random.nextDouble() - 0.5) * 16.0 / metersPerDegree }
            state = accumulate(
                state,
                fix(
                    latitude = jitter(),
                    longitude = jitter(),
                    horizontalAccuracy = 8.0,
                    elapsedNanos = index * 1_000_000_000L,
                ),
                resolvedSpeedMetersPerSecond = 0.0,
                mslAltitudeMeters = 100.0,
            )
        }

        assertEquals("a parked phone must not travel", 0.0, state.distanceMeters, 0.0)
        assertTrue("but time still passes", state.durationSeconds > 290.0)
    }

    /**
     * A real walk is counted.
     *
     * The other half of the pair: 300 fixes at 1.4 m/s should come to roughly 420 m. Under-reporting
     * slightly is expected — the gate discards any step a fix's own error could have produced — but it
     * must not discard the journey.
     */
    @Test
    fun `a real walk accumulates distance`() {
        var state = TripState()

        repeat(300) { index ->
            state = accumulate(
                state,
                fix(
                    latitude = index * 1.4 / metersPerDegree,
                    horizontalAccuracy = 5.0,
                    elapsedNanos = index * 1_000_000_000L,
                ),
                resolvedSpeedMetersPerSecond = 1.4,
                mslAltitudeMeters = 100.0,
            )
        }

        assertEquals(420.0, state.distanceMeters, 25.0)
        assertEquals(1.4, state.averageSpeedMetersPerSecond!!, 0.1)
    }

    /**
     * **One glitch does not set a record.**
     *
     * The number people screenshot, and the one a single satellite reacquisition corrupts
     * permanently. A cyclist at 8 m/s with one fix reporting 70 m/s must finish with a maximum of 8.
     */
    @Test
    fun `a single fast glitch does not become the maximum`() {
        var state = TripState()

        repeat(20) { index ->
            val speed = if (index == 10) 70.0 else 8.0
            state = accumulate(
                state,
                fix(
                    latitude = index * 8.0 / metersPerDegree,
                    speedAccuracy = 0.3,
                    elapsedNanos = index * 1_000_000_000L,
                ),
                resolvedSpeedMetersPerSecond = speed,
                mslAltitudeMeters = 100.0,
            )
        }

        assertEquals(8.0, state.maxSpeedMetersPerSecond!!, 0.01)
    }

    /** A sustained sprint does set one. */
    @Test
    fun `a sustained peak becomes the maximum`() {
        var state = TripState()

        repeat(20) { index ->
            val speed = if (index in 10..13) 15.0 else 8.0
            state = accumulate(
                state,
                fix(
                    latitude = index * 10.0 / metersPerDegree,
                    speedAccuracy = 0.3,
                    elapsedNanos = index * 1_000_000_000L,
                ),
                resolvedSpeedMetersPerSecond = speed,
                mslAltitudeMeters = 100.0,
            )
        }

        assertEquals(15.0, state.maxSpeedMetersPerSecond!!, 0.01)
    }

    /** A speed the receiver is unsure about is not evidence of a record, however fast. */
    @Test
    fun `a fast speed with a poor accuracy is not a maximum`() {
        var state = TripState()

        repeat(10) { index ->
            state = accumulate(
                state,
                fix(
                    latitude = index * 40.0 / metersPerDegree,
                    speedAccuracy = 9.0,
                    elapsedNanos = index * 1_000_000_000L,
                ),
                resolvedSpeedMetersPerSecond = 40.0,
                mslAltitudeMeters = 100.0,
            )
        }

        assertNull(state.maxSpeedMetersPerSecond)
    }

    /**
     * **A phone on a table climbs nothing.**
     *
     * The worst of the three accumulators. GNSS vertical error is ±10–30 m and wanders continuously,
     * so summing every rise records hundreds of metres of climb from a device that never moved. Here
     * the altitude wanders within its stated 15 m accuracy for ten minutes.
     */
    @Test
    fun `altitude noise accumulates no elevation gain`() {
        val random = Random(7)
        var state = TripState()

        repeat(600) { index ->
            state = accumulate(
                state,
                fix(verticalAccuracy = 15.0, elapsedNanos = index * 1_000_000_000L),
                resolvedSpeedMetersPerSecond = 0.0,
                mslAltitudeMeters = 100.0 + (random.nextDouble() - 0.5) * 28.0,
            )
        }

        assertEquals("a phone on a table must not climb", 0.0, state.elevationGainMeters, 0.0)
    }

    /** A real climb is counted. */
    @Test
    fun `a sustained climb accumulates elevation gain`() {
        var state = TripState()

        repeat(200) { index ->
            state = accumulate(
                state,
                fix(verticalAccuracy = 8.0, elapsedNanos = index * 1_000_000_000L),
                resolvedSpeedMetersPerSecond = 1.4,
                mslAltitudeMeters = 100.0 + index * 1.0,
            )
        }

        // The gate discards changes below the vertical accuracy, so the total lands a little short of
        // the 199 m actually climbed. Under-reporting is the intended direction.
        assertTrue(
            "expected roughly 200 m, was ${state.elevationGainMeters}",
            state.elevationGainMeters in 175.0..200.0,
        )
    }

    /**
     * Descent is not gain.
     *
     * Obvious and worth pinning: an accumulator summing the absolute change would double the total on
     * any out-and-back walk.
     */
    @Test
    fun `descending does not add to elevation gain`() {
        var state = TripState()

        repeat(100) { index ->
            state = accumulate(
                state,
                fix(verticalAccuracy = 5.0, elapsedNanos = index * 1_000_000_000L),
                resolvedSpeedMetersPerSecond = 1.4,
                mslAltitudeMeters = 500.0 - index * 2.0,
            )
        }

        assertEquals(0.0, state.elevationGainMeters, 0.0)
        assertEquals(500.0, state.maxAltitudeMeters!!, 0.01)
        assertTrue(state.minAltitudeMeters!! < 320.0)
    }

    /**
     * A gap in the fixes is not a journey across it.
     *
     * The screen goes away, the signal drops, and the next fix arrives a minute later a kilometre down
     * the road. Counting the straight line would add distance nobody travelled at a speed nobody
     * reached, and counting the time would ruin the average.
     */
    @Test
    fun `a long gap adds neither distance nor duration`() {
        var state = TripState()

        state = accumulate(
            state,
            fix(latitude = 0.0, elapsedNanos = 0L),
            resolvedSpeedMetersPerSecond = 0.0,
            mslAltitudeMeters = 100.0,
        )
        state = accumulate(
            state,
            fix(latitude = 0.01, elapsedNanos = 120_000_000_000L),
            resolvedSpeedMetersPerSecond = 0.0,
            mslAltitudeMeters = 100.0,
        )

        assertEquals(0.0, state.distanceMeters, 0.0)
        assertEquals(0.0, state.durationSeconds, 0.0)
    }

    /**
     * The average is distance over duration, not a mean of speeds.
     *
     * A minute at 20 m/s then a minute stopped averages 10 m/s over the trip. A running mean of the
     * instantaneous readings answers a different question, and "average speed" has one accepted
     * meaning.
     */
    @Test
    fun `average speed is distance over duration`() {
        var state = TripState()
        var latitude = 0.0

        repeat(60) { index ->
            latitude += 20.0 / metersPerDegree
            state = accumulate(
                state,
                fix(latitude = latitude, elapsedNanos = index * 1_000_000_000L),
                resolvedSpeedMetersPerSecond = 20.0,
                mslAltitudeMeters = 100.0,
            )
        }
        repeat(60) { index ->
            state = accumulate(
                state,
                fix(latitude = latitude, elapsedNanos = (60 + index) * 1_000_000_000L),
                resolvedSpeedMetersPerSecond = 0.0,
                mslAltitudeMeters = 100.0,
            )
        }

        assertEquals(10.0, state.averageSpeedMetersPerSecond!!, 0.5)
    }

    private fun fix(
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        horizontalAccuracy: Double? = 5.0,
        verticalAccuracy: Double? = 10.0,
        speedAccuracy: Double? = 0.3,
        elapsedNanos: Long = 0L,
    ) = GnssFix(
        latitude = latitude,
        longitude = longitude,
        ellipsoidalAltitudeMeters = 100.0,
        speedMetersPerSecond = null,
        speedAccuracyMetersPerSecond = speedAccuracy,
        horizontalAccuracyMeters = horizontalAccuracy,
        verticalAccuracyMeters = verticalAccuracy,
        elapsedRealtimeNanos = elapsedNanos,
        fromMockProvider = false,
    )
}
