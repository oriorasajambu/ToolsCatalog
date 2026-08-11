package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.Weighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The filters, against IEC 61672-1.
 *
 * This is the reason `:core:sound` exists as its own module. Nothing else about a phone sound meter
 * has external ground truth — the microphone's sensitivity is unknowable, so the absolute level can
 * only ever be approximate — but the *shape* of the weighting curves is published to a stated
 * tolerance at every octave centre. If these pass, the filters are right; the remaining uncertainty
 * is an offset, which is a different and honestly-labelled problem.
 *
 * Two independent checks on each figure. [responseDbAt] evaluates the transfer function
 * analytically, while `measuredResponseDb` pushes a real sine wave through the sample loop and
 * measures what comes out. A mistake in the coefficient arithmetic would fail the first; a mistake
 * in the delay line or the cascade order would fail only the second.
 */
class WeightingFilterTest {

    private val factory = WeightingFilterFactory()

    // region The standard's tables

    /**
     * IEC 61672-1 Table 3, nominal A-weighting at the octave centres, in dB.
     *
     * These are the numbers on the back of every sound level meter's datasheet.
     */
    private val aWeightingDb = mapOf(
        31.5 to -39.4,
        63.0 to -26.2,
        125.0 to -16.1,
        250.0 to -8.6,
        500.0 to -3.2,
        1000.0 to 0.0,
        2000.0 to 1.2,
        4000.0 to 1.0,
        8000.0 to -1.1,
        16000.0 to -6.6,
    )

    /** IEC 61672-1 Table 3, nominal C-weighting. Flat through the middle, which is the point. */
    private val cWeightingDb = mapOf(
        31.5 to -3.0,
        63.0 to -0.8,
        125.0 to -0.2,
        250.0 to 0.0,
        500.0 to 0.0,
        1000.0 to 0.0,
        2000.0 to -0.2,
        4000.0 to -0.8,
        8000.0 to -3.0,
        16000.0 to -8.5,
    )

    /** An acceptance band around a nominal value, asymmetric where it needs to be. */
    private data class Tolerance(val minus: Double, val plus: Double) {
        constructor(symmetric: Double) : this(symmetric, symmetric)

        fun accepts(error: Double) = error >= -minus && error <= plus
    }

    /**
     * This implementation's own budget — **not** the standard's tolerance table.
     *
     * Deliberately not quoted from IEC 61672-1's acceptance limits. Those are what an *instrument*
     * must meet, and this is not an instrument; quoting them would imply a conformance claim the app
     * explicitly disclaims. What these are is a regression bound measured from the filter as built,
     * set tight enough that any real mistake breaks them.
     *
     * Through the band that matters they are far tighter than the standard would require: ±0.5 dB
     * from 31.5 Hz to 4 kHz, where the realisation actually lands within 0.32 dB. The band widens at
     * 8 kHz and opens asymmetrically downwards at 16 kHz — see the dedicated test below, which pins
     * that deviation to a range rather than letting a loose band hide it.
     */
    private val toleranceDb = mapOf(
        31.5 to Tolerance(0.5),
        63.0 to Tolerance(0.5),
        125.0 to Tolerance(0.5),
        250.0 to Tolerance(0.5),
        500.0 to Tolerance(0.5),
        1000.0 to Tolerance(0.01),
        2000.0 to Tolerance(0.5),
        4000.0 to Tolerance(0.5),
        8000.0 to Tolerance(1.0),
        16000.0 to Tolerance(minus = 5.0, plus = 1.0),
    )

    // endregion

    @Test
    fun `A-weighting matches the standard at 48kHz`() {
        assertMatchesTable(Weighting.A, aWeightingDb, sampleRate = 48_000)
    }

    /**
     * The same assertions at 44.1 kHz.
     *
     * This is the test that catches the most tempting shortcut in the whole module: pasting a table
     * of biquad coefficients from a reference implementation, which are only correct for the one
     * sample rate they were generated at. Android decides the rate at runtime from what the hardware
     * accepts, so a filter that is right at 48 kHz and wrong at 44.1 would be wrong on some devices
     * and right on others, with nothing on screen to distinguish them.
     */
    @Test
    fun `A-weighting matches the standard at 44_1kHz`() {
        assertMatchesTable(Weighting.A, aWeightingDb, sampleRate = 44_100)
    }

    @Test
    fun `C-weighting matches the standard at 48kHz`() {
        assertMatchesTable(Weighting.C, cWeightingDb, sampleRate = 48_000)
    }

    @Test
    fun `C-weighting matches the standard at 44_1kHz`() {
        assertMatchesTable(Weighting.C, cWeightingDb, sampleRate = 44_100)
    }

    /**
     * Both curves pass through exactly 0 dB at 1 kHz.
     *
     * Separated from the table check with a far tighter bound, because this one is not a tolerance —
     * it is the definition. The whole curve is anchored here, so an error at 1 kHz is an error
     * everywhere, and it would show up in the table check as a uniform bias that still fitted inside
     * the wider bands.
     */
    @Test
    fun `A and C are exactly unity at 1kHz`() {
        for (weighting in listOf(Weighting.A, Weighting.C)) {
            for (sampleRate in listOf(44_100, 48_000)) {
                val filter = factory.create(weighting, sampleRate)
                assertEquals(
                    "$weighting at $sampleRate",
                    0.0,
                    filter.responseDbAt(1000.0),
                    1e-9,
                )
            }
        }
    }

    /**
     * The one place this realisation is genuinely off, pinned so it cannot drift unnoticed.
     *
     * The bilinear transform squeezes the whole infinite analogue frequency axis into the range up
     * to Nyquist, so the compression grows without bound as you approach it. Prewarping each section
     * pins its own corner frequency exactly, but nothing can straighten the axis everywhere at once
     * — and at 16 kHz against a 44.1 or 48 kHz rate there is not enough axis left. The result is
     * 3 to 4 dB of extra roll-off, always downwards.
     *
     * This is accepted rather than fixed, for two reasons. It is a property of the transform, not a
     * mistake — every bilinear realisation of these curves has it. And the effect on the reading is
     * negligible: A-weighting is already 6.6 dB down at 16 kHz, ambient noise carries very little
     * energy up there, and an error that only ever *under*-reports the least significant octave is
     * the safest possible place for one.
     *
     * The assertion is a **range, not a ceiling**. If someone later moves to a matched-z or
     * frequency-sampled design the error will shrink, and this test will fail and ask to be updated
     * rather than silently passing and leaving the comment above as a lie.
     */
    @Test
    fun `the bilinear transform costs 3 to 4dB at 16kHz`() {
        for (sampleRate in listOf(44_100, 48_000)) {
            val error = factory.create(Weighting.A, sampleRate).responseDbAt(16_000.0) -
                aWeightingDb.getValue(16000.0)

            assertTrue(
                "A at 16kHz/$sampleRate should be 3-5dB low, was ${-error}dB low",
                error < -3.0 && error > -5.0,
            )
        }
    }

    /**
     * Z is passthrough, sample for sample.
     *
     * Not "flat to within a tolerance" — the cascade is empty and the gain is one, so the output is
     * the input. Asserting bit-equality is what pins that down: a Z implemented as a flat filter
     * would pass an approximate check while quietly adding a delay line's worth of rounding.
     */
    @Test
    fun `Z weighting is bit-exact passthrough`() {
        val filter = factory.create(Weighting.Z, sampleRate = 48_000)
        val input = DoubleArray(512) { sin(2.0 * PI * 440.0 * it / 48_000.0) * 0.7 }
        val output = DoubleArray(512)

        filter.process(input, input.size, output)

        for (index in input.indices) {
            assertEquals(input[index], output[index], 0.0)
        }
    }

    /**
     * C reads higher than A on bass, by a lot.
     *
     * The practical consequence of the two curves, and the thing a user would notice first if the
     * weightings were wired to the wrong filters. At 63 Hz the gap is over 25 dB.
     */
    @Test
    fun `C reads far higher than A at low frequency`() {
        val a = factory.create(Weighting.A, 48_000)
        val c = factory.create(Weighting.C, 48_000)

        val gap = c.responseDbAt(63.0) - a.responseDbAt(63.0)

        assertTrue("C should exceed A at 63Hz by >20dB, was $gap", gap > 20.0)
    }

    /** A filter that has been used and reset behaves like one that never has. */
    @Test
    fun `reset clears the delay line`() {
        val filter = factory.create(Weighting.A, 48_000)
        val loud = DoubleArray(4096) { sin(2.0 * PI * 100.0 * it / 48_000.0) }
        val scratch = DoubleArray(4096)

        filter.process(loud, loud.size, scratch)
        val afterUse = measuredResponseDb(filter, frequencyHz = 1000.0, reset = true)

        val fresh = measuredResponseDb(
            factory.create(Weighting.A, 48_000),
            frequencyHz = 1000.0,
            reset = false,
        )

        assertEquals(fresh, afterUse, 1e-9)
    }

    // region Helpers

    /**
     * Checks a whole curve two ways: analytically, and by measuring a sine through the filter.
     */
    private fun assertMatchesTable(
        weighting: Weighting,
        table: Map<Double, Double>,
        sampleRate: Int,
    ) {
        val filter = factory.create(weighting, sampleRate)

        for ((frequency, expected) in table) {
            val tolerance = toleranceDb.getValue(frequency)

            val evaluated = filter.responseDbAt(frequency)
            assertTrue(
                "$weighting evaluated at ${frequency}Hz/$sampleRate: " +
                    "expected $expected +${tolerance.plus}/-${tolerance.minus}, was $evaluated",
                tolerance.accepts(evaluated - expected),
            )

            val measured = measuredResponseDb(filter, frequency, reset = true)
            assertTrue(
                "$weighting measured at ${frequency}Hz/$sampleRate: " +
                    "expected $expected +${tolerance.plus}/-${tolerance.minus}, was $measured",
                tolerance.accepts(measured - expected),
            )
        }
    }

    /**
     * Drives a unit sine through the filter and measures the steady-state gain, in dB.
     *
     * The first second is discarded before measuring. A-weighting's 20.6 Hz poles have a time
     * constant of several milliseconds and the filter starts from rest, so measuring from the first
     * sample would report the transient rather than the response — which at low frequencies is a
     * completely different number.
     */
    private fun measuredResponseDb(
        filter: WeightingFilter,
        frequencyHz: Double,
        reset: Boolean,
    ): Double {
        if (reset) filter.reset()

        val sampleRate = filter.sampleRate
        val settleSamples = sampleRate
        val measureSamples = sampleRate

        val input = DoubleArray(settleSamples + measureSamples) {
            sin(2.0 * PI * frequencyHz * it / sampleRate)
        }
        val output = DoubleArray(input.size)
        filter.process(input, input.size, output)

        var sumOfSquares = 0.0
        for (index in settleSamples until output.size) {
            sumOfSquares += output[index] * output[index]
        }
        val rms = sqrt(sumOfSquares / measureSamples)

        // A unit sine has an RMS of 1/√2, so that is the reference the gain is taken against.
        return 20.0 * log10(rms * sqrt(2.0))
    }

    // endregion
}
