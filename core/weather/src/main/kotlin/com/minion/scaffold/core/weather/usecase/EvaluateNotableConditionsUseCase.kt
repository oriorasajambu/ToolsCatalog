package com.minion.scaffold.core.weather.usecase

import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.NotableCondition
import com.minion.scaffold.core.weather.model.WeatherCondition
import javax.inject.Inject

/**
 * Decides which [NotableCondition]s to surface for a forecast.
 *
 * These thresholds are **app-computed, not sourced from any weather authority** — there is no
 * official-alert integration in this app (see SPEC.md §11) — and may need regional tuning later
 * (50 km/h wind is routine in some climates and notable in others). This is why the UI never calls
 * these "alerts" or "warnings": both words imply a national weather service stood behind them.
 *
 * Evaluated over [current] plus the next 24h of [hourly] and the first [DailyEntry] of [daily], so
 * a condition arriving in the next few hours is surfaced even if it isn't happening right now.
 */
class EvaluateNotableConditionsUseCase @Inject constructor() {

    operator fun invoke(
        current: CurrentConditions,
        hourly: List<HourlyEntry>,
        daily: List<DailyEntry>,
    ): List<NotableCondition> {
        val upcoming = hourly.take(HOURS_LOOKAHEAD)
        val today = daily.firstOrNull()

        return buildList {
            windCondition(current.windSpeed, upcoming.maxOfOrNull { it.windSpeed } ?: 0.0)
                ?.let(::add)
            heatCondition(current.temperature, today?.maxTemperature)?.let(::add)
            coldCondition(current.temperature, today?.minTemperature)?.let(::add)
            rainCondition(upcoming)?.let(::add)
            snowCondition(current.condition, upcoming)?.let(::add)
        }
    }

    private fun windCondition(currentSpeed: Double, upcomingMax: Double): NotableCondition? {
        val peak = maxOf(currentSpeed, upcomingMax)
        return when {
            peak > HIGH_WIND_KMH -> NotableCondition(NotableCondition.Kind.HIGH_WIND, NotableCondition.Severity.HIGH)
            peak > MODERATE_WIND_KMH -> NotableCondition(NotableCondition.Kind.HIGH_WIND, NotableCondition.Severity.MODERATE)
            else -> null
        }
    }

    private fun heatCondition(currentTemp: Double, todayMax: Double?): NotableCondition? {
        val peak = maxOf(currentTemp, todayMax ?: currentTemp)
        return when {
            peak >= EXTREME_HEAT_HIGH_C -> NotableCondition(NotableCondition.Kind.EXTREME_HEAT, NotableCondition.Severity.HIGH)
            peak >= EXTREME_HEAT_MODERATE_C -> NotableCondition(NotableCondition.Kind.EXTREME_HEAT, NotableCondition.Severity.MODERATE)
            else -> null
        }
    }

    private fun coldCondition(currentTemp: Double, todayMin: Double?): NotableCondition? {
        val trough = minOf(currentTemp, todayMin ?: currentTemp)
        return when {
            trough <= EXTREME_COLD_HIGH_C -> NotableCondition(NotableCondition.Kind.EXTREME_COLD, NotableCondition.Severity.HIGH)
            trough <= EXTREME_COLD_MODERATE_C -> NotableCondition(NotableCondition.Kind.EXTREME_COLD, NotableCondition.Severity.MODERATE)
            else -> null
        }
    }

    private fun rainCondition(upcoming: List<HourlyEntry>): NotableCondition? {
        val heavyRainHours = upcoming.count {
            it.condition == WeatherCondition.RAIN && it.precipitationProbability >= HEAVY_RAIN_PROBABILITY
        }
        return when {
            heavyRainHours >= HEAVY_RAIN_HOURS_HIGH -> NotableCondition(NotableCondition.Kind.HEAVY_RAIN, NotableCondition.Severity.HIGH)
            heavyRainHours >= HEAVY_RAIN_HOURS_MODERATE -> NotableCondition(NotableCondition.Kind.HEAVY_RAIN, NotableCondition.Severity.MODERATE)
            else -> null
        }
    }

    private fun snowCondition(currentCondition: WeatherCondition, upcoming: List<HourlyEntry>): NotableCondition? {
        val snowing = currentCondition == WeatherCondition.SNOW ||
            upcoming.any { it.condition == WeatherCondition.SNOW }
        return if (snowing) NotableCondition(NotableCondition.Kind.SNOW, NotableCondition.Severity.MODERATE) else null
    }

    private companion object {
        const val HOURS_LOOKAHEAD = 24

        // Wind, km/h — a bicycle becomes hard to control well above this, a sustained gust starts
        // moving unsecured outdoor furniture well above the second.
        const val MODERATE_WIND_KMH = 50.0
        const val HIGH_WIND_KMH = 80.0

        // Temperature, °C.
        const val EXTREME_HEAT_MODERATE_C = 35.0
        const val EXTREME_HEAT_HIGH_C = 40.0
        const val EXTREME_COLD_MODERATE_C = -10.0
        const val EXTREME_COLD_HIGH_C = -20.0

        // Rain — how many of the next 24 hours have a high (>= 70%) chance of rain.
        const val HEAVY_RAIN_PROBABILITY = 70
        const val HEAVY_RAIN_HOURS_MODERATE = 3
        const val HEAVY_RAIN_HOURS_HIGH = 8
    }
}
