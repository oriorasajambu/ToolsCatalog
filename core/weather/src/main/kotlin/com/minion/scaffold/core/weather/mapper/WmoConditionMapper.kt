package com.minion.scaffold.core.weather.mapper

import com.minion.scaffold.core.weather.model.WeatherCondition
import javax.inject.Inject

/**
 * Collapses Open-Meteo's numeric WMO weather code into [WeatherCondition].
 *
 * The WMO table has ~28 distinct codes (clear sky, fog, drizzle at three intensities, freezing
 * drizzle, rain at three intensities, freezing rain, snow at three intensities, snow grains,
 * rain/snow showers, thunderstorm with/without hail); this mapper groups them into the handful of
 * buckets the UI draws a different icon for. A code this doesn't recognise falls back to [CLOUDY]
 * rather than throwing — an unfamiliar code from a provider update should degrade gracefully, not
 * crash the forecast screen.
 */
class WmoConditionMapper @Inject constructor() {

    /**
     * Maps a WMO weather code to a [WeatherCondition] bucket.
     *
     * @param wmoCode The numeric WMO code from Open-Meteo.
     * @return The matching bucket, or [WeatherCondition.CLOUDY] for an unrecognised code.
     */
    operator fun invoke(wmoCode: Int): WeatherCondition = when (wmoCode) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.CLOUDY
    }
}
