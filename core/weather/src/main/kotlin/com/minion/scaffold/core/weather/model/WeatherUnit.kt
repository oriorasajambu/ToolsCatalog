package com.minion.scaffold.core.weather.model

/**
 * The display unit system. Not locale-derived — an explicit user choice, defaulting to [METRIC],
 * applied by [com.minion.scaffold.core.weather.usecase.ConvertUnitsUseCase] at the presentation
 * edge. Forecasts are always fetched and cached in metric.
 */
enum class WeatherUnit { METRIC, IMPERIAL }
