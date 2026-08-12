package com.minion.scaffold.core.gnss.model

/**
 * One position fix, in SI units, with the receiver's own error bars.
 *
 * Every accuracy field is nullable because they are genuinely optional — `getSpeedAccuracy` arrived
 * in API 26 and not every driver populates it. Nullable rather than defaulted to some large number,
 * because "the receiver did not say" and "the receiver said it is bad" call for different behaviour
 * and a sentinel would erase the distinction.
 */
data class GnssFix(
    /** Latitude in decimal degrees. */
    val latitude: Double,
    /** Longitude in decimal degrees. */
    val longitude: Double,

    /**
     * Height above the WGS-84 ellipsoid, as the receiver reports it.
     *
     * **Not height above sea level.** Named to make that impossible to forget at a call site — see
     * [com.minion.scaffold.core.gnss.geoid.GeoidModel] for what has to happen before this is shown to
     * anyone.
     */
    val ellipsoidalAltitudeMeters: Double?,

    /**
     * Ground speed, as measured by the receiver.
     *
     * On a modern chip this is derived from the Doppler shift of the carrier — a direct measurement
     * of velocity, typically good to about 0.1 m/s, and available before the position has settled.
     * `null` where `Location.hasSpeed()` was false, which is the only case in which this app computes
     * a speed itself.
     */
    val speedMetersPerSecond: Double?,

    /** The receiver's 1σ speed error in m/s, or `null` when it did not report one. */
    val speedAccuracyMetersPerSecond: Double?,
    /** The receiver's horizontal position error in metres, or `null` when unreported. */
    val horizontalAccuracyMeters: Double?,
    /** The receiver's vertical position error in metres, or `null` when unreported. */
    val verticalAccuracyMeters: Double?,

    /**
     * The device's monotonic clock at the moment of the fix.
     *
     * Elapsed realtime rather than wall time: the trip accumulators divide by intervals between
     * fixes, and a wall clock that steps — a network time sync, a timezone change, a user setting the
     * date — would produce a negative or enormous interval and a correspondingly absurd speed.
     */
    val elapsedRealtimeNanos: Long,

    /**
     * Whether the fix came from a mock provider.
     *
     * Carried through rather than filtered out. Someone may be testing deliberately, so the readout
     * stays live — but a fabricated 120 km/h must never be indistinguishable from a real one,
     * including in a screenshot, so the UI marks it.
     */
    val fromMockProvider: Boolean,
)

/**
 * How much to trust what is on screen, in one word.
 *
 * Derived from the reported accuracies rather than from satellite count, which is a proxy: eight
 * satellites in a street canyon can be worse than five in the open, and the receiver already knows
 * which it is.
 */
enum class FixQuality {

    /** No fix at all, or one with no accuracy figures to judge it by. */
    None,

    /** Usable for a rough position, not for a speed anyone should act on. */
    Poor,

    /** Workable for position and speed, visibly noisy for altitude. */
    Usable,

    /** A clear-sky fix; every reading is trustworthy. */
    Good,
}
