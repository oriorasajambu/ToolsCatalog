package com.minion.scaffold.feature.weather.presentation.search

import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.LocationSearchResult

/**
 * The add-location screen (SPEC.md §7.4): type-ahead against the geocoding host, tap a hit to save
 * it.
 */
internal data class LocationSearchState(
    val query: String = "",
    val content: ContentState = ContentState.Idle,

    /**
     * Ids already in the saved list, so a hit the user has added can be shown as such instead of
     * offering to add it twice.
     *
     * Kept as raw ids rather than baked into each result: it arrives from a different source (the
     * saved-locations flow) at a different time than the search results do, and folding the two
     * together in state would mean re-deriving every result each time either one changes.
     */
    val savedIds: Set<String> = emptySet(),
) : UiState {

    sealed interface ContentState {

        /** Nothing typed yet, or the query is still below the search floor. */
        data object Idle : ContentState

        data object Searching : ContentState

        data class Results(val results: List<LocationSearchResult>) : ContentState

        /** The search ran and matched nothing — an answer, not a failure. See SPEC.md §8. */
        data object Empty : ContentState

        data class Failure(val error: DomainError) : ContentState
    }
}

internal sealed interface LocationSearchIntent : UiIntent {
    data class QueryChanged(val query: String) : LocationSearchIntent
    data class ResultSelected(val result: LocationSearchResult) : LocationSearchIntent
    data object Retry : LocationSearchIntent
}

internal sealed interface LocationSearchEffect : UiEffect {

    /** Confirms an add, so the user gets feedback without the screen closing under them — they
     *  are likely adding more than one city in a sitting. */
    data class LocationAdded(val name: String) : LocationSearchEffect
}
