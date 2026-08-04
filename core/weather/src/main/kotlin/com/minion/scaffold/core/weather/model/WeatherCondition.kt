package com.minion.scaffold.core.weather.model

/**
 * A weather condition, collapsed from Open-Meteo's numeric WMO code into the small set of buckets
 * the UI actually draws a different icon for. See `WmoConditionMapper` in this module for the
 * code -> bucket table.
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
}
