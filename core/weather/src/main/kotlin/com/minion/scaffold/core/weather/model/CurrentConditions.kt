package com.minion.scaffold.core.weather.model

/**
 * Right-now readings for one location.
 *
 * Stored in metric (°C, km/h) regardless of the user's display preference — [ConvertUnitsUseCase]
 * converts at the presentation edge, so the cache and the network layer never have to know which
 * unit is currently selected.
 *
 * @property temperature         Air temperature in °C.
 * @property apparentTemperature "Feels like" temperature in °C.
 * @property humidity            Relative humidity as a percentage, 0–100.
 * @property windSpeed           Wind speed in km/h.
 * @property condition           The current condition bucket.
 */
data class CurrentConditions(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val condition: WeatherCondition,
)
