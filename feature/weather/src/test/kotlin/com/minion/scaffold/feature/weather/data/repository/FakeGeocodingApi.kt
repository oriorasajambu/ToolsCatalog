package com.minion.scaffold.feature.weather.data.repository

import com.minion.scaffold.feature.weather.data.remote.GeocodingApi
import com.minion.scaffold.feature.weather.data.remote.GeocodingResponseDto
import com.minion.scaffold.feature.weather.data.remote.GeocodingResultDto

internal class FakeGeocodingApi : GeocodingApi {

    /** Defaults to the shape the real API returns for a query that matched nothing: no key at all. */
    var response: GeocodingResponseDto = GeocodingResponseDto(results = null)
    var error: Throwable? = null
    var lastQuery: String? = null
        private set

    override suspend fun search(
        name: String,
        count: Int,
        language: String,
        format: String,
    ): GeocodingResponseDto {
        lastQuery = name
        error?.let { throw it }
        return response
    }

    companion object {

        fun resultsFor(vararg names: String) = GeocodingResponseDto(
            results = names.mapIndexed { index, cityName ->
                GeocodingResultDto(
                    id = index.toLong(),
                    name = cityName,
                    latitude = 1.0 + index,
                    longitude = 2.0 + index,
                    country = "Testland",
                    admin1 = "Test Province",
                )
            },
        )
    }
}
