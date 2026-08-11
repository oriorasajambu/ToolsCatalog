package com.minion.scaffold.feature.speedometer.presentation

import androidx.compose.runtime.Immutable
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.FixQuality
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.core.gnss.usecase.TripStats
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatus

@Immutable
internal data class SpeedometerState(
    val access: LocationAccess = LocationAccess.Unknown,

    /** Whether GPS is switched on at the system level — a different problem from permission. */
    val providerEnabled: Boolean = true,

    val reading: Reading = Reading.Searching,

    val satellites: SatelliteStatus = SatelliteStatus.NONE,

    val fixQuality: FixQuality = FixQuality.None,

    /** Metres per minute, when a pressure sensor is present. Null on devices without one. */
    val rateOfClimbMetersPerMinute: Double? = null,

    /**
     * Set when the fixes are being supplied by a mock provider.
     *
     * The readout stays live — someone may be testing deliberately — but a fabricated 120 km/h must
     * never be indistinguishable from a real one, including in a screenshot.
     */
    val mocked: Boolean = false,

    val measuring: Boolean = false,
    val trip: TripStats = TripStats.EMPTY,

    val speedUnit: SpeedUnit = SpeedUnit.KilometersPerHour,
    val distanceUnit: DistanceUnit = DistanceUnit.Metric,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.Decimal,
) : UiState {

    /** Whether there is anything worth showing a number for. */
    val hasFix: Boolean get() = reading is Reading.Live

    val canMeasure: Boolean get() = access == LocationAccess.Precise && providerEnabled

    /**
     * What the receiver has given us.
     *
     * A sealed type rather than nullable fields, because "searching" and "stationary" are completely
     * different situations that a null speed would flatten into one — and a speedometer showing a
     * confident 0 while it has no fix at all is a lie of exactly the kind this app's other tools are
     * built to avoid.
     */
    @Immutable
    sealed interface Reading {

        /** No fix yet. The satellite view is what the screen shows instead. */
        data object Searching : Reading

        data class Live(
            val speedMetersPerSecond: Double,

            /**
             * True when the speed was computed from position deltas rather than measured.
             *
             * A materially weaker number — it carries the position noise the Doppler path does not —
             * so the screen says so rather than presenting the two identically.
             */
            val speedDerived: Boolean,

            /** Height above mean sea level, after the geoid correction. Null if unavailable. */
            val altitudeMeters: Double?,

            val latitude: Double,
            val longitude: Double,
        ) : Reading
    }

    /**
     * How much location access there is, which is three states rather than two.
     *
     * [Approximate] is the subtle one. Since Android 12 the system dialog offers Precise or
     * Approximate and the user chooses; approximate is coarsened to roughly a city block and
     * re-coarsened per request, so consecutive fixes jump around a grid. A speed derived from those is
     * not merely imprecise, it is meaningless — and it is the user's own deliberate choice rather
     * than a bug, so it earns an explanation and an upgrade path rather than an error.
     */
    enum class LocationAccess { Unknown, Denied, PermanentlyDenied, Approximate, Precise }
}

/**
 * Everything on the screen that is *not* the live speed.
 *
 * The readout wants every fix; the gate, the panels and the trip figures do not. Pulling them into
 * one slice lets the rest of the screen sit behind a single `derivedStateOf` and recompose only when
 * something in it actually changed.
 */
@Immutable
internal data class SpeedometerChrome(
    val access: SpeedometerState.LocationAccess,
    val providerEnabled: Boolean,
    val satellites: SatelliteStatus,
    val fixQuality: FixQuality,
    val rateOfClimbMetersPerMinute: Double?,
    val mocked: Boolean,
    val measuring: Boolean,
    val canMeasure: Boolean,
    val trip: TripStats,
    val speedUnit: SpeedUnit,
    val distanceUnit: DistanceUnit,
    val coordinateFormat: CoordinateFormat,
)

internal fun SpeedometerState.toChrome() = SpeedometerChrome(
    access = access,
    providerEnabled = providerEnabled,
    satellites = satellites,
    fixQuality = fixQuality,
    // Rounded, because a rate of climb that changes in the tenths is noise being rendered.
    rateOfClimbMetersPerMinute = rateOfClimbMetersPerMinute?.let { kotlin.math.round(it) },
    mocked = mocked,
    measuring = measuring,
    canMeasure = canMeasure,
    trip = trip,
    speedUnit = speedUnit,
    distanceUnit = distanceUnit,
    coordinateFormat = coordinateFormat,
)

internal sealed interface SpeedometerIntent : UiIntent {

    /**
     * The screen became visible, or stopped being visible.
     *
     * Drives the receiver. The ViewModel outlives the screen, so collecting in `init` would leave
     * GNSS at full power in a pocket — the same shape as the level's sensor and the sound meter's
     * microphone, and at 1 Hz a real battery cost.
     */
    data object ScreenResumed : SpeedometerIntent

    data object ScreenPaused : SpeedometerIntent

    data class PermissionResult(
        val fineGranted: Boolean,
        val coarseGranted: Boolean,
        val shouldShowRationale: Boolean,
    ) : SpeedometerIntent

    data object AppSettingsRequested : SpeedometerIntent

    data object LocationSettingsRequested : SpeedometerIntent

    data object StartPressed : SpeedometerIntent

    data object StopPressed : SpeedometerIntent

    data object ResetPressed : SpeedometerIntent

    data object CopyCoordinatesRequested : SpeedometerIntent

    data object OpenInMapsRequested : SpeedometerIntent
}

internal sealed interface SpeedometerEffect : UiEffect {

    data object OpenAppSettings : SpeedometerEffect

    data object OpenLocationSettings : SpeedometerEffect

    data class Copy(val text: String) : SpeedometerEffect

    /** A `geo:` intent — the only thing in this feature that leaves the device. */
    data class OpenInMaps(val latitude: Double, val longitude: Double) : SpeedometerEffect

    data class Notice(val notice: SpeedometerNotice) : SpeedometerEffect
}

internal enum class SpeedometerNotice {
    Copied,
    NoMapApp,
    TripReset,
    NothingRecorded,
}
