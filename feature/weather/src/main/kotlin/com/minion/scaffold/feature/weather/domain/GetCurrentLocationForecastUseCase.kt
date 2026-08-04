package com.minion.scaffold.feature.weather.domain

import javax.inject.Inject

/** Resolves the pinned current-location card: GPS fix -> reverse geocode -> cached/live forecast. */
internal class GetCurrentLocationForecastUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean): LocationFixOutcome =
        repository.getCurrentLocationCard(forceRefresh)
}
