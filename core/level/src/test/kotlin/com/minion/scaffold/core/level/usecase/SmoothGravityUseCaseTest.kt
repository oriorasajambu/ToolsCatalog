package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.Synthetic
import com.minion.scaffold.core.level.model.GravitySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SmoothGravityUseCaseTest {

    private val smooth = SmoothGravityUseCase()

    @Test
    fun `seeds from the first sample rather than from zero`() {
        // Starting at zero would show a visible crawl up from "level" every time the screen opens.
        val sample = Synthetic.sample(pitchDegrees = 30.0)
        val state = smooth(SmoothingState(), sample)

        assertEquals(sample, state.value)
    }

    @Test
    fun `converges on a constant input`() {
        val target = Synthetic.sample(pitchDegrees = 10.0)
        var state = smooth(SmoothingState(), Synthetic.sample(pitchDegrees = 0.0))

        repeat(200) { index ->
            state = smooth(state, target.copy(timestampNanos = (index + 1) * SAMPLE_NANOS))
        }

        assertEquals(target.y, state.value!!.y, 1e-6)
    }

    @Test
    fun `suppresses noise well inside the tolerance budget`() {
        val random = Random(11)
        val truth = Synthetic.sample(pitchDegrees = 0.0)
        var state = smooth(SmoothingState(), truth)

        var worstDegrees = 0.0
        repeat(500) { index ->
            val noisy = GravitySample(
                x = truth.x + random.nextDouble(-0.02, 0.02),
                y = truth.y + random.nextDouble(-0.02, 0.02),
                z = truth.z + random.nextDouble(-0.02, 0.02),
                timestampNanos = (index + 1) * SAMPLE_NANOS,
            )
            state = smooth(state, noisy)

            if (index > 100) {
                val value = state.value!!
                val degrees = Math.toDegrees(
                    kotlin.math.atan2(value.y, kotlin.math.hypot(value.x, value.z)),
                )
                worstDegrees = maxOf(worstDegrees, abs(degrees))
            }
        }

        // Asserted in degrees rather than m/s^2, because degrees is what the budget is written in:
        // the level goes green inside 0.2, so residual jitter has to be a small fraction of that.
        // The raw input here is +/-0.02 m/s^2, roughly 0.12 degrees peak.
        assertTrue("worst=$worstDegrees deg", worstDegrees < 0.05)
    }

    @Test
    fun `behaves the same at 30Hz and at 200Hz`() {
        // The whole point of a dt-based alpha. SENSOR_DELAY_GAME is only a hint and devices deliver
        // anywhere in this range, so a fixed alpha would make the feel device-dependent.
        val slow = converge(intervalNanos = 33_333_333L, durationSeconds = 3.0)
        val fast = converge(intervalNanos = 5_000_000L, durationSeconds = 3.0)

        assertEquals(slow, fast, 0.02)
    }

    @Test
    fun `resets after a long gap instead of crawling from a stale value`() {
        var state = smooth(SmoothingState(), Synthetic.sample(pitchDegrees = 0.0))
        repeat(50) { index ->
            state = smooth(state, Synthetic.sample(0.0, timestampNanos = (index + 1) * SAMPLE_NANOS))
        }

        // A second later — the screen was backgrounded and has come back at a new angle.
        val resumed = Synthetic.sample(pitchDegrees = 40.0, timestampNanos = 2_000_000_000L)
        state = smooth(state, resumed)

        assertEquals(resumed, state.value)
    }

    @Test
    fun `survives a duplicate timestamp`() {
        // Real devices emit these. Without the guard the dt is zero and the derivative divides by
        // it, poisoning the filter with an infinity.
        var state = smooth(SmoothingState(), Synthetic.sample(0.0, timestampNanos = 1_000L))
        state = smooth(state, Synthetic.sample(5.0, timestampNanos = 1_000L))

        assertNotNull(state.value)
        assertTrue(state.value!!.y.isFinite())
    }

    @Test
    fun `tracks a deliberate tilt faster than it tracks noise`() {
        // The 1 euro filter's whole reason for existing: a fixed cutoff forces a choice between a
        // jittery rest and a sluggish move. Moving fast must open the cutoff up.
        val moving = converge(intervalNanos = SAMPLE_NANOS, durationSeconds = 0.4)

        assertTrue("reached $moving of 40 degrees in 0.4s", moving > 20.0)
    }

    /** Degrees of pitch reached after [durationSeconds] of a step input at the given rate. */
    private fun converge(intervalNanos: Long, durationSeconds: Double): Double {
        val steps = (durationSeconds * 1_000_000_000L / intervalNanos).toInt()
        val target = Synthetic.sample(pitchDegrees = 40.0)

        var state = smooth(SmoothingState(), Synthetic.sample(pitchDegrees = 0.0))
        repeat(steps) { index ->
            state = smooth(state, target.copy(timestampNanos = (index + 1) * intervalNanos))
        }

        val value = state.value!!
        return Math.toDegrees(kotlin.math.atan2(value.y, kotlin.math.hypot(value.x, value.z)))
    }

    private companion object {
        const val SAMPLE_NANOS = 20_000_000L
    }
}
