package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.model.GnssFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveSpeedUseCaseTest {

    private val resolveSpeed = ResolveSpeedUseCase()

    /**
     * **The load-bearing assertion of the whole feature.**
     *
     * A reported speed is passed through untouched. It is a Doppler measurement — the frequency shift
     * of the satellite carrier — and recomputing it from positions is the entire mistake this design
     * exists to avoid. If this ever fails, the speedometer has quietly become a position
     * differentiator and every reading has metres of position noise multiplied into it.
     */
    @Test
    fun `a reported speed is used verbatim and never recomputed`() {
        val (_, speed) = resolve(List(3) { fix(speed = 27.5, speedAccuracy = 0.3) })

        assertEquals(27.5, speed.metersPerSecond, 0.0)
        assertFalse("a measured speed must not be marked derived", speed.derived)
    }

    /**
     * A speed below its own uncertainty is not a measurement of motion.
     *
     * The gate is the receiver's own error bar rather than an invented constant, so it tightens with a
     * good fix and widens with a poor one. Here 0.2 m/s against a stated accuracy of 0.5 is noise.
     */
    @Test
    fun `a speed under its own accuracy reads zero`() {
        val (_, speed) = resolve(List(5) { fix(speed = 0.2, speedAccuracy = 0.5) })

        assertEquals(0.0, speed.metersPerSecond, 0.0)
    }

    /**
     * The same speed with a *better* receiver counts as movement.
     *
     * The pair of these is the point: 0.2 m/s is meaningless when the receiver admits to ±0.5 and is
     * a real slow walk when it claims ±0.05. A fixed threshold cannot tell those apart.
     */
    @Test
    fun `the same speed with a tighter accuracy is movement`() {
        val (_, speed) = resolve(List(5) { fix(speed = 0.2, speedAccuracy = 0.05) })

        assertEquals(0.2, speed.metersPerSecond, 1e-9)
    }

    /**
     * A single fast fix does not start the readout, and a single slow one does not stop it.
     *
     * Blocks the flicker a phone at the kerb would otherwise show once a second. Asymmetric: two
     * fixes to start, three to stop, so a brief signal dip at speed does not blink to zero.
     */
    @Test
    fun `one stray fix does not move the readout in either direction`() {
        var state = SpeedState()
        var speed = ResolvedSpeed(0.0, null, false)

        // At rest, then a single glitch.
        repeat(4) {
            val result = resolveSpeed(state, fix(speed = 0.1, speedAccuracy = 0.5))
            state = result.first; speed = result.second
        }
        val glitch = resolveSpeed(state, fix(speed = 30.0, speedAccuracy = 0.3))
        assertEquals("a single fast fix must not start the readout", 0.0, glitch.second.metersPerSecond, 0.0)

        // Now genuinely moving, then a single dropout.
        state = glitch.first
        repeat(4) {
            val result = resolveSpeed(state, fix(speed = 30.0, speedAccuracy = 0.3))
            state = result.first; speed = result.second
        }
        assertEquals(30.0, speed.metersPerSecond, 0.0)

        val dropout = resolveSpeed(state, fix(speed = 0.05, speedAccuracy = 0.5))
        assertTrue("a single slow fix must not stop the readout", dropout.second.metersPerSecond > 0.0)
    }

    @Test
    fun `sustained movement starts and sustained stillness stops`() {
        var state = SpeedState()

        repeat(3) { state = resolveSpeed(state, fix(speed = 12.0, speedAccuracy = 0.2)).first }
        assertTrue(resolveSpeed(state, fix(speed = 12.0, speedAccuracy = 0.2)).second.metersPerSecond > 0)

        repeat(4) { state = resolveSpeed(state, fix(speed = 0.05, speedAccuracy = 0.5)).first }
        assertEquals(0.0, resolveSpeed(state, fix(speed = 0.05, speedAccuracy = 0.5)).second.metersPerSecond, 0.0)
    }

    /**
     * With no reported speed, the fallback derives one and says so.
     *
     * Driving speed rather than walking, deliberately: the derived path carries the two position
     * errors as its uncertainty, so it only clears its own gate well above the noise. That is the
     * intended behaviour — see the conservative-bound note in the use case — and this test pins the
     * working half of it while the next pins the quiet half.
     *
     * The flag matters as much as the number: the screen presents a derived speed differently,
     * because it carries position noise the Doppler path does not.
     */
    @Test
    fun `a missing speed is derived from positions and flagged`() {
        // About 25 m of latitude per second, which is roughly 90 km/h.
        var state = SpeedState()
        var speed = ResolvedSpeed(0.0, null, false)

        repeat(5) { index ->
            val result = resolveSpeed(
                state,
                fix(
                    speed = null,
                    latitude = index * 0.000224,
                    longitude = 0.0,
                    elapsedNanos = index * 1_000_000_000L,
                ),
            )
            state = result.first; speed = result.second
        }

        assertTrue("expected a derived speed", speed.derived)
        assertEquals(24.9, speed.metersPerSecond, 0.5)
    }

    /**
     * A derived speed from a jittering stationary receiver still reads zero.
     *
     * This is the case that makes the fallback survivable. Two fixes ±5 m apart imply 10 m/s of
     * motion, but the derived accuracy is the position errors over the interval — also about 10 m/s —
     * so the gate correctly reads it as noise. Without carrying that uncertainty through, the weak
     * path would report 36 km/h for a phone on a table.
     */
    @Test
    fun `a derived speed from stationary jitter reads zero`() {
        var state = SpeedState()
        var speed = ResolvedSpeed(0.0, null, true)

        // Alternate either side of a point, 5 m each way, with 5 m stated accuracy.
        repeat(6) { index ->
            val offset = if (index % 2 == 0) 0.0000449 else -0.0000449
            val result = resolveSpeed(
                state,
                fix(
                    speed = null,
                    latitude = offset,
                    longitude = 0.0,
                    horizontalAccuracy = 5.0,
                    elapsedNanos = index * 1_000_000_000L,
                ),
            )
            state = result.first; speed = result.second
        }

        assertEquals(0.0, speed.metersPerSecond, 0.0)
    }

    /** A stale previous fix is not something to differentiate against. */
    @Test
    fun `a long gap does not produce a derived speed`() {
        val first = fix(speed = null, latitude = 0.0, elapsedNanos = 0L)
        val second = fix(speed = null, latitude = 1.0, elapsedNanos = 60_000_000_000L)

        val state = resolveSpeed(SpeedState(), first).first
        val (_, speed) = resolveSpeed(state, second)

        assertEquals(0.0, speed.metersPerSecond, 0.0)
    }

    /**
     * Where the receiver reports no accuracy at all, a conservative floor stands in.
     *
     * The only place this module uses an invented constant, and it is confined to the path where
     * there is genuinely nothing better to compare against.
     */
    @Test
    fun `with no reported accuracy a conservative floor applies`() {
        val crawling = resolve(List(5) { fix(speed = 0.3, speedAccuracy = null) })
        assertEquals(0.0, crawling.second.metersPerSecond, 0.0)

        val walking = resolve(List(5) { fix(speed = 1.4, speedAccuracy = null) })
        assertEquals(1.4, walking.second.metersPerSecond, 1e-9)
    }

    // region Helpers

    private fun resolve(fixes: List<GnssFix>): Pair<SpeedState, ResolvedSpeed> {
        var state = SpeedState()
        var speed = ResolvedSpeed(0.0, null, false)
        for (fix in fixes) {
            val result = resolveSpeed(state, fix)
            state = result.first
            speed = result.second
        }
        return state to speed
    }

    private var clock = 0L

    private fun fix(
        speed: Double?,
        speedAccuracy: Double? = 0.3,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        horizontalAccuracy: Double? = 5.0,
        elapsedNanos: Long? = null,
    ): GnssFix {
        clock += 1_000_000_000L
        return GnssFix(
            latitude = latitude,
            longitude = longitude,
            ellipsoidalAltitudeMeters = 100.0,
            speedMetersPerSecond = speed,
            speedAccuracyMetersPerSecond = speedAccuracy,
            horizontalAccuracyMeters = horizontalAccuracy,
            verticalAccuracyMeters = 10.0,
            elapsedRealtimeNanos = elapsedNanos ?: clock,
            fromMockProvider = false,
        )
    }

    // endregion
}
