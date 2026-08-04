package com.minion.scaffold.feature.weather.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.navigation.WeatherDetailRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.weather.data.repository.CURRENT_LOCATION_KEY
import com.minion.scaffold.feature.weather.domain.GetForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class ForecastDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecast: GetForecastUseCase,
) : MviViewModel<ForecastDetailState, ForecastDetailIntent, ForecastDetailEffect>(ForecastDetailState()) {

    private val locationId: String =
        savedStateHandle.get<String>(WeatherDetailRoute.ARG_LOCATION_ID) ?: CURRENT_LOCATION_KEY

    init {
        load(forceRefresh = false)
    }

    override fun onIntent(intent: ForecastDetailIntent) {
        when (intent) {
            ForecastDetailIntent.PullToRefresh -> load(forceRefresh = true)
            ForecastDetailIntent.Retry -> load(forceRefresh = false)
        }
    }

    private fun load(forceRefresh: Boolean) = viewModelScope.launch {
        if (currentState.content !is ForecastDetailState.ContentState.Success) {
            reduce { copy(content = ForecastDetailState.ContentState.Loading) }
        }

        when (val result = getForecast(locationId, forceRefresh)) {
            is AppResult.Success -> reduce {
                copy(
                    content = ForecastDetailState.ContentState.Success(
                        forecast = result.data.forecast,
                        staleHoursAgo = if (result.data.isStale) {
                            Duration.between(result.data.forecast.fetchedAt, Instant.now()).toHours()
                        } else {
                            null
                        },
                    ),
                )
            }

            is AppResult.Failure -> reduce {
                copy(content = ForecastDetailState.ContentState.Failure(result.error))
            }
        }
    }
}
