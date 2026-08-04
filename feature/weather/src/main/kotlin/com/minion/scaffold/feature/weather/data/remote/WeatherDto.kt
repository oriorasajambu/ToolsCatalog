package com.minion.scaffold.feature.weather.data.remote

import com.google.gson.annotations.SerializedName

/** Open-Meteo's `/v1/forecast` response — network model only, never crosses into `presentation/`. */
internal data class WeatherResponseDto(
    @SerializedName("current") val current: CurrentDto,
    @SerializedName("hourly") val hourly: HourlyDto,
    @SerializedName("daily") val daily: DailyDto,
)

internal data class CurrentDto(
    @SerializedName("time") val time: String,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("apparent_temperature") val apparentTemperature: Double,
    @SerializedName("relative_humidity_2m") val relativeHumidity: Int,
    @SerializedName("wind_speed_10m") val windSpeed: Double,
    @SerializedName("weather_code") val weatherCode: Int,
)

/** Parallel arrays, one entry per hour — Open-Meteo's column-oriented shape. */
internal data class HourlyDto(
    @SerializedName("time") val time: List<String>,
    @SerializedName("temperature_2m") val temperature: List<Double>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int>,
    @SerializedName("wind_speed_10m") val windSpeed: List<Double>,
)

/** Parallel arrays, one entry per day. */
internal data class DailyDto(
    @SerializedName("time") val time: List<String>,
    @SerializedName("weather_code") val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max") val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min") val temperatureMin: List<Double>,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>,
)
