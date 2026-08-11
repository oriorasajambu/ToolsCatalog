package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.weather.model.WeatherUnit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reads and writes the metric/imperial display preference (SPEC.md §6). */

/** Observes the metric/imperial display preference. */
internal class ObserveWeatherUnitUseCase @Inject constructor(
    private val repository: WeatherPreferencesRepository,
) {
    /** @return A [Flow] of the display unit, [WeatherUnit.METRIC] by default. */
    operator fun invoke(): Flow<WeatherUnit> = repository.unit
}

/** Sets the metric/imperial display preference. */
internal class SetWeatherUnitUseCase @Inject constructor(
    private val repository: WeatherPreferencesRepository,
) {
    /** @param unit The unit system to display in. */
    suspend operator fun invoke(unit: WeatherUnit) = repository.setUnit(unit)
}
