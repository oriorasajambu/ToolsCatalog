package com.minion.scaffold.feature.speedometer.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.feature.speedometer.domain.ObserveSpeedometerPreferencesUseCase
import com.minion.scaffold.feature.speedometer.domain.SetCoordinateFormatUseCase
import com.minion.scaffold.feature.speedometer.domain.SetDistanceUnitUseCase
import com.minion.scaffold.feature.speedometer.domain.SetSpeedUnitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SpeedometerSettingsState(
    val speedUnit: SpeedUnit = SpeedUnit.KilometersPerHour,
    val distanceUnit: DistanceUnit = DistanceUnit.Metric,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.Decimal,
)

/**
 * Not an `MviViewModel`: three selectors, nothing one-shot to emit.
 *
 * The MVI scaffolding exists to keep complex screens honest, and wrapping three enums in a State, an
 * Intent and an Effect would be ceremony rather than structure — the same call `:feature:soundmeter`
 * makes for its offset.
 */
@HiltViewModel
internal class SpeedometerSettingsViewModel @Inject constructor(
    observePreferences: ObserveSpeedometerPreferencesUseCase,
    private val setSpeedUnit: SetSpeedUnitUseCase,
    private val setDistanceUnit: SetDistanceUnitUseCase,
    private val setCoordinateFormat: SetCoordinateFormatUseCase,
) : ViewModel() {

    val state: StateFlow<SpeedometerSettingsState> = combine(
        observePreferences.speedUnit,
        observePreferences.distanceUnit,
        observePreferences.coordinateFormat,
    ) { speed, distance, coordinates ->
        SpeedometerSettingsState(speed, distance, coordinates)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SpeedometerSettingsState(),
    )

    fun onSpeedUnitChange(unit: SpeedUnit) {
        viewModelScope.launch { setSpeedUnit(unit) }
    }

    fun onDistanceUnitChange(unit: DistanceUnit) {
        viewModelScope.launch { setDistanceUnit(unit) }
    }

    fun onCoordinateFormatChange(format: CoordinateFormat) {
        viewModelScope.launch { setCoordinateFormat(format) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
