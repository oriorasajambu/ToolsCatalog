package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.Synthetic
import com.minion.scaffold.core.level.model.UpVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DetectStabilityUseCaseTest {

    private val detect = DetectStabilityUseCase()

    @Test
    fun `a still device becomes steady`() {
        val state = feed(List(100) { UpVector(0.0, 0.0, 1.0) })

        assertEquals(Steadiness.Steady, state.steadiness)
    }

    @Test
    fun `sensor noise alone does not prevent steadiness`() {
        // Fed through the smoother first, because that is how the pipeline is wired — and the
        // coupling is deliberate. The thresholds here are chosen for a filtered vector; raw white
        // noise at this amplitude produces a large sample-to-sample delta and would read as
        // movement, which is exactly what the filter exists to remove.
        val random = Random(5)
        val smooth = SmoothGravityUseCase()

        var smoothing = SmoothingState()
        var state = StabilityState()

        repeat(300) { index ->
            val timestamp = index * SAMPLE_NANOS
            val noisy = com.minion.scaffold.core.level.model.GravitySample(
                x = random.nextDouble(-0.02, 0.02),
                y = random.nextDouble(-0.02, 0.02),
                z = Synthetic.G,
                timestampNanos = timestamp,
            )
            smoothing = smooth(smoothing, noisy)
            state = detect(state, smoothing.value!!.normalizedOrNull()!!, timestamp)
        }

        assertEquals(Steadiness.Steady, state.steadiness)
    }

    @Test
    fun `movement is reported immediately`() {
        var state = feed(List(100) { UpVector(0.0, 0.0, 1.0) })
        assertEquals(Steadiness.Steady, state.steadiness)

        // One decisive lurch. The warning must not wait — a late warning is useless, because the
        // user has already read the wrong number by then.
        var timestamp = 100L * SAMPLE_NANOS
        repeat(6) { index ->
            timestamp += SAMPLE_NANOS
            state = detect(state, Synthetic.up(pitchDegrees = index * 3.0), timestamp)
        }

        assertEquals(Steadiness.Moving, state.steadiness)
    }

    @Test
    fun `settling is not immediately trustworthy`() {
        // Asymmetry: instant to warn, deliberate to clear. A steadiness that flickers on for one
        // frame invites capturing a calibration point at exactly the wrong moment.
        var state = feed(List(50) { UpVector(0.0, 0.0, 1.0) })
        var timestamp = 50L * SAMPLE_NANOS

        repeat(10) { index ->
            timestamp += SAMPLE_NANOS
            state = detect(state, Synthetic.up(pitchDegrees = index * 4.0), timestamp)
        }
        assertEquals(Steadiness.Moving, state.steadiness)

        // Stop dead. The very next sample must not read as trustworthy.
        timestamp += SAMPLE_NANOS
        state = detect(state, Synthetic.up(pitchDegrees = 36.0), timestamp)
        assertTrue(state.steadiness != Steadiness.Steady)
    }

    private fun feed(samples: List<UpVector>) =
        samples.foldIndexed(StabilityState()) { index, state, up ->
            detect(state, up, index * SAMPLE_NANOS)
        }

    private companion object {
        const val SAMPLE_NANOS = 20_000_000L
    }
}

class PlanBeepUseCaseTest {

    private val planBeep = PlanBeepUseCase()

    @Test
    fun `silent when disabled`() {
        assertEquals(BeepPlan.Silent, planBeep(0.0, enabled = false, wasSteady = false))
    }

    @Test
    fun `steady inside tolerance`() {
        assertEquals(BeepPlan.Steady, planBeep(0.1, enabled = true, wasSteady = false))
    }

    @Test
    fun `hysteresis keeps the tone steady just outside the entry threshold`() {
        // Hovering at the boundary is exactly where a user spends their time, so the tone must not
        // chatter between steady and pulsing there.
        val entering = planBeep(0.3, enabled = true, wasSteady = false)
        val holding = planBeep(0.3, enabled = true, wasSteady = true)

        assertTrue(entering is BeepPlan.Pulse)
        assertEquals(BeepPlan.Steady, holding)
    }

    @Test
    fun `the interval shortens as it approaches level`() {
        val intervals = listOf(20.0, 10.0, 5.0, 2.0, 1.0, 0.5).map {
            (planBeep(it, enabled = true, wasSteady = false) as BeepPlan.Pulse).intervalMillis
        }

        assertEquals(intervals.sortedDescending(), intervals)
    }

    @Test
    fun `never falls silent however far off it is`() {
        val plan = planBeep(179.0, enabled = true, wasSteady = false)

        // Silence would read as a broken app rather than as "very far off".
        assertTrue(plan is BeepPlan.Pulse)
        assertEquals(
            PlanBeepUseCase.MAX_INTERVAL_MILLIS.toLong(),
            (plan as BeepPlan.Pulse).intervalMillis,
        )
    }

    @Test
    fun `pulses never overlap the tone they play`() {
        // An interval shorter than the tone stacks beeps into a buzz instead of quickening.
        val fastest = (planBeep(0.21, enabled = true, wasSteady = false) as BeepPlan.Pulse)

        assertTrue(fastest.intervalMillis > PlanBeepUseCase.TONE_DURATION_MILLIS * 2)
    }

    @Test
    fun `sign does not matter`() {
        val above = planBeep(3.0, enabled = true, wasSteady = false)
        val below = planBeep(-3.0, enabled = true, wasSteady = false)

        assertEquals(above, below)
    }
}
