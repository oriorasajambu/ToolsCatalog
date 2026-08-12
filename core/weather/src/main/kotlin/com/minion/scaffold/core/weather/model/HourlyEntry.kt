package com.minion.scaffold.core.weather.model

import java.time.Instant

/**
 * One point in the next 24-48h hourly strip.
 *
 * @property time                     The instant this entry covers.
 * @property temperature              Air temperature in °C.
 * @property condition                The condition bucket for the hour.
 * @property precipitationProbability Chance of precipitation as a percentage, 0–100.
 * @property windSpeed                Wind speed in km/h.
 */
data class HourlyEntry(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
    val windSpeed: Double,
)
