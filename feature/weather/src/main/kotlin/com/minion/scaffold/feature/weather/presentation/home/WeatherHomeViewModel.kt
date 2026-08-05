package com.minion.scaffold.feature.weather.presentation.home

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.Location
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.core.weather.usecase.ConvertUnitsUseCase
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.GetCurrentLocationForecastUseCase
import com.minion.scaffold.feature.weather.domain.GetForecastUseCase
import com.minion.scaffold.feature.weather.domain.LocationCard
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import com.minion.scaffold.feature.weather.domain.ObserveSavedLocationsUseCase
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import com.minion.scaffold.feature.weather.domain.RemoveSavedLocationUseCase
import com.minion.scaffold.feature.weather.domain.ReorderSavedLocationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class WeatherHomeViewModel @Inject constructor(
    private val getCurrentLocationForecast: GetCurrentLocationForecastUseCase,
    private val getForecast: GetForecastUseCase,
    private val removeSavedLocation: RemoveSavedLocationUseCase,
    private val reorderSavedLocations: ReorderSavedLocationsUseCase,
    private val convertUnits: ConvertUnitsUseCase,
    observeSavedLocations: ObserveSavedLocationsUseCase,
    observeWeatherUnit: ObserveWeatherUnitUseCase,
) : MviViewModel<WeatherHomeState, WeatherHomeIntent, WeatherHomeEffect>(WeatherHomeState()) {

    /**
     * The raw, always-metric data this screen renders from, kept beside the state rather than in
     * it.
     *
     * State holds numbers already converted for display, so flipping the unit toggle has to
     * recompute every one of them — which is only possible if the unconverted originals are still
     * around. Converting in place and writing back into state would lose them after the first flip
     * and turn a second flip into °F → °F-treated-as-°C.
     */
    private var pinnedCard: LocationCard? = null
    private var savedLocations: List<Location> = emptyList()
    private val savedForecasts = mutableMapOf<String, ForecastResult>()
    private val failedLocationIds = mutableSetOf<String>()

    /**
     * Named `displayUnit`, not `unit`: inside `reduce { }` the state is the lambda receiver, so a
     * field called `unit` would be shadowed by [WeatherHomeState.unit] and `copy(unit = unit)`
     * would silently assign the state's own value back to itself.
     */
    private var displayUnit: WeatherUnit = WeatherUnit.METRIC

    init {
        observeSavedLocations()
            .onEach(::onSavedLocationsChanged)
            .launchIn(viewModelScope)

        observeWeatherUnit()
            .onEach { newUnit ->
                displayUnit = newUnit
                render()
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: WeatherHomeIntent) {
        when (intent) {
            is WeatherHomeIntent.PermissionResult -> onPermissionResult(intent)

            WeatherHomeIntent.AppSettingsRequested -> emit(WeatherHomeEffect.OpenAppSettings)

            WeatherHomeIntent.Retry -> loadPinned(forceRefresh = false)

            WeatherHomeIntent.PullToRefresh -> {
                loadPinned(forceRefresh = true)
                loadSavedForecasts(forceRefresh = true)
            }

            WeatherHomeIntent.CardClicked -> {
                val card = (currentState.content as? WeatherHomeState.ContentState.Success)?.card
                    ?: return
                emit(WeatherHomeEffect.NavigateToDetail(card.locationId))
            }

            is WeatherHomeIntent.SavedCardClicked ->
                emit(WeatherHomeEffect.NavigateToDetail(intent.locationId))

            is WeatherHomeIntent.SavedCardRemoved -> viewModelScope.launch {
                removeSavedLocation(intent.locationId)
                // The saved-locations flow re-emits and rebuilds the list; these two maps are not
                // part of that emission, so they would otherwise keep the removed city's forecast
                // alive and hand it straight back if the user re-added the same place.
                savedForecasts.remove(intent.locationId)
                failedLocationIds.remove(intent.locationId)
            }

            is WeatherHomeIntent.SavedCardMoved -> moveSavedCard(intent.fromIndex, intent.toIndex)

            WeatherHomeIntent.SavedCardOrderCommitted -> viewModelScope.launch {
                reorderSavedLocations(savedLocations.map { it.id })
            }

            WeatherHomeIntent.AddLocationClicked -> emit(WeatherHomeEffect.NavigateToSearch)

            WeatherHomeIntent.SettingsClicked -> emit(WeatherHomeEffect.NavigateToSettings)
        }
    }

    private fun onSavedLocationsChanged(locations: List<Location>) {
        savedLocations = locations
        // Forecasts for cities that are gone would otherwise accumulate for the process's lifetime.
        savedForecasts.keys.retainAll(locations.mapTo(mutableSetOf()) { it.id })
        failedLocationIds.retainAll(locations.mapTo(mutableSetOf()) { it.id })
        render()
        loadSavedForecasts(forceRefresh = false)
    }

    /**
     * Reorders the in-memory list only. The write waits for
     * [WeatherHomeIntent.SavedCardOrderCommitted] at the end of the gesture.
     */
    private fun moveSavedCard(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in savedLocations.indices || toIndex !in savedLocations.indices) return

        savedLocations = savedLocations.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        render()
    }

    private fun onPermissionResult(result: WeatherHomeIntent.PermissionResult) {
        val newState = result.toPermissionState()
        reduce { copy(permission = newState) }

        if (newState == PermissionState.Granted) loadPinned(forceRefresh = false)
    }

    private fun loadPinned(forceRefresh: Boolean) = viewModelScope.launch {
        // Only shows the initial spinner. A pull-to-refresh or a resume re-check on an already
        // populated card must not blank out content the user is currently looking at.
        if (currentState.content !is WeatherHomeState.ContentState.Success) {
            reduce { copy(content = WeatherHomeState.ContentState.Loading) }
        }

        when (val outcome = getCurrentLocationForecast(forceRefresh)) {
            is LocationFixOutcome.Found -> {
                pinnedCard = outcome.card
                render()
            }

            LocationFixOutcome.NoFixAvailable -> {
                pinnedCard = null
                reduce { copy(content = WeatherHomeState.ContentState.NoFix) }
            }

            is LocationFixOutcome.Failed -> {
                pinnedCard = null
                reduce { copy(content = WeatherHomeState.ContentState.Failure(outcome.error)) }
            }
        }
    }

    /**
     * Fetches each saved location's forecast in its own coroutine, so one slow or failing city
     * never holds up the rest of the list (SPEC.md §5).
     */
    private fun loadSavedForecasts(forceRefresh: Boolean) {
        savedLocations.forEach { location ->
            if (!forceRefresh && savedForecasts.containsKey(location.id)) return@forEach

            viewModelScope.launch {
                when (val result = getForecast(location.id, location.latitude, location.longitude, forceRefresh)) {
                    is AppResult.Success -> {
                        savedForecasts[location.id] = result.data
                        failedLocationIds.remove(location.id)
                    }

                    is AppResult.Failure -> failedLocationIds.add(location.id)
                }
                render()
            }
        }
    }

    /** Rebuilds every derived, unit-converted part of the state from the raw data above. */
    private fun render() {
        val pinnedContent = pinnedCard
            ?.let { WeatherHomeState.ContentState.Success(it.toUi()) }
            ?: currentState.content

        val cards = savedLocations.map { location ->
            SavedCardUi(
                locationId = location.id,
                displayName = location.name,
                forecast = when {
                    savedForecasts.containsKey(location.id) ->
                        savedForecasts.getValue(location.id).toForecastState()

                    location.id in failedLocationIds -> SavedCardUi.ForecastState.Failed

                    else -> SavedCardUi.ForecastState.Loading
                },
            )
        }

        reduce { copy(content = pinnedContent, savedCards = cards, unit = displayUnit) }
    }

    private fun LocationCard.toUi(): LocationCardUi = LocationCardUi(
        locationId = locationId,
        displayName = displayName,
        temperature = result.forecast.displayTemperature(),
        condition = result.forecast.current.condition,
        staleHoursAgo = result.staleHoursAgo(),
    )

    private fun ForecastResult.toForecastState(): SavedCardUi.ForecastState =
        SavedCardUi.ForecastState.Ready(
            temperature = forecast.displayTemperature(),
            condition = forecast.current.condition,
            staleHoursAgo = staleHoursAgo(),
        )

    /** Metric on disk, the user's choice on screen — the conversion happens here and nowhere else. */
    private fun Forecast.displayTemperature(): Double = convertUnits(this, displayUnit).current.temperature

    private fun ForecastResult.staleHoursAgo(): Long? = if (isStale) {
        Duration.between(forecast.fetchedAt, Instant.now()).toHours()
    } else {
        null
    }

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
