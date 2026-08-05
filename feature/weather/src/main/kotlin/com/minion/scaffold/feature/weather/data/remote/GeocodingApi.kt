package com.minion.scaffold.feature.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo's companion geocoding host — a *different* base URL from [WeatherApi]'s, which is why
 * it gets its own Retrofit instance in `WeatherNetworkModule` (SPEC.md §2: two hosts).
 *
 * Search-by-name only. There is no reverse (lat/lon → name) endpoint here, which is why the pinned
 * current-location card resolves its display name through Android's own `Geocoder` instead — see
 * `ReverseGeocoder`.
 */
internal interface GeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int,
        @Query("language") language: String,
        @Query("format") format: String,
    ): GeocodingResponseDto
}

/** The query parameters every [GeocodingApi.search] call passes. */
internal object GeocodingFields {

    /** Enough hits to disambiguate a common name without turning the screen into a phone book. */
    const val RESULT_COUNT = 10
    const val LANGUAGE = "en"
    const val FORMAT = "json"
}
