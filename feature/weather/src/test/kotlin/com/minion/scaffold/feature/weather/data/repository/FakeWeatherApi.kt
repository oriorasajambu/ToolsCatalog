package com.minion.scaffold.feature.weather.data.repository

import com.minion.scaffold.feature.weather.data.remote.CurrentDto
import com.minion.scaffold.feature.weather.data.remote.DailyDto
import com.minion.scaffold.feature.weather.data.remote.HourlyDto
import com.minion.scaffold.feature.weather.data.remote.WeatherApi
import com.minion.scaffold.feature.weather.data.remote.WeatherResponseDto

internal class FakeWeatherApi : WeatherApi {

    var response: WeatherResponseDto? = sampleResponse()
    var error: Throwable? = null
    var callCount: Int = 0
        private set

    override suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        current: String,
        hourly: String,
        daily: String,
        timezone: String,
        forecastDays: Int,
    ): WeatherResponseDto {
        callCount++
        error?.let { throw it }
        return response ?: error("FakeWeatherApi has neither a response nor an error configured")
    }

    companion object {
        fun sampleResponse() = WeatherResponseDto(
            current = CurrentDto(
                time = "2026-01-01T00:00",
                temperature = 28.0,
                apparentTemperature = 30.0,
                relativeHumidity = 70,
                windSpeed = 12.0,
                weatherCode = 0,
            ),
            hourly = HourlyDto(
                time = listOf("2026-01-01T00:00"),
                temperature = listOf(27.0),
                weatherCode = listOf(0),
                precipitationProbability = listOf(10),
                windSpeed = listOf(8.0),
            ),
            daily = DailyDto(
                time = listOf("2026-01-01"),
                weatherCode = listOf(0),
                temperatureMax = listOf(30.0),
                temperatureMin = listOf(24.0),
                precipitationProbabilityMax = listOf(10),
            ),
        )
    }
}
