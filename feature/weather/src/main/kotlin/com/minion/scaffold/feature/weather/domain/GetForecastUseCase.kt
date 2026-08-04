package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.common.result.AppResult
import javax.inject.Inject

/** Resolves one location's forecast for the detail screen, from cache or the network. */
internal class GetForecastUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(locationKey: String, forceRefresh: Boolean): AppResult<ForecastResult> =
        repository.getForecastByKey(locationKey, forceRefresh)
}
