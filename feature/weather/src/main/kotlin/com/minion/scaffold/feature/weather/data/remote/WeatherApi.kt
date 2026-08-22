package com.minion.scaffold.feature.weather.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo's forecast endpoint. No API key, no auth header — see SPEC.md §3.
 *
 * The `current`/`hourly`/`daily` field lists are query parameters rather than Open-Meteo's default
 * (return nothing unless asked); an interface method can't carry a default parameter value in
 * Kotlin, so [ForecastFields] holds the field lists the caller always passes.
 */
internal interface WeatherApi {

    @GET("v1/forecast")
    // Retrofit binds one @Query per parameter. A request object would need @QueryMap, which gives
    // up the types and the named field-list constants for nothing.
    @Suppress("LongParameterList")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("hourly") hourly: String,
        @Query("daily") daily: String,
        @Query("timezone") timezone: String,
        @Query("forecast_days") forecastDays: Int,
    ): WeatherResponseDto
}

/** The field lists [WeatherMapper] reads, and nothing else — kept beside the call site. */
internal object ForecastFields {
    const val CURRENT =
        "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
    const val HOURLY =
        "temperature_2m,weather_code,precipitation_probability,wind_speed_10m"
    const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"

    // UTC, not "auto" (the location's local time). Timestamps land in [HourlyEntry.time] as an
    // absolute Instant, and "auto" would make that instant depend on which timezone the requested
    // lat/lon happens to sit in — unparseable without a second lookup this app has no need for.
    const val TIMEZONE = "UTC"
    const val FORECAST_DAYS = 10
}
