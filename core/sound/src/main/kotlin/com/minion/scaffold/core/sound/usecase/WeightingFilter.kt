package com.minion.scaffold.core.sound.usecase

import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.core.sound.model.Weighting
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The A, C and Z frequency-weighting filters from IEC 61672-1.
 *
 * ## Why the coefficients are computed rather than written down
 *
 * The standard defines these curves as *analogue* transfer functions — a pole at 20.6 Hz, and so on.
 * Turning one into something that can run on a sample stream means a bilinear transform, and the
 * bilinear transform's coefficients depend on the sample rate. Android hands back 48 kHz on most
 * devices and 44.1 kHz on some, decided at runtime by what the hardware accepts.
 *
 * So the tempting shortcut — paste a table of coefficients from a reference implementation — is
 * wrong in a way that leaves no trace: the filter still runs, the numbers still look like decibels,
 * and every reading on a 44.1 kHz device is quietly off. [WeightingFilterFactory] therefore builds
 * the sections from the pole frequencies at whatever rate it is given, and the tests assert the
 * response at **both** rates for exactly this reason.
 *
 * ## Prewarping is not optional here
 *
 * The bilinear transform compresses the frequency axis, and the compression is severe near Nyquist.
 * A-weighting's outer poles sit at 12194 Hz, which at 48 kHz is a quarter of the way to Nyquist and
 * lands roughly 22% low if transformed naively — visible as several dB of error at 8 and 16 kHz.
 * Each section is therefore prewarped at its own characteristic frequency, so that frequency maps
 * exactly and the error is pushed out to where the tolerance band is widest.
 *
 * ## What holds state
 *
 * This object owns its delay line, which is the one deliberate exception to this module's
 * no-mutable-state rule (see the module's `build.gradle.kts`). A filter's delay line is not
 * accumulated *domain* state; it is created per capture session, [reset] explicitly, and never
 * injected. Threading four doubles per section through a call per sample would be both unreadable
 * and slow enough to matter at 48000 of them a second.
 */
class WeightingFilter internal constructor(
    /** The weighting curve this filter applies. */
    val weighting: Weighting,
    /** The sample rate the filter's coefficients were built for, in Hz. */
    val sampleRate: Int,
    private val sections: List<Biquad>,
    /** Chosen so the cascade is exactly 0 dB at 1 kHz, which is how the standard defines these. */
    private val gain: Double,
) {

    /** Clears the delay line. Call between sessions, or the first block inherits the last one. */
    fun reset() {
        for (section in sections) section.reset()
    }

    /**
     * Filters [count] samples of [input] into [output], normalised to ±1.0.
     *
     * In-place into a caller-owned buffer rather than returning a new array: this runs roughly fifty
     * times a second for the life of the screen, and allocating a block each time would hand the
     * collector a steady stream of garbage for no benefit.
     *
     * @param input  The raw 16-bit PCM block.
     * @param count  How many samples of [input] to process.
     * @param output The caller-owned buffer the filtered, normalised samples are written into.
     */
    fun process(input: ShortArray, count: Int, output: DoubleArray) {
        for (i in 0 until count) {
            output[i] = processSample(input[i] / SoundReference.FULL_SCALE_PCM16)
        }
    }

    /** As [process], for callers that already hold normalised samples — the tests, in practice. */
    internal fun process(input: DoubleArray, count: Int, output: DoubleArray) {
        for (i in 0 until count) output[i] = processSample(input[i])
    }

    private fun processSample(sample: Double): Double {
        var value = sample
        for (section in sections) value = section.process(value)
        return value * gain
    }

    /**
     * The cascade's gain at [frequencyHz], in dB — the curve itself, evaluated rather than measured.
     *
     * Used to normalise at 1 kHz when the filter is built, and by the tests to compare against the
     * standard's table. The tests *also* push real sine waves through [process] and compare the
     * result, so a mistake in this evaluation cannot quietly validate a mistake in the filter.
     */
    internal fun responseDbAt(frequencyHz: Double): Double {
        val magnitude = magnitudeAt(frequencyHz) * gain
        return if (magnitude <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(magnitude)
    }

    private fun magnitudeAt(frequencyHz: Double): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRate
        var magnitude = 1.0
        for (section in sections) magnitude *= section.magnitudeAt(omega)
        return magnitude
    }

    internal companion object {

        /**
         * Builds a cascade from analogue sections and normalises it to 0 dB at [NORMALISE_HZ].
         *
         * Normalising the *digital* cascade rather than scaling by the standard's analogue constant
         * is deliberate: prewarping shifts the response slightly, and the standard's requirement is
         * that the curve pass through 0 dB at 1 kHz — so it is the built filter that has to satisfy
         * it, not the prototype it came from.
         *
         * @param weighting      The weighting curve being built.
         * @param sampleRate     The sample rate to design the digital sections for, in Hz.
         * @param analogSections The analogue prototype sections to transform and cascade.
         * @return A [WeightingFilter] normalised to 0 dB at [NORMALISE_HZ].
         */
        fun build(
            weighting: Weighting,
            sampleRate: Int,
            analogSections: List<AnalogSection>,
        ): WeightingFilter {
            val biquads = analogSections.map { it.toDigital(sampleRate) }

            var magnitudeAtReference = 1.0
            val omega = 2.0 * PI * NORMALISE_HZ / sampleRate
            for (biquad in biquads) magnitudeAtReference *= biquad.magnitudeAt(omega)

            return WeightingFilter(
                weighting = weighting,
                sampleRate = sampleRate,
                sections = biquads,
                gain = if (magnitudeAtReference > 0.0) 1.0 / magnitudeAtReference else 1.0,
            )
        }

        const val NORMALISE_HZ = 1000.0
    }
}

/**
 * One second-order section of the analogue prototype, `(b0 s² + b1 s + b2) / (a0 s² + a1 s + a2)`.
 *
 * [criticalRadiansPerSecond] is the frequency the bilinear transform is prewarped to map exactly —
 * the section's own pole frequency, or the geometric mean where it has two.
 */
internal class AnalogSection(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a0: Double,
    private val a1: Double,
    private val a2: Double,
    private val criticalRadiansPerSecond: Double,
) {

    /**
     * Bilinear transform: `s → K(1 − z⁻¹)/(1 + z⁻¹)`, multiplied through by `(1 + z⁻¹)²`.
     *
     * Factorising the filter into sections and transforming each one separately gives exactly the
     * same result as transforming the whole product, because the transform is a substitution — so
     * the arithmetic below is not an approximation of the cascade, it *is* the cascade.
     */
    fun toDigital(sampleRate: Int): Biquad {
        val k = prewarpedK(sampleRate)
        val k2 = k * k

        val n0 = b0 * k2 + b1 * k + b2
        val n1 = 2.0 * (b2 - b0 * k2)
        val n2 = b0 * k2 - b1 * k + b2

        val d0 = a0 * k2 + a1 * k + a2
        val d1 = 2.0 * (a2 - a0 * k2)
        val d2 = a0 * k2 - a1 * k + a2

        return Biquad(
            b0 = n0 / d0,
            b1 = n1 / d0,
            b2 = n2 / d0,
            a1 = d1 / d0,
            a2 = d2 / d0,
        )
    }

    /**
     * `K = ωc / tan(ωc·T/2)`, so that ωc lands on itself instead of being dragged downwards.
     *
     * Falls back to the unwarped `2/T` when the critical frequency is at or above Nyquist, where the
     * tangent is undefined or negative and would produce an unstable section. That cannot happen at
     * the rates this app opens (44.1 and 48 kHz against a 12194 Hz pole), but a filter that becomes
     * an oscillator on an unexpected sample rate is not a failure mode worth leaving open.
     */
    private fun prewarpedK(sampleRate: Int): Double {
        val unwarped = 2.0 * sampleRate
        val halfAngle = criticalRadiansPerSecond / (2.0 * sampleRate)
        if (halfAngle <= 0.0 || halfAngle >= PI / 2.0) return unwarped

        val tangent = tan(halfAngle)
        return if (tangent > 0.0) criticalRadiansPerSecond / tangent else unwarped
    }
}

/**
 * A digital second-order section, normalised so `a0 = 1`.
 *
 * Transposed Direct Form II: fewer state variables than Direct Form I and better behaved
 * numerically, which matters here because A-weighting's 20.6 Hz poles sit extremely close to `z = 1`
 * at a 48 kHz sample rate.
 */
internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {

    private var s1 = 0.0
    private var s2 = 0.0

    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }

    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    /** `|H(e^{jω})|`, evaluated directly from the coefficients. */
    fun magnitudeAt(omega: Double): Double {
        val cos1 = cos(omega)
        val sin1 = sin(omega)
        val cos2 = cos(2.0 * omega)
        val sin2 = sin(2.0 * omega)

        // e^{-jω} = cos ω − j sin ω, hence the negated imaginary parts.
        val numeratorReal = b0 + b1 * cos1 + b2 * cos2
        val numeratorImaginary = -(b1 * sin1 + b2 * sin2)
        val denominatorReal = 1.0 + a1 * cos1 + a2 * cos2
        val denominatorImaginary = -(a1 * sin1 + a2 * sin2)

        val numerator = sqrt(
            numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary,
        )
        val denominator = sqrt(
            denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary,
        )

        return if (denominator > 0.0) numerator / denominator else 0.0
    }
}

/**
 * Builds a [WeightingFilter] for a weighting and a sample rate.
 *
 * A factory rather than an injected filter because the filter holds a delay line and is bound to one
 * sample rate — a singleton would be shared across sessions and would be wrong the moment the device
 * opened the input at a different rate.
 */
class WeightingFilterFactory @Inject constructor() {

    /**
     * Builds a filter bound to one weighting and one sample rate.
     *
     * @param weighting  The frequency weighting to apply.
     * @param sampleRate The input's sample rate, in hertz.
     * @return A fresh filter, with a delay line of its own.
     */
    fun create(weighting: Weighting, sampleRate: Int): WeightingFilter = WeightingFilter.build(
        weighting = weighting,
        sampleRate = sampleRate,
        analogSections = when (weighting) {
            Weighting.A -> aSections()
            Weighting.C -> cSections()
            // No sections and unity gain: Z is the unweighted signal, and the empty cascade means
            // it is bit-exact passthrough rather than a flat filter that merely rounds to flat.
            Weighting.Z -> emptyList()
        },
    )

    /**
     * `H(s) = s⁴ / [(s + ω₁)² (s + ω₂)(s + ω₃)(s + ω₄)²]`, normalised at 1 kHz.
     *
     * Four zeros at the origin give the 24 dB/octave roll-off through the bass that is the whole
     * point of A-weighting; the ω₄ pair rolls the top off again above 12 kHz.
     */
    private fun aSections(): List<AnalogSection> = listOf(
        highPassPair(W1),
        highPassPair(W4),
        lowPassPair(W2, W3),
    )

    /**
     * `H(s) = ω₄² s² / [(s + ω₁)² (s + ω₄)²]`, normalised at 1 kHz.
     *
     * The same outer pole pairs as A with the middle section dropped, which is exactly why C reads
     * higher than A on anything bass-heavy: it is flat where A is falling away.
     */
    private fun cSections(): List<AnalogSection> = listOf(
        highPassPair(W1),
        lowPassPair(W4, W4),
    )

    /** `s² / (s + ω)²` — a double zero at DC against a double pole at ω. */
    private fun highPassPair(omega: Double) = AnalogSection(
        b0 = 1.0, b1 = 0.0, b2 = 0.0,
        a0 = 1.0, a1 = 2.0 * omega, a2 = omega * omega,
        criticalRadiansPerSecond = omega,
    )

    /** `1 / ((s + ωa)(s + ωb))` — two real poles, prewarped at their geometric mean. */
    private fun lowPassPair(omegaA: Double, omegaB: Double) = AnalogSection(
        b0 = 0.0, b1 = 0.0, b2 = 1.0,
        a0 = 1.0, a1 = omegaA + omegaB, a2 = omegaA * omegaB,
        criticalRadiansPerSecond = sqrt(omegaA * omegaB),
    )

    private companion object {

        /**
         * The pole frequencies of IEC 61672-1, in Hz, to the precision the standard states them.
         *
         * They look arbitrary because they are: the curves were fitted to equal-loudness contours,
         * not derived. Rounding them is not a simplification, it is a different filter.
         */
        const val F1 = 20.598997
        const val F2 = 107.65265
        const val F3 = 737.86223
        const val F4 = 12194.217

        val W1 = 2.0 * PI * F1
        val W2 = 2.0 * PI * F2
        val W3 = 2.0 * PI * F3
        val W4 = 2.0 * PI * F4
    }
}
