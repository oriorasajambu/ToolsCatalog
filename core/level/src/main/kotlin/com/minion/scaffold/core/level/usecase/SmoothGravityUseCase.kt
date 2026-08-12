package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.GravitySample
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Accumulated filter state. Threaded by the caller so nothing here holds mutable state.
 *
 * `null` fields mean "not yet seeded" — the filter starts from the first real sample rather than
 * from zero, or every entry to the screen would show a visible crawl up from "level" to the true
 * reading.
 */
data class SmoothingState(
    /** The smoothed sample, or `null` before the filter is seeded. */
    val value: GravitySample? = null,
    /** The filtered per-axis derivative, or `null` before the filter is seeded. */
    val derivative: Triple<Double, Double, Double>? = null,
    /** The previous sample's timestamp in nanoseconds, or `null` before the filter is seeded. */
    val lastTimestampNanos: Long? = null,
)

/**
 * Steadies the gravity reading without making it feel laggy.
 *
 * ## Filter the vector, not the angles
 *
 * Angles are a nonlinear function of the gravity vector, so filtering degrees introduces bias,
 * misbehaves across the ±180° seam of the downhill bearing, and lets the pose machine disagree with
 * the number on screen. Filtering the vector and deriving angles afterwards avoids all three — and
 * hands the bullseye its bubble position for free, since that is linear in the vector.
 *
 * ## The 1€ filter, not a plain EMA
 *
 * A fixed low-pass forces a choice between a jittery reading at rest and a sluggish one when the
 * user deliberately tilts the phone. The 1€ filter resolves it by making the cutoff a function of
 * how fast the signal is moving: slow when still, so noise is crushed; fast when moving, so it
 * tracks. Named rather than bespoke, so a reader can look it up.
 *
 * **One cutoff for all three components, computed from the *vector* speed.** Filtering each axis at
 * its own rate would smooth them by different amounts and so spuriously *rotate* the vector during
 * motion — which shows up as the bubble taking a curved path when the phone is tilted diagonally.
 */
class SmoothGravityUseCase @Inject constructor() {

    /**
     * Folds one raw sample into the smoothing filter.
     *
     * @param state  The accumulated filter state from the previous call.
     * @param sample The raw gravity sample.
     * @return The updated state whose [SmoothingState.value] is the steadied reading.
     */
    operator fun invoke(state: SmoothingState, sample: GravitySample): SmoothingState {
        val previous = state.value
        val previousTimestamp = state.lastTimestampNanos

        if (previous == null || previousTimestamp == null) return seed(sample)

        val elapsed = (sample.timestampNanos - previousTimestamp) / NANOS_PER_SECOND

        // Checked against the *raw* gap, before the clamp below — clamping first would cap it at
        // MAX_DT_SECONDS and this branch could never fire. A long gap means the screen was
        // backgrounded or the sensor stalled, and restarting beats interpolating: the alternative
        // is a slow crawl up from a stale value on every resume.
        if (elapsed >= RESET_SECONDS) return seed(sample)

        val dt = resolveDt(sample.timestampNanos - previousTimestamp)

        val rawDerivative = Triple(
            (sample.x - previous.x) / dt,
            (sample.y - previous.y) / dt,
            (sample.z - previous.z) / dt,
        )
        val derivative = state.derivative
            ?.let { lowPass(it, rawDerivative, alphaFor(DERIVATIVE_CUTOFF_HZ, dt)) }
            ?: rawDerivative

        val speed = sqrt(
            derivative.first * derivative.first +
                derivative.second * derivative.second +
                derivative.third * derivative.third,
        )
        val cutoff = MIN_CUTOFF_HZ + SPEED_COEFFICIENT * speed
        val alpha = alphaFor(cutoff, dt)

        return SmoothingState(
            value = GravitySample(
                x = previous.x + alpha * (sample.x - previous.x),
                y = previous.y + alpha * (sample.y - previous.y),
                z = previous.z + alpha * (sample.z - previous.z),
                timestampNanos = sample.timestampNanos,
            ),
            derivative = derivative,
            lastTimestampNanos = sample.timestampNanos,
        )
    }

    private fun seed(sample: GravitySample) = SmoothingState(
        value = sample,
        derivative = null,
        lastTimestampNanos = sample.timestampNanos,
    )

    /**
     * Seconds between samples, defended against what sensor timestamps actually do.
     *
     * `SensorEvent.timestamp` is monotone in principle and duplicated or briefly rolled back in
     * practice, and `SENSOR_DELAY_GAME` is only a hint — devices deliver anywhere from 30 to 200Hz.
     * That is why the filter is dt-based rather than assuming a rate.
     */
    private fun resolveDt(deltaNanos: Long): Double {
        if (deltaNanos <= 0L) return DEFAULT_DT_SECONDS
        return (deltaNanos / NANOS_PER_SECOND).coerceIn(MIN_DT_SECONDS, MAX_DT_SECONDS)
    }

    /**
     * `1 − exp(−dt/τ)` — the exact discretisation of a first-order low-pass.
     *
     * Not `dt/(dt+τ)`, which is the backward-Euler approximation and drifts at large dt, and
     * certainly not a fixed alpha, which would make the filter's behaviour depend on the sample
     * rate the device happens to deliver.
     */
    private fun alphaFor(cutoffHz: Double, dt: Double): Double {
        val tau = 1.0 / (TWO_PI * cutoffHz)
        return 1.0 - exp(-dt / tau)
    }

    private fun lowPass(
        previous: Triple<Double, Double, Double>,
        raw: Triple<Double, Double, Double>,
        alpha: Double,
    ) = Triple(
        previous.first + alpha * (raw.first - previous.first),
        previous.second + alpha * (raw.second - previous.second),
        previous.third + alpha * (raw.third - previous.third),
    )

    private companion object {

        const val TWO_PI = 2.0 * Math.PI
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Cutoff when the device is still — τ ≈ 0.4s, which crushes noise well below 0.01°. */
        const val MIN_CUTOFF_HZ = 0.4

        /** How much the cutoff opens up with movement. Tuned on device; higher tracks faster. */
        const val SPEED_COEFFICIENT = 0.5

        /** Cutoff for the speed estimate itself, so the adaptation is not driven by noise. */
        const val DERIVATIVE_CUTOFF_HZ = 1.0

        const val MIN_DT_SECONDS = 0.001
        const val MAX_DT_SECONDS = 0.100
        const val DEFAULT_DT_SECONDS = 0.020

        /** Past this gap, restart rather than interpolate. */
        const val RESET_SECONDS = 0.250
    }
}
