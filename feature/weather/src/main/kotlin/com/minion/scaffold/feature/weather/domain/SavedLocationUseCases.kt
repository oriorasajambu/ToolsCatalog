package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.weather.model.Location
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The saved-locations list operations, one class per action per the repo convention.
 *
 * They share a file rather than getting five near-empty ones of their own: they are the CRUD
 * surface of a single list, they always change together, and splitting them would put more
 * ceremony on screen than logic.
 */

/** Observes the saved-locations list. */
internal class ObserveSavedLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    /** @return A [Flow] of the saved locations in their chosen order. */
    operator fun invoke(): Flow<List<Location>> = repository.observeSavedLocations()
}

/** Appends a location to the saved list. */
internal class AddSavedLocationUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    /** @param location The location to save. */
    suspend operator fun invoke(location: Location) = repository.addSavedLocation(location)
}

/** Removes a location from the saved list. */
internal class RemoveSavedLocationUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    /** @param locationId The id of the location to remove. */
    suspend operator fun invoke(locationId: String) = repository.removeSavedLocation(locationId)
}

/** Persists a new saved-list order. */
internal class ReorderSavedLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    /** @param orderedIds The complete list of location ids, front to back. */
    suspend operator fun invoke(orderedIds: List<String>) = repository.reorderSavedLocations(orderedIds)
}
