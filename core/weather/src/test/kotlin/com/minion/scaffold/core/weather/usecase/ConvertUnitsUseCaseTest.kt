package com.minion.scaffold.core.weather.usecase

import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConvertUnitsUseCaseTest {

    private val convert = ConvertUnitsUseCase()

    private val metricForecast = Forecast(
        current = CurrentConditions(
            temperature = 0.0,
            apparentTemperature = 0.0,
            humidity = 50,
            windSpeed = 10.0,
            condition = WeatherCondition.CLEAR,
        ),
        hourly = emptyList(),
        daily = listOf(
            DailyEntry(
                date = LocalDate.of(2026, 1, 1),
                minTemperature = 0.0,
                maxTemperature = 100.0,
                condition = WeatherCondition.CLEAR,
                precipitationProbability = 0,
            ),
        ),
        notableConditions = emptyList(),
        fetchedAt = Instant.EPOCH,
    )

    @Test
    fun `metric passes through unchanged`() {
        assertEquals(metricForecast, convert(metricForecast, WeatherUnit.METRIC))
    }

    @Test
    fun `0 celsius converts to 32 fahrenheit`() {
        val result = convert(metricForecast, WeatherUnit.IMPERIAL)
        assertEquals(32.0, result.current.temperature, 0.001)
    }

    @Test
    fun `100 celsius converts to 212 fahrenheit`() {
        val result = convert(metricForecast, WeatherUnit.IMPERIAL)
        assertEquals(212.0, result.daily.first().maxTemperature, 0.001)
    }

    @Test
    fun `wind speed converts km per h to mph`() {
        val result = convert(metricForecast, WeatherUnit.IMPERIAL)
        assertEquals(6.21371, result.current.windSpeed, 0.001)
    }
}
