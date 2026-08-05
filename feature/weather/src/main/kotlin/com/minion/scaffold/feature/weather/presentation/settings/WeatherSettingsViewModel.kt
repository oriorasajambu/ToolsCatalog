package com.minion.scaffold.feature.weather.presentation.settings

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import com.minion.scaffold.feature.weather.domain.SetWeatherUnitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WeatherSettingsViewModel @Inject constructor(
    observeWeatherUnit: ObserveWeatherUnitUseCase,
    private val setWeatherUnit: SetWeatherUnitUseCase,
) : MviViewModel<WeatherSettingsState, WeatherSettingsIntent, WeatherSettingsEffect>(WeatherSettingsState()) {

    init {
        // The screen renders what DataStore says, not what was tapped: the write below is the only
        // way the value changes, and it comes back through here. One source of truth, so a failed
        // write can never leave the toggle showing a setting that was not saved.
        observeWeatherUnit()
            .onEach { unit -> reduce { copy(unit = unit) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: WeatherSettingsIntent) {
        when (intent) {
            is WeatherSettingsIntent.UnitSelected -> viewModelScope.launch {
                setWeatherUnit(intent.unit)
            }
        }
    }
}
