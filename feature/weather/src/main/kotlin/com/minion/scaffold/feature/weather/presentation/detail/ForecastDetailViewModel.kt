package com.minion.scaffold.feature.weather.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.navigation.WeatherDetailRoute
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.core.weather.usecase.ConvertUnitsUseCase
import com.minion.scaffold.feature.weather.data.repository.CURRENT_LOCATION_KEY
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.GetForecastUseCase
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class ForecastDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecast: GetForecastUseCase,
    private val convertUnits: ConvertUnitsUseCase,
    observeWeatherUnit: ObserveWeatherUnitUseCase,
) : MviViewModel<ForecastDetailState, ForecastDetailIntent, ForecastDetailEffect>(ForecastDetailState()) {

    private val locationId: String =
        savedStateHandle.get<String>(WeatherDetailRoute.ARG_LOCATION_ID) ?: CURRENT_LOCATION_KEY

    /**
     * The always-metric forecast, kept beside the state rather than in it — the state's copy has
     * been converted for display, and converting an already-converted forecast a second time is
     * how °F becomes °F-treated-as-°C. See `WeatherHomeViewModel` for the same arrangement.
     */
    private var rawResult: ForecastResult? = null

    /**
     * Named `displayUnit`, not `unit`: inside `reduce { }` the state is the lambda receiver, so a
     * field called `unit` would be shadowed by [ForecastDetailState.unit] and `copy(unit = unit)`
     * would silently assign the state's own value back to itself.
     */
    private var displayUnit: WeatherUnit = WeatherUnit.METRIC

    init {
        observeWeatherUnit()
            .onEach { newUnit ->
                displayUnit = newUnit
                render()
            }
            .launchIn(viewModelScope)

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
            is AppResult.Success -> {
                rawResult = result.data
                render()
            }

            is AppResult.Failure -> {
                rawResult = null
                reduce { copy(content = ForecastDetailState.ContentState.Failure(result.error)) }
            }
        }
    }

    /** Rebuilds the unit-converted state from [rawResult]. */
    private fun render() {
        val result = rawResult ?: run {
            reduce { copy(unit = displayUnit) }
            return
        }

        reduce {
            copy(
                content = ForecastDetailState.ContentState.Success(
                    forecast = convertUnits(result.forecast, displayUnit),
                    staleHoursAgo = if (result.isStale) {
                        Duration.between(result.forecast.fetchedAt, Instant.now()).toHours()
                    } else {
                        null
                    },
                ),
                unit = displayUnit,
            )
        }
    }
}
