package com.minion.scaffold.feature.weather.presentation.home

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.weather.domain.GetCurrentLocationForecastUseCase
import com.minion.scaffold.feature.weather.domain.LocationCard
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class WeatherHomeViewModel @Inject constructor(
    private val getCurrentLocationForecast: GetCurrentLocationForecastUseCase,
) : MviViewModel<WeatherHomeState, WeatherHomeIntent, WeatherHomeEffect>(WeatherHomeState()) {

    override fun onIntent(intent: WeatherHomeIntent) {
        when (intent) {
            is WeatherHomeIntent.PermissionResult -> onPermissionResult(intent)

            WeatherHomeIntent.AppSettingsRequested -> emit(WeatherHomeEffect.OpenAppSettings)

            WeatherHomeIntent.Retry -> load(forceRefresh = false)

            WeatherHomeIntent.PullToRefresh -> load(forceRefresh = true)

            WeatherHomeIntent.CardClicked -> {
                val card = (currentState.content as? WeatherHomeState.ContentState.Success)?.card
                    ?: return
                emit(WeatherHomeEffect.NavigateToDetail(card.locationId))
            }
        }
    }

    /**
     * Loads once, the moment permission first becomes [PermissionState.Granted] — not on every
     * result, or a re-check on resume (SPEC.md §5's "refreshed each time the screen is opened" is
     * covered separately by resume re-checks re-emitting [WeatherHomeIntent.PermissionResult] with
     * `granted = true`, which this treats as a pull-to-refresh-strength no-op below).
     */
    private fun onPermissionResult(result: WeatherHomeIntent.PermissionResult) {
        val previous = currentState.permission
        val newState = result.toPermissionState()
        reduce { copy(permission = newState) }

        if (newState == PermissionState.Granted) load(forceRefresh = false)
    }

    private fun load(forceRefresh: Boolean) = viewModelScope.launch {
        // Only shows the initial spinner. A pull-to-refresh or a resume re-check on an already
        // populated card must not blank out content the user is currently looking at.
        if (currentState.content !is WeatherHomeState.ContentState.Success) {
            reduce { copy(content = WeatherHomeState.ContentState.Loading) }
        }

        when (val outcome = getCurrentLocationForecast(forceRefresh)) {
            is LocationFixOutcome.Found -> reduce {
                copy(content = WeatherHomeState.ContentState.Success(outcome.card.toUi()))
            }

            LocationFixOutcome.NoFixAvailable -> reduce {
                copy(content = WeatherHomeState.ContentState.NoFix)
            }

            is LocationFixOutcome.Failed -> reduce {
                copy(content = WeatherHomeState.ContentState.Failure(outcome.error))
            }
        }
    }

    private fun LocationCard.toUi(): LocationCardUi = LocationCardUi(
        locationId = locationId,
        displayName = displayName,
        temperatureCelsius = result.forecast.current.temperature,
        condition = result.forecast.current.condition,
        staleHoursAgo = if (result.isStale) {
            Duration.between(result.forecast.fetchedAt, Instant.now()).toHours()
        } else {
            null
        },
    )

    /** See `QrScanViewModel`'s identically-named function — same reasoning, same permission gate. */
    private fun WeatherHomeIntent.PermissionResult.toPermissionState(): PermissionState = when {
        granted -> PermissionState.Granted
        shouldShowRationale -> PermissionState.Denied
        else -> PermissionState.PermanentlyDenied
    }

    private fun emit(effect: WeatherHomeEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }
}
