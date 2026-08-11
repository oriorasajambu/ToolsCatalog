package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.BlockLevel
import com.minion.scaffold.core.sound.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccumulateSessionUseCaseTest {

    private val accumulate = AccumulateSessionUseCase()

    @Test
    fun `an untouched session has no statistics`() {
        val stats = SessionState().toStats()

        assertNull(stats.leqDbSpl)
        assertNull(stats.minDbSpl)
        assertNull(stats.maxDbSpl)
        assertEquals(0.0, stats.durationSeconds, 0.0)
    }

    @Test
    fun `a constant level gives that level as Leq`() {
        val state = feed(List(100) { 65.0 })

        assertEquals(65.0, state.leqDbSpl!!, 1e-9)
    }

    /**
     * **The test that defines what Leq is.**
     *
     * Half a session at 80 dB and half at 90 comes to 87.4, not 85. The louder half carries ten
     * times the energy, so it dominates — which is the whole reason Leq is the figure that appears
     * in noise regulations rather than a plain mean.
     *
     * If someone later "simplifies" the accumulator into an arithmetic average of decibels, every
     * other test in this file still passes and this one fails. That asymmetry is the point: the
     * wrong implementation produces a stable, plausible number, and 85 looks at least as reasonable
     * as 87.4 to anyone not checking.
     */
    @Test
    fun `Leq is an energy average, not an arithmetic one`() {
        val state = feed(List(50) { 80.0 } + List(50) { 90.0 })

        assertEquals(87.4, state.leqDbSpl!!, 0.05)
    }

    /** Order cannot matter to an energy sum, and a regression that broke this would be subtle. */
    @Test
    fun `Leq does not depend on the order blocks arrive in`() {
        val quietFirst = feed(List(50) { 80.0 } + List(50) { 90.0 })
        val loudFirst = feed(List(50) { 90.0 } + List(50) { 80.0 })

        assertEquals(quietFirst.leqDbSpl!!, loudFirst.leqDbSpl!!, 1e-9)
    }

    @Test
    fun `min and max track the displayed level`() {
        val state = feed(listOf(60.0, 82.0, 55.0, 71.0))

        assertEquals(55.0, state.minDbSpl!!, 1e-9)
        assertEquals(82.0, state.maxDbSpl!!, 1e-9)
    }

    /**
     * A clipped block moves nothing but the unmeasurable clock.
     *
     * The most important assertion about clipping in the module. A clipped block still *has* a
     * computed level — it is simply wrong, and wrong low — so an accumulator that took it at face
     * value would set the session maximum from the one moment the meter was least able to measure.
     * The loudest thing that happened would be recorded as something ordinary.
     */
    @Test
    fun `a clipped block sets no minimum, maximum or Leq`() {
        var state = feed(listOf(70.0))
        state = accumulate(state, BlockLevel.Clipped, displayedDbSpl = 98.0, blockSeconds = BLOCK)

        assertEquals(70.0, state.maxDbSpl!!, 1e-9)
        assertEquals(70.0, state.minDbSpl!!, 1e-9)
        assertEquals(70.0, state.leqDbSpl!!, 1e-9)
        assertEquals(BLOCK, state.unmeasurableSeconds, 1e-9)
    }

    @Test
    fun `a below-floor block sets no minimum either`() {
        var state = feed(listOf(70.0))
        state = accumulate(state, BlockLevel.BelowFloor, displayedDbSpl = 12.0, blockSeconds = BLOCK)

        assertEquals(70.0, state.minDbSpl!!, 1e-9)
        assertEquals(BLOCK, state.unmeasurableSeconds, 1e-9)
    }

    /**
     * Out-of-range time is excluded from Leq's denominator, not counted as silence.
     *
     * Ten seconds of clipping between two measured blocks must not dilute the average. If
     * `measuredSeconds` were replaced with `durationSeconds` here, a long stretch of clipping — the
     * loudest part of any session — would *lower* the reported Leq. The two constructions differ
     * only in which field is divided by, which is exactly the kind of thing that survives review.
     */
    @Test
    fun `unmeasurable time does not dilute Leq`() {
        var state = feed(listOf(85.0, 85.0))
        repeat(500) {
            state = accumulate(state, BlockLevel.Clipped, displayedDbSpl = null, blockSeconds = BLOCK)
        }

        assertEquals(85.0, state.leqDbSpl!!, 1e-9)
        assertEquals(502 * BLOCK, state.durationSeconds, 1e-6)
    }

    @Test
    fun `time above the threshold is counted from the displayed level`() {
        val state = feed(listOf(80.0, 86.0, 90.0, 84.9, 85.0))

        assertEquals(3 * BLOCK, state.secondsAboveThreshold, 1e-9)
    }

    /**
     * Before the smoothing has seeded, energy still accrues but min and max do not.
     *
     * The displayed level is null for the first block only, and dropping its energy would make Leq
     * quietly wrong by one block's worth on every session.
     */
    @Test
    fun `a block with no displayed level still contributes energy`() {
        val state = accumulate(
            SessionState(),
            BlockLevel.Measured(75.0),
            displayedDbSpl = null,
            blockSeconds = BLOCK,
        )

        assertEquals(75.0, state.leqDbSpl!!, 1e-9)
        assertNull(state.maxDbSpl)
    }

    @Test
    fun `a zero-length block changes nothing`() {
        val seeded = feed(listOf(70.0))

        val state = accumulate(seeded, BlockLevel.Measured(90.0), 90.0, blockSeconds = 0.0)

        assertEquals(seeded, state)
    }

    /** Blocks arrive at the audio cadence, not at one a second — duration has to follow them. */
    private fun feed(levels: List<Double>): SessionState =
        levels.fold(SessionState()) { state, level ->
            accumulate(state, BlockLevel.Measured(level), displayedDbSpl = level, blockSeconds = BLOCK)
        }

    private companion object {
        /** 1024 samples at 48 kHz — the block size the feature actually reads. */
        const val BLOCK = 1024.0 / 48_000.0
    }
}
