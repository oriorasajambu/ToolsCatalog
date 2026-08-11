package com.minion.scaffold.core.sound.model

import kotlin.math.log10

/**
 * What a measurement session came to.
 *
 * Every field is nullable-or-zero rather than defaulted to a plausible number, because a session
 * that has measured nothing must be visibly empty on screen instead of reporting a confident 0 dB.
 */
data class SessionStats(
    /** Lowest time-weighted level seen, in dB SPL. */
    val minDbSpl: Double?,

    /** Highest time-weighted level seen, in dB SPL. */
    val maxDbSpl: Double?,

    /** The energy average over everything measurable. See [SessionState.leqDbSpl]. */
    val leqDbSpl: Double?,

    /** Wall time since the session started, measurable or not. */
    val durationSeconds: Double,

    /** How long the level sat at or above [SoundReference.EXPOSURE_THRESHOLD_DB]. */
    val secondsAboveThreshold: Double,

    /**
     * How long the input was clipped or below the noise floor.
     *
     * Reported rather than swallowed. A session that spent a third of its time out of range is not
     * a session whose Leq means what it appears to mean, and the only way the user can know that is
     * if the meter says so.
     */
    val unmeasurableSeconds: Double,
) {

    /** Whether the session measured anything, i.e. it has an [leqDbSpl]. */
    val hasMeasurement: Boolean get() = leqDbSpl != null

    companion object {
        val EMPTY = SessionStats(
            minDbSpl = null,
            maxDbSpl = null,
            leqDbSpl = null,
            durationSeconds = 0.0,
            secondsAboveThreshold = 0.0,
            unmeasurableSeconds = 0.0,
        )
    }
}

/**
 * The running accumulators behind [SessionStats].
 *
 * Separate from the stats for the same reason `SmoothingState` is separate from `Tilt` in
 * `:core:level`: this is bookkeeping the screen has no business seeing, and keeping it out of the
 * rendered type means the UI cannot come to depend on an intermediate.
 *
 * [energySeconds] is what makes Leq O(1) rather than O(session). A running sum of energy × time
 * needs no history at all, so a fifteen-minute measurement costs exactly as much as a one-second
 * one — which is the difference between a feature that works and one that has to cap its duration.
 */
data class SessionState(
    /** Running sum of energy × time over measurable blocks — the numerator of Leq. */
    val energySeconds: Double = 0.0,
    /** Total time of measurable blocks — the denominator of Leq. */
    val measuredSeconds: Double = 0.0,
    /** Wall time since the session started, measurable or not. */
    val durationSeconds: Double = 0.0,
    /** Lowest time-weighted level seen so far, or `null` before any measurement. */
    val minDbSpl: Double? = null,
    /** Highest time-weighted level seen so far, or `null` before any measurement. */
    val maxDbSpl: Double? = null,
    /** Total time at or above [SoundReference.EXPOSURE_THRESHOLD_DB]. */
    val secondsAboveThreshold: Double = 0.0,
    /** Total time the input was clipped or below the noise floor. */
    val unmeasurableSeconds: Double = 0.0,
) {

    /**
     * The equivalent continuous level — the steady level carrying the same energy as what happened.
     *
     * **Divided by [measuredSeconds], not [durationSeconds].** Time the input was out of range is
     * not silence; counting it as silence would let a stretch of clipping — the loudest part of the
     * session — quietly *reduce* the average, which is the exact inversion this feature is built to
     * prevent.
     */
    val leqDbSpl: Double?
        get() = if (measuredSeconds > 0.0 && energySeconds > 0.0) {
            10.0 * log10(energySeconds / measuredSeconds)
        } else {
            null
        }

    /**
     * A render-ready snapshot of these accumulators.
     *
     * @return The [SessionStats] the screen displays.
     */
    fun toStats(): SessionStats = SessionStats(
        minDbSpl = minDbSpl,
        maxDbSpl = maxDbSpl,
        leqDbSpl = leqDbSpl,
        durationSeconds = durationSeconds,
        secondsAboveThreshold = secondsAboveThreshold,
        unmeasurableSeconds = unmeasurableSeconds,
    )
}
