package com.minion.scaffold.feature.speedometer.presentation

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.gnss.geoid.GeoidModel
import com.minion.scaffold.core.gnss.model.CoordinateFormatter
import com.minion.scaffold.core.gnss.model.GnssFix
import com.minion.scaffold.core.gnss.usecase.AccumulateTripUseCase
import com.minion.scaffold.core.gnss.usecase.ClassifyFixQualityUseCase
import com.minion.scaffold.core.gnss.usecase.ResolveSpeedUseCase
import com.minion.scaffold.core.gnss.usecase.SpeedState
import com.minion.scaffold.core.gnss.usecase.TripState
import com.minion.scaffold.core.gnss.usecase.TripStats
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.speedometer.domain.LocationEvent
import com.minion.scaffold.feature.speedometer.domain.LocationSource
import com.minion.scaffold.feature.speedometer.domain.ObserveSpeedometerPreferencesUseCase
import com.minion.scaffold.feature.speedometer.domain.RateOfClimbSource
import com.minion.scaffold.feature.speedometer.domain.SatelliteStatusSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Folds the fix stream through `:core:gnss` and hands the result to the screen.
 *
 * ```
 * fix → speed gate → display
 *    └→ geoid → altitude → trip accumulators
 * ```
 *
 * Everything of substance is a pure function; what happens here is threading accumulated state from
 * one fix to the next, which is exactly the shape those functions were written for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SpeedometerViewModel @Inject constructor(
    private val locationSource: LocationSource,
    private val satelliteStatusSource: SatelliteStatusSource,
    private val rateOfClimbSource: RateOfClimbSource,
    private val geoid: GeoidModel,
    private val resolveSpeed: ResolveSpeedUseCase,
    private val accumulateTrip: AccumulateTripUseCase,
    private val classifyFixQuality: ClassifyFixQualityUseCase,
    observePreferences: ObserveSpeedometerPreferencesUseCase,
) : MviViewModel<SpeedometerState, SpeedometerIntent, SpeedometerEffect>(SpeedometerState()) {

    /** Whether the screen is on show. The receiver follows this, not the ViewModel's lifetime. */
    private val screenVisible = MutableStateFlow(false)

    /**
     * Whether precise location has been granted.
     *
     * Combined with visibility rather than letting the source be started and fail. Approximate
     * location produces fixes that are re-coarsened per request — a grid, not a track — so opening
     * the receiver on that grant would feed the whole pipeline positions that jump about and produce
     * speeds that are not merely imprecise but meaningless.
     */
    private val preciseGranted = MutableStateFlow(false)

    private var speedState = SpeedState()
    private var trip = TripState()

    init {
        combine(
            observePreferences.speedUnit,
            observePreferences.distanceUnit,
            observePreferences.coordinateFormat,
        ) { speed, distance, coordinates -> Triple(speed, distance, coordinates) }
            .onEach { (speed, distance, coordinates) ->
                reduce {
                    copy(speedUnit = speed, distanceUnit = distance, coordinateFormat = coordinates)
                }
            }
            .launchIn(viewModelScope)

        val active = combine(screenVisible, preciseGranted) { visible, granted -> visible && granted }
            .distinctUntilChanged()

        active
            .flatMapLatest { on -> if (on) locationSource.fixes() else emptyFlow() }
            .onEach(::onLocationEvent)
            .launchIn(viewModelScope)

        active
            .flatMapLatest { on -> if (on) satelliteStatusSource.status() else emptyFlow() }
            .onEach { status -> reduce { copy(satellites = status) } }
            .launchIn(viewModelScope)

        active
            .flatMapLatest { on -> if (on) rateOfClimbSource.ratePerMinute() else emptyFlow() }
            .onEach { rate -> reduce { copy(rateOfClimbMetersPerMinute = rate) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SpeedometerIntent) {
        when (intent) {
            SpeedometerIntent.ScreenResumed -> screenVisible.value = true

            SpeedometerIntent.ScreenPaused -> {
                screenVisible.value = false
                // Dropped so the next visit starts from a fresh fix rather than differentiating
                // against one that may be minutes old.
                speedState = SpeedState()
                reduce {
                    copy(
                        reading = SpeedometerState.Reading.Searching,
                        satellites = com.minion.scaffold.feature.speedometer.domain
                            .SatelliteStatus.NONE,
                    )
                }
            }

            is SpeedometerIntent.PermissionResult -> {
                val access = resolveAccess(intent)
                preciseGranted.value = access == SpeedometerState.LocationAccess.Precise
                reduce { copy(access = access) }
            }

            SpeedometerIntent.AppSettingsRequested -> emit(SpeedometerEffect.OpenAppSettings)

            SpeedometerIntent.LocationSettingsRequested ->
                emit(SpeedometerEffect.OpenLocationSettings)

            SpeedometerIntent.StartPressed -> {
                trip = TripState()
                reduce { copy(measuring = true, trip = TripStats.EMPTY) }
            }

            SpeedometerIntent.StopPressed -> {
                reduce { copy(measuring = false) }
                if (!currentState.trip.hasMeasurement) {
                    emit(SpeedometerEffect.Notice(SpeedometerNotice.NothingRecorded))
                }
            }

            SpeedometerIntent.ResetPressed -> {
                trip = TripState()
                reduce { copy(trip = TripStats.EMPTY) }
                emit(SpeedometerEffect.Notice(SpeedometerNotice.TripReset))
            }

            SpeedometerIntent.CopyCoordinatesRequested -> {
                val live = currentState.reading as? SpeedometerState.Reading.Live ?: return
                emit(
                    SpeedometerEffect.Copy(
                        CoordinateFormatter.format(
                            live.latitude,
                            live.longitude,
                            currentState.coordinateFormat,
                        ),
                    ),
                )
            }

            SpeedometerIntent.OpenInMapsRequested -> {
                val live = currentState.reading as? SpeedometerState.Reading.Live ?: return
                emit(SpeedometerEffect.OpenInMaps(live.latitude, live.longitude))
            }
        }
    }

    /**
     * Three grants, not two.
     *
     * Coarse-without-fine is the case that needs naming: it is a deliberate user choice made in the
     * system dialog, and treating it as a denial tells someone who granted something that they
     * granted nothing.
     */
    private fun resolveAccess(result: SpeedometerIntent.PermissionResult) = when {
        result.fineGranted -> SpeedometerState.LocationAccess.Precise
        result.coarseGranted -> SpeedometerState.LocationAccess.Approximate
        result.shouldShowRationale -> SpeedometerState.LocationAccess.Denied
        else -> SpeedometerState.LocationAccess.PermanentlyDenied
    }

    private fun onLocationEvent(event: LocationEvent) {
        when (event) {
            is LocationEvent.Fix -> onFix(event.fix)
            LocationEvent.ProviderDisabled -> reduce {
                copy(providerEnabled = false, reading = SpeedometerState.Reading.Searching)
            }

            LocationEvent.ProviderEnabled -> reduce { copy(providerEnabled = true) }
        }
    }

    private fun onFix(fix: GnssFix) {
        val (nextSpeedState, speed) = resolveSpeed(speedState, fix)
        speedState = nextSpeedState

        // The geoid correction, applied once, here. Everything downstream — the readout, the trip
        // minimum and maximum, the elevation gain — sees a height above sea level and never the raw
        // ellipsoidal figure.
        val altitude = fix.ellipsoidalAltitudeMeters?.let {
            geoid.mslAltitudeMeters(it, fix.latitude, fix.longitude)
        }

        if (currentState.measuring) {
            trip = accumulateTrip(trip, fix, speed.metersPerSecond, altitude)
        }

        val measuring = currentState.measuring
        val stats = if (measuring) trip.toStats() else currentState.trip

        reduce {
            copy(
                providerEnabled = true,
                reading = SpeedometerState.Reading.Live(
                    speedMetersPerSecond = speed.metersPerSecond,
                    speedDerived = speed.derived,
                    altitudeMeters = altitude,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                ),
                fixQuality = classifyFixQuality(fix),
                mocked = fix.fromMockProvider,
                trip = stats,
            )
        }
    }

    private fun emit(effect: SpeedometerEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }
}
