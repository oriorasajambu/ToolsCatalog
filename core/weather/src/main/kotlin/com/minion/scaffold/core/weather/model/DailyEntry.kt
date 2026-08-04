package com.minion.scaffold.core.weather.model

import java.time.LocalDate

/** One row in the 5-10 day daily forecast list. */
data class DailyEntry(
    val date: LocalDate,
    val minTemperature: Double,
    val maxTemperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
)
