package com.minion.scaffold.feature.weather.presentation.search

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.weather.model.LocationSearchResult
import com.minion.scaffold.feature.weather.domain.AddSavedLocationUseCase
import com.minion.scaffold.feature.weather.domain.ObserveSavedLocationsUseCase
import com.minion.scaffold.feature.weather.domain.SearchLocationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
internal class LocationSearchViewModel @Inject constructor(
    private val searchLocations: SearchLocationsUseCase,
    private val addSavedLocation: AddSavedLocationUseCase,
    observeSavedLocations: ObserveSavedLocationsUseCase,
) : MviViewModel<LocationSearchState, LocationSearchIntent, LocationSearchEffect>(LocationSearchState()) {

    /**
     * Keystrokes go here rather than straight into a search.
     *
     * Typing "Jakarta" is seven state changes; without the debounce below it is also seven HTTP
     * requests, six of which are for prefixes the user was never asking about — and their
     * responses can land out of order and overwrite the one that matters.
     */
    private val queryInput = MutableStateFlow("")

    init {
        queryInput
            .debounce(DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .onEach(::runSearch)
            .launchIn(viewModelScope)

        observeSavedLocations()
            .onEach { saved -> reduce { copy(savedIds = saved.mapTo(mutableSetOf()) { it.id }) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LocationSearchIntent) {
        when (intent) {
            is LocationSearchIntent.QueryChanged -> {
                // State updates immediately so the field stays responsive; only the *search* waits.
                reduce { copy(query = intent.query) }
                queryInput.value = intent.query
            }

            is LocationSearchIntent.ResultSelected -> add(intent.result)

            LocationSearchIntent.Retry -> viewModelScope.launch { runSearch(currentState.query) }
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.trim().length < SearchLocationsUseCase.MIN_QUERY_LENGTH) {
            reduce { copy(content = LocationSearchState.ContentState.Idle) }
            return
        }

        reduce { copy(content = LocationSearchState.ContentState.Searching) }

        val content = when (val result = searchLocations(query)) {
            is AppResult.Success -> if (result.data.isEmpty()) {
                LocationSearchState.ContentState.Empty
            } else {
                LocationSearchState.ContentState.Results(result.data)
            }

            is AppResult.Failure -> LocationSearchState.ContentState.Failure(result.error)
        }

        // Dropped if the user kept typing while this was in flight — otherwise a slow response for
        // "Jak" lands after the one for "Jakarta" and replaces the right list with a stale one.
        if (currentState.query == query) reduce { copy(content = content) }
    }

    private fun add(result: LocationSearchResult) = viewModelScope.launch {
        addSavedLocation(result.toLocation())
        emitEffect(LocationSearchEffect.LocationAdded(result.name))
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 350L
    }
}
