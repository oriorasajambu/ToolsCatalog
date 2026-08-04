package com.minion.scaffold.core.weather.model

import java.time.Instant

/** Everything one location's forecast screen needs, already app-computed and ready to render. */
data class Forecast(
    val current: CurrentConditions,
    val hourly: List<HourlyEntry>,
    val daily: List<DailyEntry>,
    val notableConditions: List<NotableCondition>,
    val fetchedAt: Instant,
)
