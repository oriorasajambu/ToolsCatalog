package com.minion.scaffold.core.gnss.usecase

import com.minion.scaffold.core.gnss.model.GnssFix
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Accumulated state, threaded by the caller so nothing here holds it.
 *
 * [movingStreak] and [stillStreak] are the dwell: a single fix on either side of the threshold does
 * not flip the readout, the same shape as the level's pose machine and the sound meter's clipping
 * detector. Without it a phone at the kerb flickers between 0 and 2 km/h once a second.
 *
 * @property previous     The previous fix, for the derivation fallback.
 * @property movingStreak Consecutive fixes that looked moving.
 * @property stillStreak  Consecutive fixes that looked still.
 * @property moving       The committed moving/still verdict, after the dwell.
 */
data class SpeedState(
    val previous: GnssFix? = null,
    val movingStreak: Int = 0,
    val stillStreak: Int = 0,
    val moving: Boolean = false,
)

/**
 * What the speedometer shows, and where the number came from.
 *
 * [derived] is surfaced because it is a materially weaker measurement, and the screen says so rather
 * than presenting the two identically.
 *
 * @property metersPerSecond         The speed to display, in m/s; zero when the device is not moving.
 * @property accuracyMetersPerSecond The speed's 1σ error in m/s, or `null` when unknown.
 * @property derived                 Whether the speed was computed here rather than reported by the
 *   receiver — the weaker path.
 */
data class ResolvedSpeed(
    val metersPerSecond: Double,
    val accuracyMetersPerSecond: Double?,
    val derived: Boolean,
)

/**
 * Decides the speed to display.
 *
 * ## Doppler, not distance over time
 *
 * `Location.getSpeed()` on a modern chip is derived from the **Doppler shift of the satellite
 * carrier** — a direct measurement of velocity along the line of sight to each satellite, resolved
 * into a ground speed. It is typically accurate to about 0.1 m/s and is available before the position
 * solution has settled.
 *
 * Differentiating positions instead is the obvious approach and quietly terrible. Two fixes a second
 * apart, each good to ±5 m, can differ by 10 m through noise alone — 36 km/h of phantom speed while
 * standing still. **Every metre of position error lands directly in the number**, multiplied by the
 * sample rate.
 *
 * So a reported speed is used verbatim and never recomputed. Derivation happens only where
 * `hasSpeed()` was false, and says so.
 *
 * ## Zero is decided by the receiver, not by a constant
 *
 * A stationary receiver does not report standing still: the solution wanders and the Doppler estimate
 * has its own floor. Some threshold is needed, and a fixed one is wrong in both directions — it still
 * creeps on a bad fix and it erases genuine slow walking.
 *
 * The receiver already publishes what it thinks its own speed error is. **A speed smaller than its
 * own uncertainty is not a measurement of motion**, so that is the test. It tightens automatically
 * when the fix is good and widens when it is poor, with nothing to tune per device.
 *
 * Where no accuracy is reported — API 25 and below, or a driver that omits it — there is nothing to
 * compare against and a conservative fixed floor is the only option left. That path is named rather
 * than pretended away.
 */
class ResolveSpeedUseCase @Inject constructor() {

    /**
     * Folds one fix into the speed state and resolves the speed to show.
     *
     * @param state The accumulated state from the previous call.
     * @param fix   The current fix.
     * @return The updated state paired with the resolved speed (zeroed while not moving).
     */
    operator fun invoke(state: SpeedState, fix: GnssFix): Pair<SpeedState, ResolvedSpeed> {
        val measured = fix.speedMetersPerSecond
        val raw = when {
            measured != null -> ResolvedSpeed(measured, fix.speedAccuracyMetersPerSecond, derived = false)
            else -> derive(state.previous, fix)
        }

        val threshold = raw.accuracyMetersPerSecond ?: FALLBACK_FLOOR_METERS_PER_SECOND
        val looksMoving = raw.metersPerSecond > threshold

        val movingStreak = if (looksMoving) state.movingStreak + 1 else 0
        val stillStreak = if (looksMoving) 0 else state.stillStreak + 1

        val moving = when {
            movingStreak >= FIXES_TO_START -> true
            stillStreak >= FIXES_TO_STOP -> false
            else -> state.moving
        }

        return SpeedState(
            previous = fix,
            movingStreak = movingStreak,
            stillStreak = stillStreak,
            moving = moving,
        ) to raw.copy(metersPerSecond = if (moving) raw.metersPerSecond else 0.0)
    }

    /**
     * Distance over time, for the rare receiver that reports no speed.
     *
     * Great-circle distance between the two positions, divided by the elapsed monotonic time. Every
     * caveat in the class note applies: this is the weak path, and callers show it as such.
     */
    private fun derive(previous: GnssFix?, fix: GnssFix): ResolvedSpeed {
        if (previous == null) return ResolvedSpeed(0.0, null, derived = true)

        val elapsedSeconds = (fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) /
            NANOS_PER_SECOND
        if (elapsedSeconds <= 0.0 || elapsedSeconds > MAX_DERIVATION_GAP_SECONDS) {
            return ResolvedSpeed(0.0, null, derived = true)
        }

        val meters = haversineMeters(
            previous.latitude, previous.longitude,
            fix.latitude, fix.longitude,
        )

        // The uncertainty of a derived speed is the two position errors over the interval. Summed
        // rather than combined in quadrature: quadrature is the right statistics for independent
        // errors, and these are neither independent nor the place to be clever. This path only runs
        // on a receiver that reports no velocity at all, so it is already the weak one — and its
        // characteristic failure is phantom motion from a stationary phone. A conservative bound
        // means the fallback reports driving and stays quiet about walking, which is the right way
        // round for a number someone might act on.
        val accuracy = listOfNotNull(previous.horizontalAccuracyMeters, fix.horizontalAccuracyMeters)
            .takeIf { it.size == 2 }
            ?.let { (a, b) -> (a + b) / elapsedSeconds }

        return ResolvedSpeed(meters / elapsedSeconds, accuracy, derived = true)
    }

    private companion object {

        /**
         * How many consecutive fixes must agree before the readout starts or stops.
         *
         * Asymmetric on purpose. Two fixes to start moving keeps a single glitch off the screen;
         * three to stop means a brief signal dip while genuinely driving does not blink to zero.
         */
        const val FIXES_TO_START = 2
        const val FIXES_TO_STOP = 3

        /**
         * Used only where the receiver reports no speed accuracy at all.
         *
         * About 1.8 km/h — below a walking pace, above the drift of a stationary consumer fix. An
         * invented constant, which is why it is confined to the path where there is genuinely nothing
         * better to compare against.
         */
        const val FALLBACK_FLOOR_METERS_PER_SECOND = 0.5

        const val NANOS_PER_SECOND = 1_000_000_000.0

        /** Past this gap the previous fix is too stale to differentiate against. */
        const val MAX_DERIVATION_GAP_SECONDS = 10.0
    }
}

/**
 * Great-circle distance in metres.
 *
 * The haversine formula on a spherical Earth. Accurate to about 0.3% against the ellipsoid, which
 * over the tens of metres between consecutive fixes is a fraction of a millimetre — far below the
 * metres of position noise it is being applied to. Vincenty's method would be exact and would be
 * solving a problem this does not have.
 *
 * @param latitude1  First point's latitude in decimal degrees.
 * @param longitude1 First point's longitude in decimal degrees.
 * @param latitude2  Second point's latitude in decimal degrees.
 * @param longitude2 Second point's longitude in decimal degrees.
 * @return The great-circle distance between the two points, in metres.
 */
internal fun haversineMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val lat1 = Math.toRadians(latitude1)
    val lat2 = Math.toRadians(latitude2)
    val deltaLat = Math.toRadians(latitude2 - latitude1)
    val deltaLon = Math.toRadians(longitude2 - longitude1)

    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)

    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Mean Earth radius, IUGG. */
private const val EARTH_RADIUS_METERS = 6_371_008.8
