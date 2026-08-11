package com.minion.scaffold.feature.weather.domain

import javax.inject.Inject

/** Resolves the pinned current-location card: GPS fix -> reverse geocode -> cached/live forecast. */
internal class GetCurrentLocationForecastUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    /**
     * @param forceRefresh Skip the cache and fetch fresh.
     * @return [LocationFixOutcome.Found] with the card, or a reason there is nothing to show.
     */
    suspend operator fun invoke(forceRefresh: Boolean): LocationFixOutcome =
        repository.getCurrentLocationCard(forceRefresh)
}
