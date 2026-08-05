package com.minion.scaffold.feature.weather.presentation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.R

/**
 * [WeatherCondition] -> icon, shared by the home card and the forecast detail screen so the two
 * never draw a condition differently.
 */
internal fun WeatherCondition.toIcon(): ImageVector = when (this) {
    WeatherCondition.CLEAR -> Icons.Filled.WbSunny
    WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.CloudQueue
    WeatherCondition.CLOUDY -> Icons.Filled.Cloud
    WeatherCondition.FOG -> Icons.Filled.Dehaze
    WeatherCondition.DRIZZLE -> Icons.Filled.Grain
    WeatherCondition.RAIN -> Icons.Filled.Umbrella
    WeatherCondition.SNOW -> Icons.Filled.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Filled.Bolt
}

/**
 * How old a cached forecast is, in words (SPEC.md §6).
 *
 * A `@Composable` returning a `String` rather than a plain function, because picking *which*
 * phrasing applies is a branch over resources — resolving them here keeps every caller from
 * repeating the same three-way `when`, and keeps the choice on the composable side of the line
 * where text gets made.
 */
@Composable
internal fun stalenessLabel(hoursAgo: Long): String {
    val days = hoursAgo / HOURS_PER_DAY
    return when {
        days < 1 -> stringResource(R.string.weather_updated_hours_ago, hoursAgo)
        days == 1L -> stringResource(R.string.weather_updated_yesterday)
        else -> stringResource(R.string.weather_updated_days_ago, days)
    }
}

private const val HOURS_PER_DAY = 24

/**
 * The degree suffix for the user's chosen unit. The number itself is converted in the ViewModel —
 * this only picks how to label it, which is why it lives with the other `@StringRes` lookups.
 */
@StringRes
internal fun WeatherUnit.temperatureFormatRes(): Int = when (this) {
    WeatherUnit.METRIC -> R.string.weather_temperature_celsius
    WeatherUnit.IMPERIAL -> R.string.weather_temperature_fahrenheit
}

/** Humidity and wind on one line; the wind unit follows the same preference as temperature. */
@StringRes
internal fun WeatherUnit.humidityWindFormatRes(): Int = when (this) {
    WeatherUnit.METRIC -> R.string.weather_humidity_wind_metric
    WeatherUnit.IMPERIAL -> R.string.weather_humidity_wind_imperial
}

@StringRes
internal fun WeatherCondition.toLabelRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_condition_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_condition_cloudy
    WeatherCondition.FOG -> R.string.weather_condition_fog
    WeatherCondition.DRIZZLE -> R.string.weather_condition_drizzle
    WeatherCondition.RAIN -> R.string.weather_condition_rain
    WeatherCondition.SNOW -> R.string.weather_condition_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_condition_thunderstorm
}

