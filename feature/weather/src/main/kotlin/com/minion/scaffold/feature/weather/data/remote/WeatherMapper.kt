package com.minion.scaffold.feature.weather.data.remote

import com.minion.scaffold.core.weather.mapper.WmoConditionMapper
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.HourlyEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** DTO -> domain, for the shapes [WeatherApi] returns. Requested in UTC — see [ForecastFields]. */
internal fun WeatherResponseDto.toCurrentConditions(conditionMapper: WmoConditionMapper): CurrentConditions =
    CurrentConditions(
        temperature = current.temperature,
        apparentTemperature = current.apparentTemperature,
        humidity = current.relativeHumidity,
        windSpeed = current.windSpeed,
        condition = conditionMapper(current.weatherCode),
    )

internal fun WeatherResponseDto.toHourlyEntries(conditionMapper: WmoConditionMapper): List<HourlyEntry> =
    hourly.time.indices.map { i ->
        HourlyEntry(
            time = hourly.time[i].toUtcInstant(),
            temperature = hourly.temperature[i],
            condition = conditionMapper(hourly.weatherCode[i]),
            precipitationProbability = hourly.precipitationProbability[i],
            windSpeed = hourly.windSpeed[i],
        )
    }

internal fun WeatherResponseDto.toDailyEntries(conditionMapper: WmoConditionMapper): List<DailyEntry> =
    daily.time.indices.map { i ->
        DailyEntry(
            date = LocalDate.parse(daily.time[i]),
            minTemperature = daily.temperatureMin[i],
            maxTemperature = daily.temperatureMax[i],
            condition = conditionMapper(daily.weatherCode[i]),
            precipitationProbability = daily.precipitationProbabilityMax[i],
        )
    }

/** Open-Meteo returns naive local-looking timestamps (`2026-08-04T14:00`) that are UTC here. */
private fun String.toUtcInstant(): Instant = LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
