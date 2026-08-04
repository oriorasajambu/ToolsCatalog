package com.minion.scaffold.feature.weather.presentation.detail

import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.Forecast

/** One location's forecast: current conditions, notable-conditions banner, hourly strip, daily list. */
internal data class ForecastDetailState(
    val content: ContentState = ContentState.Loading,
) : UiState {

    sealed interface ContentState {
        data object Loading : ContentState

        /** [staleHoursAgo] non-null only when shown after a failed background refresh — see
         *  `WeatherHomeState.LocationCardUi.staleHoursAgo` for the same reasoning. */
        data class Success(val forecast: Forecast, val staleHoursAgo: Long?) : ContentState

        /** Only reachable when there is no cache at all (SPEC.md §8) — a cached failure never
         *  reaches this state, it stays [Success] with a stale label. */
        data class Failure(val error: DomainError) : ContentState
    }
}

internal sealed interface ForecastDetailIntent : UiIntent {
    data object PullToRefresh : ForecastDetailIntent
    data object Retry : ForecastDetailIntent
}

/** No one-shot events in this slice; the type stays declared for whatever needs one later. */
internal sealed interface ForecastDetailEffect : UiEffect
