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
    /** How much location access there is. */
    val access: LocationAccess = LocationAccess.Unknown,

    /** Whether GPS is switched on at the system level — a different problem from permission. */
    val providerEnabled: Boolean = true,

    /** What the receiver has given us — searching or a live fix. */
    val reading: Reading = Reading.Searching,

    /** What the receiver can currently see, for the cold-start diagnosis. */
    val satellites: SatelliteStatus = SatelliteStatus.NONE,

    /** How much to trust the current fix. */
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

    /** Whether a trip is accumulating. */
    val measuring: Boolean = false,
    /** The running trip statistics. */
    val trip: TripStats = TripStats.EMPTY,

    /** The unit the speed is shown in. */
    val speedUnit: SpeedUnit = SpeedUnit.KilometersPerHour,
    /** The unit altitude and distance are shown in. */
    val distanceUnit: DistanceUnit = DistanceUnit.Metric,
    /** The format coordinates are shown in. */
    val coordinateFormat: CoordinateFormat = CoordinateFormat.Decimal,
) : UiState {

    /** Whether there is anything worth showing a number for. */
    val hasFix: Boolean get() = reading is Reading.Live

    /** Whether a trip can be started — precise access and the provider enabled. */
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

        /**
         * A live fix.
         *
         * @property speedMetersPerSecond The speed to display, in m/s.
         * @property speedDerived         True when the speed was computed from position deltas rather
         *   than measured — a materially weaker number, so the screen says so.
         * @property altitudeMeters       Height above mean sea level, after the geoid correction, or
         *   `null` if unavailable.
         * @property latitude             Latitude in decimal degrees.
         * @property longitude            Longitude in decimal degrees.
         */
        data class Live(
            val speedMetersPerSecond: Double,
            val speedDerived: Boolean,
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
    enum class LocationAccess {

        /** Not yet asked in this session. */
        Unknown,

        /** Refused, but the system will still show the dialog. */
        Denied,

        /** Refused to the point where only Settings can grant it. */
        PermanentlyDenied,

        /** Coarse location only — meaningless for speed; offers a precise-upgrade path. */
        Approximate,

        /** Precise location — the receiver can be opened. */
        Precise,
    }
}

/**
 * Everything on the screen that is *not* the live speed.
 *
 * The readout wants every fix; the gate, the panels and the trip figures do not. Pulling them into
 * one slice lets the rest of the screen sit behind a single `derivedStateOf` and recompose only when
 * something in it actually changed.
 *
 * @property access                     How much location access there is.
 * @property providerEnabled            Whether GPS is switched on at the system level.
 * @property satellites                 What the receiver can currently see.
 * @property fixQuality                 How much to trust the current fix.
 * @property rateOfClimbMetersPerMinute Barometric rate of climb, rounded, or `null` without a sensor.
 * @property mocked                     Whether fixes come from a mock provider.
 * @property measuring                  Whether a trip is accumulating.
 * @property canMeasure                 Whether a trip can be started.
 * @property trip                       The running trip statistics.
 * @property speedUnit                  The unit the speed is shown in.
 * @property distanceUnit               The unit altitude and distance are shown in.
 * @property coordinateFormat           The format coordinates are shown in.
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

/**
 * Projects the full state onto the [SpeedometerChrome] slice, rounding the rate of climb.
 *
 * @receiver The full speedometer state.
 * @return The chrome slice for the non-readout parts of the screen.
 */
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

/** Everything the user (or the system) can do on the speedometer screen. */
internal sealed interface SpeedometerIntent : UiIntent {

    /**
     * The screen became visible, or stopped being visible.
     *
     * Drives the receiver. The ViewModel outlives the screen, so collecting in `init` would leave
     * GNSS at full power in a pocket — the same shape as the level's sensor and the sound meter's
     * microphone, and at 1 Hz a real battery cost.
     */
    data object ScreenResumed : SpeedometerIntent

    /** The screen stopped being visible. */
    data object ScreenPaused : SpeedometerIntent

    /**
     * The location permission request returned.
     *
     * @property fineGranted         Whether precise location was granted.
     * @property coarseGranted       Whether approximate location was granted.
     * @property shouldShowRationale The system's rationale flag.
     */
    data class PermissionResult(
        val fineGranted: Boolean,
        val coarseGranted: Boolean,
        val shouldShowRationale: Boolean,
    ) : SpeedometerIntent

    /** Open the app's system settings, to grant a permanently denied permission. */
    data object AppSettingsRequested : SpeedometerIntent

    /** Open the system location settings, to switch GPS back on. */
    data object LocationSettingsRequested : SpeedometerIntent

    /** Start a trip. */
    data object StartPressed : SpeedometerIntent

    /** Stop the trip. */
    data object StopPressed : SpeedometerIntent

    /** Reset the trip statistics. */
    data object ResetPressed : SpeedometerIntent

    /** Copy the current coordinates. */
    data object CopyCoordinatesRequested : SpeedometerIntent

    /** Open the current coordinates in a map app. */
    data object OpenInMapsRequested : SpeedometerIntent
}

/** One-shot events from the speedometer screen. */
internal sealed interface SpeedometerEffect : UiEffect {

    /** Open the app's system settings. */
    data object OpenAppSettings : SpeedometerEffect

    /** Open the system location settings. */
    data object OpenLocationSettings : SpeedometerEffect

    /**
     * Put text on the clipboard.
     *
     * @property text The text to copy.
     */
    data class Copy(val text: String) : SpeedometerEffect

    /**
     * A `geo:` intent — the only thing in this feature that leaves the device.
     *
     * @property latitude  The latitude to open.
     * @property longitude The longitude to open.
     */
    data class OpenInMaps(val latitude: Double, val longitude: Double) : SpeedometerEffect

    /**
     * Show a transient message.
     *
     * @property notice What to tell the user.
     */
    data class Notice(val notice: SpeedometerNotice) : SpeedometerEffect
}

/** The transient messages the speedometer shows as a snackbar. */
internal enum class SpeedometerNotice {

    /** The coordinates were copied. */
    Copied,

    /** No app could handle the `geo:` intent. */
    NoMapApp,

    /** The trip statistics were reset. */
    TripReset,

    /** Stopped with nothing recorded — no movement was measured. */
    NothingRecorded,
}
