package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.common.result.AppResult
import javax.inject.Inject

/** Resolves one location's forecast, from cache or the network. */
internal class GetForecastUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {

    /**
     * By cache key alone — for the detail screen, which is only ever reached from a card that has
     * already fetched, and therefore cached, this location's coordinates.
     */
    suspend operator fun invoke(locationKey: String, forceRefresh: Boolean): AppResult<ForecastResult> =
        repository.getForecastByKey(locationKey, forceRefresh)

    /**
     * With coordinates — for a saved-location card, which may never have been fetched before. The
     * key-only overload would answer [com.minion.scaffold.core.common.error.DomainError.EmptyCache]
     * for a city the user added seconds ago, since nothing has written its row yet.
     */
    suspend operator fun invoke(
        locationKey: String,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): AppResult<ForecastResult> =
        repository.getForecast(locationKey, latitude, longitude, forceRefresh)
}
