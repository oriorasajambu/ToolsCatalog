package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.model.GnssFix
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The running totals behind a trip.
 *
 * Separate from the rendered [TripStats] for the same reason `SessionState` is separate from
 * `SessionStats` in `:core:sound`: this is bookkeeping the screen has no business seeing.
 */
data class TripState(
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val maxSpeedMetersPerSecond: Double? = null,
    val elevationGainMeters: Double = 0.0,
    val minAltitudeMeters: Double? = null,
    val maxAltitudeMeters: Double? = null,

    internal val lastFix: GnssFix? = null,
    internal val lastAcceptedAltitudeMeters: Double? = null,
    internal val previousTrustworthySpeed: Double? = null,
) {

    /**
     * The average over the whole trip, from distance and duration.
     *
     * Not a running mean of instantaneous speeds — that would weight a minute spent stationary the
     * same as a minute at speed, and answer a question nobody asked.
     */
    val averageSpeedMetersPerSecond: Double?
        get() = if (durationSeconds > 0.0) distanceMeters / durationSeconds else null

    fun toStats() = TripStats(
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        averageSpeedMetersPerSecond = averageSpeedMetersPerSecond,
        maxSpeedMetersPerSecond = maxSpeedMetersPerSecond,
        elevationGainMeters = elevationGainMeters,
        minAltitudeMeters = minAltitudeMeters,
        maxAltitudeMeters = maxAltitudeMeters,
    )
}

data class TripStats(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val averageSpeedMetersPerSecond: Double?,
    val maxSpeedMetersPerSecond: Double?,
    val elevationGainMeters: Double,
    val minAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
) {
    val hasMeasurement: Boolean get() = durationSeconds > 0.0

    companion object {
        val EMPTY = TripStats(0.0, 0.0, null, null, 0.0, null, null)
    }
}

/**
 * Folds one fix into the trip.
 *
 * ## Distance is the integral of speed, not a sum of position steps
 *
 * The obvious implementation adds the distance between consecutive fixes. It is wrong in both
 * directions at once, which is what makes it worth spelling out — the first version of this file did
 * exactly that and its tests caught it:
 *
 *  - **A parked phone travels.** Its position wanders by metres every second, and summing those steps
 *    accumulated 1.7 km over five stationary minutes. Rejecting steps smaller than the position error
 *    does not fix it: random excursions past any threshold still happen, and every one of them is
 *    added and never subtracted, so the total only ever ratchets upward.
 *  - **A walk disappears.** Someone at 1.4 m/s covers 1.4 m between fixes while their position is
 *    accurate to 5 m, so *every* real step is smaller than the noise and the same threshold discards
 *    the entire journey.
 *
 * There is no threshold that separates those two cases, because they overlap. What separates them is
 * a different measurement entirely: the Doppler speed, which is accurate to about 0.1 m/s and already
 * reads exactly zero when stationary. Integrating it sidesteps position noise completely — and it is
 * consistent, since that same speed is what the screen shows.
 *
 * ## Every accumulator is gated on movement
 *
 * Distance, elevation gain and the maximum all take the already-gated speed as their signal that
 * anything is happening at all. A device that is not moving cannot travel, cannot climb, and cannot
 * set a record — and asking "is it moving" once, well, beats asking it three times badly.
 *
 * All three deliberately under-report rather than over-report. A trip that reads slightly short is a
 * small disappointment; one that invents a kilometre is a broken tool.
 */
class AccumulateTripUseCase @Inject constructor() {

    /**
     * @param resolvedSpeedMetersPerSecond the already-gated speed from [ResolveSpeedUseCase], which
     *   is exactly zero when the receiver's own uncertainty says the device is not moving.
     * @param mslAltitudeMeters height above sea level, after the geoid correction.
     */
    operator fun invoke(
        state: TripState,
        fix: GnssFix,
        resolvedSpeedMetersPerSecond: Double,
        mslAltitudeMeters: Double?,
    ): TripState {
        val previous = state.lastFix
            ?: return state.copy(
                lastFix = fix,
                lastAcceptedAltitudeMeters = mslAltitudeMeters,
                minAltitudeMeters = mslAltitudeMeters,
                maxAltitudeMeters = mslAltitudeMeters,
            )

        val elapsedSeconds =
            (fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
        if (elapsedSeconds <= 0.0 || elapsedSeconds > MAX_GAP_SECONDS) {
            // A long gap means the screen was away or the signal was lost. Time that was not measured
            // is not counted, and the two positions either side of it are not a journey.
            return state.copy(lastFix = fix)
        }

        val moving = resolvedSpeedMetersPerSecond > 0.0

        return state
            .copy(
                durationSeconds = state.durationSeconds + elapsedSeconds,
                distanceMeters = state.distanceMeters + resolvedSpeedMetersPerSecond * elapsedSeconds,
                lastFix = fix,
            )
            .withMaxSpeed(resolvedSpeedMetersPerSecond, fix)
            .withAltitude(fix, mslAltitudeMeters, moving)
    }

    /**
     * Raises the maximum, but only for a speed the receiver stands behind and that lasted.
     *
     * The candidate is the **lower of this fix and the one before it** — which is what "sustained
     * across two fixes" means arithmetically. A single 70 m/s glitch between 8 m/s fixes produces
     * candidates of `min(8, 70)` and `min(70, 8)`, both 8, so it can never raise the maximum; a real
     * acceleration to 15 m/s produces `min(15, 15)` as soon as it holds.
     *
     * The accuracy bar alone would not do this: a reacquisition glitch can arrive with a
     * confident-looking accuracy attached. The cost is shaving a genuinely instantaneous peak, which
     * for a number that is recorded permanently is the safe direction.
     */
    private fun TripState.withMaxSpeed(speedMetersPerSecond: Double, fix: GnssFix): TripState {
        val accuracy = fix.speedAccuracyMetersPerSecond
        val trustworthy = speedMetersPerSecond > 0.0 &&
            (accuracy == null || accuracy <= MAX_SPEED_ACCURACY_METERS_PER_SECOND)

        if (!trustworthy) return copy(previousTrustworthySpeed = null)

        val previousSpeed = previousTrustworthySpeed
            ?: return copy(previousTrustworthySpeed = speedMetersPerSecond)

        val sustained = min(previousSpeed, speedMetersPerSecond)

        return copy(
            maxSpeedMetersPerSecond = max(maxSpeedMetersPerSecond ?: 0.0, sustained),
            previousTrustworthySpeed = speedMetersPerSecond,
        )
    }

    /**
     * Tracks the altitude range, and accumulates climb only while actually moving.
     *
     * Two gates, and both are needed. The movement gate is what stops a phone on a table climbing
     * 1.4 km in ten minutes — GNSS vertical error wanders continuously, and a threshold alone ratchets
     * upward on every excursion past it exactly as the distance sum did. The noise threshold is what
     * stops a genuinely moving device from adding vertical drift on flat ground.
     *
     * The comparison is against the last altitude that was *accepted*, not the last one seen —
     * otherwise a slow drift adds a little on every fix and never trips the threshold, which is the
     * quiet version of the same bug.
     */
    private fun TripState.withAltitude(
        fix: GnssFix,
        altitudeMeters: Double?,
        moving: Boolean,
    ): TripState {
        if (altitudeMeters == null) return this

        val updated = copy(
            minAltitudeMeters = min(minAltitudeMeters ?: altitudeMeters, altitudeMeters),
            maxAltitudeMeters = max(maxAltitudeMeters ?: altitudeMeters, altitudeMeters),
        )

        if (!moving) return updated

        val anchor = lastAcceptedAltitudeMeters
            ?: return updated.copy(lastAcceptedAltitudeMeters = altitudeMeters)

        val noise = (fix.verticalAccuracyMeters ?: FALLBACK_VERTICAL_NOISE_METERS)
            .coerceAtLeast(MIN_ELEVATION_STEP_METERS)
        val change = altitudeMeters - anchor
        if (abs(change) < noise) return updated

        return updated.copy(
            elevationGainMeters = elevationGainMeters + if (change > 0) change else 0.0,
            lastAcceptedAltitudeMeters = altitudeMeters,
        )
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Beyond this the fixes either side are not a continuous journey. */
        const val MAX_GAP_SECONDS = 30.0

        /** Vertical error is typically 1.5 to 3 times horizontal, so the fallback is generous. */
        const val FALLBACK_VERTICAL_NOISE_METERS = 20.0

        /**
         * A floor under the elevation gate, in case a receiver reports an optimistic vertical
         * accuracy. Without it an over-confident driver turns gentle drift back into phantom climb.
         */
        const val MIN_ELEVATION_STEP_METERS = 3.0

        /** A speed whose own error bar is wider than this is not evidence of a record. */
        const val MAX_SPEED_ACCURACY_METERS_PER_SECOND = 2.0
    }
}
