package com.minion.scaffold.core.weather.usecase

import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.DailyEntry
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.HourlyEntry
import com.minion.scaffold.core.weather.model.WeatherUnit
import javax.inject.Inject

/**
 * Converts a [Forecast] from the metric units it is fetched and cached in to the unit system the
 * user has chosen to see, at the presentation edge.
 *
 * A no-op for [WeatherUnit.METRIC] — the data is already metric — so callers can apply this
 * unconditionally without branching on the preference themselves.
 */
class ConvertUnitsUseCase @Inject constructor() {

    operator fun invoke(forecast: Forecast, unit: WeatherUnit): Forecast {
        if (unit == WeatherUnit.METRIC) return forecast
        return forecast.copy(
            current = forecast.current.toImperial(),
            hourly = forecast.hourly.map { it.toImperial() },
            daily = forecast.daily.map { it.toImperial() },
        )
    }

    private fun CurrentConditions.toImperial() = copy(
        temperature = temperature.celsiusToFahrenheit(),
        apparentTemperature = apparentTemperature.celsiusToFahrenheit(),
        windSpeed = windSpeed.kmhToMph(),
    )

    private fun HourlyEntry.toImperial() = copy(
        temperature = temperature.celsiusToFahrenheit(),
        windSpeed = windSpeed.kmhToMph(),
    )

    private fun DailyEntry.toImperial() = copy(
        minTemperature = minTemperature.celsiusToFahrenheit(),
        maxTemperature = maxTemperature.celsiusToFahrenheit(),
    )

    private fun Double.celsiusToFahrenheit(): Double = this * 9.0 / 5.0 + 32.0

    private fun Double.kmhToMph(): Double = this * KM_TO_MILES

    private companion object {
        const val KM_TO_MILES = 0.621371
    }
}
