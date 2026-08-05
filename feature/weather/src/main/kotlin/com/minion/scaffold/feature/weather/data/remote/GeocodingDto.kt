package com.minion.scaffold.feature.weather.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Open-Meteo's geocoding `/v1/search` response.
 *
 * [results] is nullable on purpose: the API omits the key entirely for a query that matches
 * nothing, rather than returning an empty array — so a non-null `List` here would deserialize to
 * `null` anyway and NPE at the first access.
 */
internal data class GeocodingResponseDto(
    @SerializedName("results") val results: List<GeocodingResultDto>?,
)

internal data class GeocodingResultDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("country") val country: String?,
    @SerializedName("admin1") val admin1: String?,
)
