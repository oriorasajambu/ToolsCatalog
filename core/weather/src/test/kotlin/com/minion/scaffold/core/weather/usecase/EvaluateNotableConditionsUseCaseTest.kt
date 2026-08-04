package com.minion.scaffold.core.weather.usecase

import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.NotableCondition
import com.minion.scaffold.core.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EvaluateNotableConditionsUseCaseTest {

    private val evaluate = EvaluateNotableConditionsUseCase()

    private fun calmConditions(windSpeed: Double = 5.0, temperature: Double = 20.0) = CurrentConditions(
        temperature = temperature,
        apparentTemperature = temperature,
        humidity = 50,
        windSpeed = windSpeed,
        condition = WeatherCondition.CLEAR,
    )

    private fun today(min: Double = 15.0, max: Double = 25.0) = DailyEntry(
        date = LocalDate.of(2026, 1, 1),
        minTemperature = min,
        maxTemperature = max,
        condition = WeatherCondition.CLEAR,
        precipitationProbability = 0,
    )

    @Test
    fun `calm conditions produce no notable conditions`() {
        val result = evaluate(calmConditions(), emptyList(), listOf(today()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `wind just above 50 kmh is moderate`() {
        val result = evaluate(calmConditions(windSpeed = 51.0), emptyList(), listOf(today()))
        assertEquals(
            NotableCondition(NotableCondition.Kind.HIGH_WIND, NotableCondition.Severity.MODERATE),
            result.single(),
        )
    }

    @Test
    fun `wind at exactly 50 kmh is not notable`() {
        val result = evaluate(calmConditions(windSpeed = 50.0), emptyList(), listOf(today()))
        assertTrue(result.none { it.kind == NotableCondition.Kind.HIGH_WIND })
    }

    @Test
    fun `wind above 80 kmh is high severity`() {
        val result = evaluate(calmConditions(windSpeed = 81.0), emptyList(), listOf(today()))
        assertEquals(
            NotableCondition(NotableCondition.Kind.HIGH_WIND, NotableCondition.Severity.HIGH),
            result.single(),
        )
    }

    @Test
    fun `extreme heat above 40 is high severity`() {
        val result = evaluate(calmConditions(temperature = 41.0), emptyList(), listOf(today(max = 41.0)))
        assertEquals(
            NotableCondition(NotableCondition.Kind.EXTREME_HEAT, NotableCondition.Severity.HIGH),
            result.single(),
        )
    }

    @Test
    fun `extreme cold below minus 10 is moderate severity`() {
        val result = evaluate(calmConditions(temperature = -11.0), emptyList(), listOf(today(min = -11.0)))
        assertEquals(
            NotableCondition(NotableCondition.Kind.EXTREME_COLD, NotableCondition.Severity.MODERATE),
            result.single(),
        )
    }

    @Test
    fun `heavy rain probability across many hours is notable`() {
        val hourly = (1..10).map {
            HourlyEntry(
                time = Instant.EPOCH.plusSeconds(it * 3600L),
                temperature = 18.0,
                condition = WeatherCondition.RAIN,
                precipitationProbability = 90,
                windSpeed = 5.0,
            )
        }
        val result = evaluate(calmConditions(), hourly, listOf(today()))
        assertEquals(
            NotableCondition(NotableCondition.Kind.HEAVY_RAIN, NotableCondition.Severity.HIGH),
            result.single { it.kind == NotableCondition.Kind.HEAVY_RAIN },
        )
    }

    @Test
    fun `snow in the current condition is notable`() {
        val result = evaluate(calmConditions().copy(condition = WeatherCondition.SNOW), emptyList(), listOf(today()))
        assertEquals(
            NotableCondition(NotableCondition.Kind.SNOW, NotableCondition.Severity.MODERATE),
            result.single(),
        )
    }
}
