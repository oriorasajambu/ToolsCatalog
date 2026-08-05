package com.minion.scaffold.feature.weather.domain

import com.minion.scaffold.core.weather.model.WeatherUnit
import kotlinx.coroutines.flow.Flow

/**
 * The user's display-unit choice (SPEC.md §6).
 *
 * A [Flow] rather than a one-shot read because every screen showing a temperature has to re-render
 * the moment the toggle flips — including one sitting behind the settings screen on the back stack.
 */
internal interface WeatherPreferencesRepository {

    /** Defaults to [WeatherUnit.METRIC]. Explicitly not locale-derived — see SPEC.md §6. */
    val unit: Flow<WeatherUnit>

    suspend fun setUnit(unit: WeatherUnit)
}
