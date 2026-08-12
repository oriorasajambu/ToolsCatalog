package com.minion.scaffold.core.weather.model

import java.time.LocalDate

/**
 * One row in the 5-10 day daily forecast list.
 *
 * @property date                     The calendar date this row covers.
 * @property minTemperature           The day's low in °C.
 * @property maxTemperature           The day's high in °C.
 * @property condition                The representative condition bucket for the day.
 * @property precipitationProbability Chance of precipitation as a percentage, 0–100.
 */
data class DailyEntry(
    val date: LocalDate,
    val minTemperature: Double,
    val maxTemperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
)
