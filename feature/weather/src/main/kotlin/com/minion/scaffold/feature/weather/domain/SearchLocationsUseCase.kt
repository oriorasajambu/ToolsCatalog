package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.weather.model.LocationSearchResult
import javax.inject.Inject

/**
 * Place-name search for the add-location screen.
 *
 * A query shorter than [MIN_QUERY_LENGTH] short-circuits to an empty result without hitting the
 * network: a one-character query matches most of the planet, so the round trip buys nothing but a
 * list the user cannot use. Debouncing is the caller's job — this only guards the floor.
 */
internal class SearchLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {

    /**
     * @param query The place-name query.
     * @return The matches (possibly empty), or a failure. Empty without a network call when [query]
     *   is shorter than [MIN_QUERY_LENGTH].
     */
    suspend operator fun invoke(query: String): AppResult<List<LocationSearchResult>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return AppResult.Success(emptyList())
        return repository.searchLocations(trimmed)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
