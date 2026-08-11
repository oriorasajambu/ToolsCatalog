package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.TimeWeighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

class ApplyTimeWeightingUseCaseTest {

    private val applyTimeWeighting = ApplyTimeWeightingUseCase()

    @Test
    fun `an unseeded state has no level`() {
        assertNull(TimeWeightingState().levelDbSpl)
    }

    /**
     * The average starts at the first block rather than at zero.
     *
     * Without this, opening the screen in a noisy room would show the reading climbing from silence
     * for a second before settling — which reads as the meter warming up, and is indistinguishable
     * from a genuinely rising sound.
     */
    @Test
    fun `the first block seeds the average exactly`() {
        val state = applyTimeWeighting(
            TimeWeightingState(),
            blockDbSpl = 72.0,
            timeWeighting = TimeWeighting.Fast,
            blockSeconds = 0.021,
        )

        assertEquals(72.0, state.levelDbSpl!!, 1e-9)
    }

    /**
     * A step reaches 63.2% of its way in exactly one time constant.
     *
     * `1 − 1/e` is the definition of a first-order time constant, so this is what makes "Fast is
     * 125 ms" a true statement about the code rather than a name. Measured in the **linear** domain,
     * because that is where the averaging happens.
     */
    @Test
    fun `a step reaches 63_2 percent of the way in one time constant`() {
        for (weighting in TimeWeighting.entries) {
            val fromDb = 40.0
            val toDb = 90.0
            val blockSeconds = 0.001

            var state = TimeWeightingState(10.0.pow(fromDb / 10.0))
            val blocks = (weighting.tauSeconds / blockSeconds).toInt()
            repeat(blocks) {
                state = applyTimeWeighting(state, toDb, weighting, blockSeconds)
            }

            val from = 10.0.pow(fromDb / 10.0)
            val to = 10.0.pow(toDb / 10.0)
            val progress = (state.meanSquare!! - from) / (to - from)

            assertEquals("$weighting", 0.632, progress, 0.002)
        }
    }

    /**
     * Slow lags Fast, which is the entire reason both exist.
     *
     * A clap into a quiet room: after 125 ms Fast has covered most of the step and Slow has barely
     * started. If these two ever produced the same number, the mode switch would be decoration.
     */
    @Test
    fun `Slow lags Fast on a transient`() {
        val blockSeconds = 0.005
        val blocks = (0.125 / blockSeconds).toInt()

        var fast = TimeWeightingState(10.0.pow(3.0))
        var slow = TimeWeightingState(10.0.pow(3.0))
        repeat(blocks) {
            fast = applyTimeWeighting(fast, 95.0, TimeWeighting.Fast, blockSeconds)
            slow = applyTimeWeighting(slow, 95.0, TimeWeighting.Slow, blockSeconds)
        }

        assertTrue(
            "Fast ${fast.levelDbSpl} should lead Slow ${slow.levelDbSpl} by >5dB",
            fast.levelDbSpl!! - slow.levelDbSpl!! > 5.0,
        )
    }

    /**
     * The same signal over the same wall time converges alike at any block size.
     *
     * `SENSOR_DELAY` has an audio analogue: the block cadence follows the sample rate and buffer the
     * device granted, and neither is promised. A fixed α would make Fast mean 125 ms on one phone
     * and something else on another — the bug this test exists to catch, ported from
     * `SmoothGravityUseCaseTest`.
     */
    @Test
    fun `convergence is independent of block size`() {
        val totalSeconds = 0.5
        val results = listOf(0.002, 0.010, 0.021).map { blockSeconds ->
            var state = TimeWeightingState(10.0.pow(4.0))
            repeat((totalSeconds / blockSeconds).toInt()) {
                state = applyTimeWeighting(state, 85.0, TimeWeighting.Fast, blockSeconds)
            }
            state.levelDbSpl!!
        }

        val spread = results.max() - results.min()
        assertTrue("levels $results should agree within 0.1dB, spread was $spread", spread < 0.1)
    }

    /**
     * Averaging happens in energy, not in decibels.
     *
     * A signal alternating between 60 and 90 dB settles at 87, the level actually carrying that
     * power — not at the arithmetic 75 that smoothing the decibels would give. Both numbers are
     * stable and plausible, and only one is the level, so this asserts the *difference* rather than
     * merely that the filter converges.
     */
    @Test
    fun `alternating levels settle at the energy mean, not the arithmetic one`() {
        var state = TimeWeightingState()
        val blockSeconds = 0.002

        repeat(2000) { index ->
            state = applyTimeWeighting(
                state,
                if (index % 2 == 0) 60.0 else 90.0,
                TimeWeighting.Slow,
                blockSeconds,
            )
        }

        val energyMean = 10.0 * log10((10.0.pow(6.0) + 10.0.pow(9.0)) / 2.0)
        assertEquals(energyMean, state.levelDbSpl!!, 0.2)
        assertTrue(
            "should not have landed on the arithmetic mean of 75dB",
            abs(state.levelDbSpl!! - 75.0) > 10.0,
        )
    }

    /** A zero-length block cannot advance anything, and must not divide by it either. */
    @Test
    fun `a zero-length block leaves the state alone`() {
        val seeded = TimeWeightingState(10.0.pow(7.0))

        val state = applyTimeWeighting(seeded, 90.0, TimeWeighting.Fast, blockSeconds = 0.0)

        assertEquals(seeded, state)
    }
}
