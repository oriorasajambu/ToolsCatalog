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

internal class ObserveSavedLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    operator fun invoke(): Flow<List<Location>> = repository.observeSavedLocations()
}

internal class AddSavedLocationUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(location: Location) = repository.addSavedLocation(location)
}

internal class RemoveSavedLocationUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(locationId: String) = repository.removeSavedLocation(locationId)
}

internal class ReorderSavedLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(orderedIds: List<String>) = repository.reorderSavedLocations(orderedIds)
}
