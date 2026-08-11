package com.minion.scaffold.core.weather.model

import java.time.Instant

/**
 * Everything one location's forecast screen needs, already app-computed and ready to render.
 *
 * @property current           The right-now readings.
 * @property hourly            The next 24–48h, one entry per hour.
 * @property daily             The 5–10 day outlook, one entry per day.
 * @property notableConditions App-computed conditions worth calling out, possibly empty.
 * @property fetchedAt         When this forecast was retrieved, for staleness display.
 */
data class Forecast(
    val current: CurrentConditions,
    val hourly: List<HourlyEntry>,
    val daily: List<DailyEntry>,
    val notableConditions: List<NotableCondition>,
    val fetchedAt: Instant,
)
