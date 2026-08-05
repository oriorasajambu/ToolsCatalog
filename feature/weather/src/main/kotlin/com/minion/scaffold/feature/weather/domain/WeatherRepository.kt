package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.Location
import com.minion.scaffold.core.weather.model.LocationSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * A forecast for one location, plus whatever the repository knows about how fresh it is.
 *
 * [isStale] is separate from [AppResult.Failure] on purpose (SPEC.md §6/§8): a background refresh
 * that fails while a cache exists must never surface a [com.minion.scaffold.core.common.error.DomainError]
 * to the UI — it silently keeps showing [forecast] tagged stale instead.
 */
data class ForecastResult(
    val forecast: Forecast,
    val isStale: Boolean,
)

/** The current-location pinned card's resolved identity and forecast. */
data class LocationCard(
    val locationId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val result: ForecastResult,
)

/** Why the pinned current-location card has nothing to show yet. */
sealed interface LocationFixOutcome {
    data class Found(val card: LocationCard) : LocationFixOutcome

    /** No GPS fix available yet (deep indoors, first cold start) — retry, never a hard failure. */
    data object NoFixAvailable : LocationFixOutcome

    /** A fix was resolved but the forecast fetch failed with nothing cached to fall back to. */
    data class Failed(val error: com.minion.scaffold.core.common.error.DomainError) : LocationFixOutcome
}

/**
 * The weather feature's one data-access seam: a GPS-resolved current-location forecast, forecasts
 * for arbitrary lat/lons, the user's saved-location list, and place-name search.
 */
internal interface WeatherRepository {

    /**
     * Resolves the device's current GPS fix, reverse-geocodes it to a display name, and returns
     * its forecast — from cache if fresh, from the network otherwise.
     */
    suspend fun getCurrentLocationCard(forceRefresh: Boolean): LocationFixOutcome

    /** The forecast for one location, identified by a stable cache key plus its coordinates. */
    suspend fun getForecast(
        locationKey: String,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): AppResult<ForecastResult>

    /**
     * The forecast for a location the caller only has the cache key for — the forecast detail
     * screen, reached from a card that has already fetched (and therefore cached) coordinates for
     * [locationKey]. [DomainError.EmptyCache] when nothing was ever cached for that key: this
     * screen is only ever opened from a card, so that means the caller passed a stale/unknown key.
     */
    suspend fun getForecastByKey(locationKey: String, forceRefresh: Boolean): AppResult<ForecastResult>

    /**
     * The user's saved locations, in their chosen order.
     *
     * A [Flow] so the home screen sees an add land while it is on the back stack behind the search
     * screen, without anything having to tell it to re-read.
     */
    fun observeSavedLocations(): Flow<List<Location>>

    /** Appends [location] to the end of the list. Re-adding an existing id is a no-op. */
    suspend fun addSavedLocation(location: Location)

    suspend fun removeSavedLocation(locationId: String)

    /** Persists a new order. [orderedIds] is the complete list, front to back. */
    suspend fun reorderSavedLocations(orderedIds: List<String>)

    /**
     * Place-name search against the geocoding host.
     *
     * A query that matches nothing is [AppResult.Success] with an empty list, not a failure —
     * "no such place" is an answer, and the search screen renders it as an empty state rather than
     * an error with a retry button.
     */
    suspend fun searchLocations(query: String): AppResult<List<LocationSearchResult>>
}
