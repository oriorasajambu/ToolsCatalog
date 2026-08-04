package com.minion.scaffold.core.weather.model

import java.time.Instant

/** One point in the next 24-48h hourly strip. */
data class HourlyEntry(
    val time: Instant,
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
    val windSpeed: Double,
)
