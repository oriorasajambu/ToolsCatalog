package com.minion.scaffold.feature.weather.data.remote

import com.minion.scaffold.core.weather.model.LocationSearchResult

/**
 * DTO → domain for the geocoding endpoint.
 *
 * The provider's numeric id becomes a `String` because that is what the forecast cache keys on —
 * the same column also holds the literal `"current"` for the GPS card, so the key type has to be
 * wide enough for both.
 */
internal fun GeocodingResponseDto.toSearchResults(): List<LocationSearchResult> =
    results.orEmpty().map { it.toDomain() }

internal fun GeocodingResultDto.toDomain(): LocationSearchResult = LocationSearchResult(
    id = id.toString(),
    name = name,
    country = country,
    admin1 = admin1,
    latitude = latitude,
    longitude = longitude,
)
