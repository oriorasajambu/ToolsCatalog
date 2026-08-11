package com.minion.scaffold.feature.weather.presentation.detail

import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.WeatherUnit

/** One location's forecast: current conditions, notable-conditions banner, hourly strip, daily list. */
internal data class ForecastDetailState(
    /** The mutually exclusive load phase. */
    val content: ContentState = ContentState.Loading,

    /**
     * The display unit the [ContentState.Success] forecast has *already* been converted into. Here
     * so the composable knows which degree suffix to print, not so it can convert anything itself.
     */
    val unit: WeatherUnit = WeatherUnit.METRIC,
) : UiState {

    /** The forecast load phase. */
    sealed interface ContentState {

        /** The forecast is loading. */
        data object Loading : ContentState

        /**
         * The forecast is ready.
         *
         * [staleHoursAgo] non-null only when shown after a failed background refresh — see
         * `WeatherHomeState.LocationCardUi.staleHoursAgo` for the same reasoning.
         *
         * @property forecast      The forecast to show.
         * @property staleHoursAgo Hours since fetch when shown stale, or `null` when fresh.
         */
        data class Success(val forecast: Forecast, val staleHoursAgo: Long?) : ContentState

        /**
         * The fetch failed with no cache to fall back to.
         *
         * Only reachable when there is no cache at all (SPEC.md §8) — a cached failure never
         * reaches this state, it stays [Success] with a stale label.
         *
         * @property error Why the forecast could not be retrieved.
         */
        data class Failure(val error: DomainError) : ContentState
    }
}

/** Everything the user can do on the forecast detail screen. */
internal sealed interface ForecastDetailIntent : UiIntent {

    /** Pull-to-refresh: force-refresh the forecast. */
    data object PullToRefresh : ForecastDetailIntent

    /** Retry a failed fetch. */
    data object Retry : ForecastDetailIntent
}

/** No one-shot events in this slice; the type stays declared for whatever needs one later. */
internal sealed interface ForecastDetailEffect : UiEffect
