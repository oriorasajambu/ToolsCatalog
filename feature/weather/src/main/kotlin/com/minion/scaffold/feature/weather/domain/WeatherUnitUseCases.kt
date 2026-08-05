package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.weather.model.WeatherUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes the metric/imperial display preference (SPEC.md §6). */

internal class ObserveWeatherUnitUseCase @Inject constructor(
    private val repository: WeatherPreferencesRepository,
) {
    operator fun invoke(): Flow<WeatherUnit> = repository.unit
}

internal class SetWeatherUnitUseCase @Inject constructor(
    private val repository: WeatherPreferencesRepository,
) {
    suspend operator fun invoke(unit: WeatherUnit) = repository.setUnit(unit)
}
