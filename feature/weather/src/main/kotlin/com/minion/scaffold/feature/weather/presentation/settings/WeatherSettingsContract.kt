package com.minion.scaffold.feature.weather.presentation.settings

import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.WeatherUnit

/**
 * The unit toggle (SPEC.md §7.5).
 *
 * No `ContentState` here, unlike the other three screens: there is exactly one field, it always
 * has a value (the preference defaults to metric), and it is read from disk rather than the
 * network — so there is no loading, empty or failure phase for a sealed hierarchy to make
 * unrepresentable.
 */
internal data class WeatherSettingsState(
    val unit: WeatherUnit = WeatherUnit.METRIC,
) : UiState

internal sealed interface WeatherSettingsIntent : UiIntent {
    data class UnitSelected(val unit: WeatherUnit) : WeatherSettingsIntent
}

/** Declared for the contract's shape; this screen has no one-shot events yet. */
internal sealed interface WeatherSettingsEffect : UiEffect
