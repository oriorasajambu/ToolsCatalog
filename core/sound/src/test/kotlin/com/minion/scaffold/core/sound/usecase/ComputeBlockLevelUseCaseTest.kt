package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.BlockLevel
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.Weighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class ComputeBlockLevelUseCaseTest {

    private val computeBlockLevel = ComputeBlockLevelUseCase()
    private val factory = WeightingFilterFactory()

    /**
     * A full-scale sine reads −3.01 dBFS, exactly.
     *
     * The one figure in this module with a closed form: a sine's RMS is its amplitude over √2, and
     * `20·log₁₀(1/√2)` is −3.0103. It pins the conversion against the classic off-by-3 — confusing
     * amplitude with power, or 10·log with 20·log — which produces numbers that are wrong by a
     * factor of two in dB terms while still looking entirely like decibels.
     */
    @Test
    fun `a full-scale sine is 3_01 dB below full scale`() {
        val level = computeBlockLevel(
            raw = sineBlock(frequencyHz = 1000.0, amplitude = 0.999),
            weighted = weight(sineBlock(1000.0, 0.999), Weighting.Z),
            count = BLOCK,
            offsetDb = 0.0,
        )

        assertTrue(level is BlockLevel.Measured)
        assertEquals(-3.01, (level as BlockLevel.Measured).dbSpl, 0.01)
    }

    /** The offset is a straight shift, so a dB SPL reading tracks the slider one for one. */
    @Test
    fun `the offset shifts the level one for one`() {
        val block = sineBlock(1000.0, 0.5)
        val weighted = weight(block, Weighting.Z)

        val base = computeBlockLevel(block, weighted, BLOCK, offsetDb = 100.0)
        val shifted = computeBlockLevel(block, weighted, BLOCK, offsetDb = 112.0)

        assertEquals(
            12.0,
            (shifted as BlockLevel.Measured).dbSpl - (base as BlockLevel.Measured).dbSpl,
            1e-9,
        )
    }

    /** One sample on the rail is enough. See the use case for why it fails in this direction. */
    @Test
    fun `a single full-scale sample clips the block`() {
        val block = sineBlock(1000.0, 0.5)
        block[BLOCK / 2] = Short.MAX_VALUE

        val level = computeBlockLevel(block, weight(block, Weighting.Z), BLOCK, offsetDb = 105.0)

        assertEquals(BlockLevel.Clipped, level)
    }

    @Test
    fun `the negative rail clips too`() {
        val block = sineBlock(1000.0, 0.5)
        block[3] = Short.MIN_VALUE

        val level = computeBlockLevel(block, weight(block, Weighting.Z), BLOCK, offsetDb = 105.0)

        assertEquals(BlockLevel.Clipped, level)
    }

    /**
     * A loud-but-unclipped block is measured, not rejected.
     *
     * The counterpart to the tests above, and the one that stops an over-eager clip detector from
     * making the meter useless: at 0.99 of full scale the converter is fine, and refusing to report
     * would turn every loud measurement into an error.
     */
    @Test
    fun `a block peaking just below the rail is measured`() {
        val block = sineBlock(1000.0, 0.99)

        val level = computeBlockLevel(block, weight(block, Weighting.Z), BLOCK, offsetDb = 105.0)

        assertTrue("expected a measurement, was $level", level is BlockLevel.Measured)
    }

    @Test
    fun `near-silence is below the floor rather than a very quiet reading`() {
        val block = sineBlock(1000.0, amplitude = 0.00002)

        val level = computeBlockLevel(block, weight(block, Weighting.Z), BLOCK, offsetDb = 105.0)

        assertEquals(BlockLevel.BelowFloor, level)
    }

    @Test
    fun `digital silence is below the floor`() {
        val block = ShortArray(BLOCK)

        val level = computeBlockLevel(block, DoubleArray(BLOCK), BLOCK, offsetDb = 105.0)

        assertEquals(BlockLevel.BelowFloor, level)
    }

    /**
     * Clipping is judged on the raw input, never on the weighted signal.
     *
     * A 30 Hz rumble hard against the rails is destroying the waveform, and A-weighting takes about
     * 40 dB off it — so a detector looking at the weighted signal would see something modest and
     * report a comfortable number for an input the converter cannot represent. This is the specific
     * inversion the use case's two-signal shape exists to prevent, and it is worth a test because
     * the shortcut version compiles and looks reasonable.
     */
    @Test
    fun `a clipped bass rumble clips even though A-weighting hides it`() {
        val block = sineBlock(frequencyHz = 30.0, amplitude = 1.5)

        val level = computeBlockLevel(block, weight(block, Weighting.A), BLOCK, offsetDb = 105.0)

        assertEquals(BlockLevel.Clipped, level)
    }

    /** An empty block is an absence of measurement, not a zero. */
    @Test
    fun `an empty block is below the floor`() {
        val level = computeBlockLevel(ShortArray(0), DoubleArray(0), 0, offsetDb = 105.0)

        assertEquals(BlockLevel.BelowFloor, level)
    }

    // region Helpers

    /** A sine at [amplitude] relative to full scale, saturating at the rails like a real converter. */
    private fun sineBlock(frequencyHz: Double, amplitude: Double) = ShortArray(BLOCK) { index ->
        val value = amplitude * SoundReference.FULL_SCALE_PCM16 *
            sin(2.0 * PI * frequencyHz * index / SAMPLE_RATE)
        value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun weight(block: ShortArray, weighting: Weighting): DoubleArray {
        val output = DoubleArray(block.size)
        factory.create(weighting, SAMPLE_RATE).process(block, block.size, output)
        return output
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK = 4800
    }
}
