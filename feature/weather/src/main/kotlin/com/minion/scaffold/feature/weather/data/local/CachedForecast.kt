package com.minion.scaffold.feature.weather.data.local

import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.NotableCondition
import com.minion.scaffold.core.weather.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate

/**
 * [Forecast], reshaped with only Gson-friendly types (epoch millis instead of [Instant], ISO
 * strings instead of [LocalDate]) so [ForecastCacheEntity.forecastJson] round-trips without a
 * custom `TypeAdapter`. Never leaves `data/local/` — everything above this package works with the
 * domain [Forecast].
 */
internal data class CachedForecast(
    val current: CachedCurrentConditions,
    val hourly: List<CachedHourlyEntry>,
    val daily: List<CachedDailyEntry>,
    val notableConditions: List<CachedNotableCondition>,
    val fetchedAtEpochMillis: Long,
)

internal data class CachedCurrentConditions(
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val condition: WeatherCondition,
)

internal data class CachedHourlyEntry(
    val epochMillis: Long,
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
    val windSpeed: Double,
)

internal data class CachedDailyEntry(
    val isoDate: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val condition: WeatherCondition,
    val precipitationProbability: Int,
)

internal data class CachedNotableCondition(
    val kind: NotableCondition.Kind,
    val severity: NotableCondition.Severity,
)

internal fun Forecast.toCache(): CachedForecast = CachedForecast(
    current = CachedCurrentConditions(
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.condition,
    ),
    hourly = hourly.map {
        CachedHourlyEntry(
            epochMillis = it.time.toEpochMilli(),
            temperature = it.temperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
            windSpeed = it.windSpeed,
        )
    },
    daily = daily.map {
        CachedDailyEntry(
            isoDate = it.date.toString(),
            minTemperature = it.minTemperature,
            maxTemperature = it.maxTemperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
        )
    },
    notableConditions = notableConditions.map { CachedNotableCondition(it.kind, it.severity) },
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun CachedForecast.toDomain(): Forecast = Forecast(
    current = CurrentConditions(
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.humidity,
        windSpeed = current.windSpeed,
        condition = current.condition,
    ),
    hourly = hourly.map {
        HourlyEntry(
            time = Instant.ofEpochMilli(it.epochMillis),
            temperature = it.temperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
            windSpeed = it.windSpeed,
        )
    },
    daily = daily.map {
        DailyEntry(
            date = LocalDate.parse(it.isoDate),
            minTemperature = it.minTemperature,
            maxTemperature = it.maxTemperature,
            condition = it.condition,
            precipitationProbability = it.precipitationProbability,
        )
    },
    notableConditions = notableConditions.map { NotableCondition(it.kind, it.severity) },
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
)
