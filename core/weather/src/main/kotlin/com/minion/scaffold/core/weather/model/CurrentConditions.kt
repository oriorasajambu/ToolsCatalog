package com.minion.scaffold.core.weather.model

/**
 * Right-now readings for one location.
 *
 * Stored in metric (°C, km/h) regardless of the user's display preference — [ConvertUnitsUseCase]
 * converts at the presentation edge, so the cache and the network layer never have to know which
 * unit is currently selected.
 */
data class CurrentConditions(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val condition: WeatherCondition,
)
