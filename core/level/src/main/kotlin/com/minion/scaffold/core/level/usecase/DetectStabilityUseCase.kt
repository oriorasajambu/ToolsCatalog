package com.minion.scaffold.core.level.usecase

import com.minion.scaffold.core.level.model.UpVector
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.sqrt

/** How much the reading can be trusted right now. */
enum class Steadiness {

    /** Moving. The number on screen is not a measurement of anything. */
    Moving,

    /** Slowing, but not yet trustworthy enough to capture from. */
    Settling,

    /** Still. Safe to read, and safe to capture a calibration point or a reference from. */
    Steady,
}

/** Accumulated stability state, threaded by the caller. */
data class StabilityState(
    /** The current stability verdict. */
    val steadiness: Steadiness = Steadiness.Settling,
    /** The smoothed angular rate of the up-vector, in radians per second. */
    val smoothedRate: Double = 0.0,
    /** The previous up-vector, or `null` before the first sample. */
    val previousUp: UpVector? = null,
    /** The previous sample's timestamp in nanoseconds, or `null` before the first sample. */
    val lastTimestampNanos: Long? = null,

    /** How long the rate has been below the steady threshold, in nanoseconds. */
    val calmForNanos: Long = 0L,
)

/**
 * Decides whether the device is still enough for its reading to mean anything.
 *
 * This matters more than it sounds. When the phone is being moved, acceleration contaminates the
 * gravity estimate and the angle it reports is simply wrong — and a wrong angle is indistinguishable
 * from a right one on screen. Without this, a reading taken while sliding the phone along a beam is
 * silently false.
 *
 * ## Why not `|‖g‖ − 9.81|`
 *
 * The obvious motion signal is the gravity vector's magnitude departing from 9.81. Three problems,
 * and the third is fatal: it is blind to pure rotation, 9.81 is wrong by up to 0.5% with latitude
 * and by 1–2% again from the sensor's own scale error, and — decisively — **many devices force
 * `TYPE_GRAVITY`'s magnitude to a constant** as part of the fusion, so the signal does not exist at
 * all. It survives here only as the plausibility gate on [
 * com.minion.scaffold.core.level.model.GravitySample].
 *
 * ## What is used instead
 *
 * The angular rate of the up-vector: how far the direction of gravity moved, per second. That works
 * with either sensor source and catches rotation, which is the motion that actually matters.
 *
 * The thresholds are **asymmetric on purpose**. Becoming Moving is instant, because a warning that
 * arrives late is useless; becoming Steady takes [CALM_NANOS] of sustained quiet, because a reading
 * that flickers "trustworthy" for one frame invites capturing at exactly the wrong moment. That
 * asymmetry is what stops the indicator strobing.
 */
class DetectStabilityUseCase @Inject constructor() {

    /**
     * Folds one reading into the stability state.
     *
     * @param state          The accumulated state from the previous call.
     * @param up             The current up-vector.
     * @param timestampNanos The current sample's timestamp in nanoseconds.
     * @return The updated state, carrying the new [Steadiness] verdict.
     */
    operator fun invoke(
        state: StabilityState,
        up: UpVector,
        timestampNanos: Long,
    ): StabilityState {
        val previousUp = state.previousUp
        val previousTimestamp = state.lastTimestampNanos

        if (previousUp == null || previousTimestamp == null) {
            return state.copy(previousUp = up, lastTimestampNanos = timestampNanos)
        }

        val deltaNanos = timestampNanos - previousTimestamp
        val dt = if (deltaNanos <= 0L) DEFAULT_DT_SECONDS else deltaNanos / NANOS_PER_SECOND

        // Chord length approximates the arc for the small angles between consecutive samples, so
        // this is the angular rate in radians per second without needing a trig call per sample.
        val dx = up.x - previousUp.x
        val dy = up.y - previousUp.y
        val dz = up.z - previousUp.z
        val rate = sqrt(dx * dx + dy * dy + dz * dz) / dt

        val alpha = 1.0 - exp(-dt / RATE_TAU_SECONDS)
        val smoothed = state.smoothedRate + alpha * (rate - state.smoothedRate)

        val calmFor = if (smoothed < STEADY_RATE) state.calmForNanos + deltaNanos else 0L

        val steadiness = when {
            smoothed > MOVING_RATE -> Steadiness.Moving
            calmFor >= CALM_NANOS -> Steadiness.Steady
            else -> Steadiness.Settling
        }

        return StabilityState(
            steadiness = steadiness,
            smoothedRate = smoothed,
            previousUp = up,
            lastTimestampNanos = timestampNanos,
            calmForNanos = calmFor,
        )
    }

    private companion object {

        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val DEFAULT_DT_SECONDS = 0.020

        /** Smoothing on the rate itself, so one noisy sample does not read as movement. */
        const val RATE_TAU_SECONDS = 0.25

        /** Above this, say so immediately. ~3.4°/s. */
        const val MOVING_RATE = 0.06

        /** Below this for [CALM_NANOS], it is trustworthy. ~1.1°/s. */
        const val STEADY_RATE = 0.02

        const val CALM_NANOS = 300_000_000L
    }
}
