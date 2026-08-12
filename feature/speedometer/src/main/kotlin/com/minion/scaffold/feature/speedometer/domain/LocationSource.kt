package com.minion.scaffold.feature.speedometer.domain

import com.minion.scaffold.core.gnss.model.GnssFix
import kotlinx.coroutines.flow.Flow

/**
 * Where fixes come from.
 *
 * An interface so the ViewModel is testable: `LocationManager` does not exist in a JVM unit test. The
 * same seam, and the same reason, as `:feature:level`'s `GravitySource` and `:feature:soundmeter`'s
 * `AudioSource`.
 */
internal interface LocationSource {

    /**
     * Fixes, for as long as this is collected.
     *
     * Cold. Updates are requested when collection starts and removed when it stops, so the receiver
     * is not powered while the screen is away — which at 1 Hz is a real battery cost rather than a
     * theoretical one.
     *
     * @return A cold [Flow] of location events that holds the receiver open only while collected.
     */
    fun fixes(): Flow<LocationEvent>
}

/** An event from the location receiver. */
internal sealed interface LocationEvent {

    /**
     * A new position fix arrived.
     *
     * @property fix The fix.
     */
    data class Fix(val fix: GnssFix) : LocationEvent

    /**
     * The GPS provider is switched off at the system level.
     *
     * Distinct from having no permission and from simply not having a fix yet: the recovery is a trip
     * to system settings, and telling someone to grant a permission they already granted is the kind
     * of dead end that gets an app uninstalled.
     */
    data object ProviderDisabled : LocationEvent

    /** The GPS provider was switched back on. */
    data object ProviderEnabled : LocationEvent
}

/**
 * What the receiver can see, while it is looking.
 *
 * The point of showing this is the cold start. Without a network there is no almanac to download, so
 * a first fix can take minutes and indoors may never come — and a blank wait gives no way to tell
 * whether to keep waiting or go outside. Satellites visible with none used means keep waiting; none
 * visible means go outside.
 *
 * It is also the most direct demonstration that the tool needs no network at all.
 */
internal data class SatelliteStatus(
    /** How many satellites are currently visible. */
    val visible: Int,
    /** How many of the visible satellites are used in the current fix. */
    val usedInFix: Int,
    /** Carrier-to-noise density for each visible satellite, dB-Hz. Roughly 20 (weak) to 50 (strong). */
    val signalStrengths: List<Float>,
    /** Which constellations are contributing. */
    val constellations: Set<Constellation>,
) {
    /** Whether any satellite is visible at all. */
    val hasAny: Boolean get() = visible > 0

    companion object {
        /** Nothing visible — the cold-start starting point. */
        val NONE = SatelliteStatus(0, 0, emptyList(), emptySet())
    }
}

/**
 * Which systems are contributing.
 *
 * Worth showing for an offline tool: a modern chip using four constellations is the reason a fix
 * arrives at all in a street canyon, and it is a fact about the hardware that no network supplied.
 */
internal enum class Constellation { Gps, Glonass, Galileo, BeiDou, Qzss, Irnss, Sbas, Unknown }

/** Where satellite visibility comes from. */
internal interface SatelliteStatusSource {
    /**
     * Satellite status, for as long as this is collected.
     *
     * @return A cold [Flow] of [SatelliteStatus] that registers the status callback while collected.
     */
    fun status(): Flow<SatelliteStatus>
}

/**
 * Rate of climb from the pressure sensor, in metres per minute.
 *
 * Shown **beside** the altitude rather than fused into it. A barometer is precise to tenths of a
 * metre for a *change* and has no idea of its absolute reference, which drifts about 8–10 m as a
 * weather front passes; GNSS is the reverse. Keeping them separate means neither number is quietly
 * wrong — the satellites give the height, the barometer gives the change.
 *
 * Absent on many devices, in which case the flow simply never emits and the row does not appear.
 */
internal interface RateOfClimbSource {
    /**
     * Rate of climb, for as long as this is collected.
     *
     * @return A cold [Flow] of metres per minute; never emits on a device without a barometer.
     */
    fun ratePerMinute(): Flow<Double>
}
