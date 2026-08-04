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
import androidx.compose.ui.graphics.vector.ImageVector
import com.minion.scaffold.core.weather.model.WeatherCondition
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

