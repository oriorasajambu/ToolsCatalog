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
    /** The current search query. */
    val query: String = "",
    /** The mutually exclusive search phase. */
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

    /** The search phase. */
    sealed interface ContentState {

        /** Nothing typed yet, or the query is still below the search floor. */
        data object Idle : ContentState

        /** A search is in flight. */
        data object Searching : ContentState

        /**
         * The search returned matches.
         *
         * @property results The matching locations.
         */
        data class Results(val results: List<LocationSearchResult>) : ContentState

        /** The search ran and matched nothing — an answer, not a failure. See SPEC.md §8. */
        data object Empty : ContentState

        /**
         * The search failed.
         *
         * @property error Why the search could not complete.
         */
        data class Failure(val error: DomainError) : ContentState
    }
}

/** Everything the user can do on the location search screen. */
internal sealed interface LocationSearchIntent : UiIntent {

    /**
     * The search query changed.
     *
     * @property query The new query.
     */
    data class QueryChanged(val query: String) : LocationSearchIntent

    /**
     * A search result was selected to save.
     *
     * @property result The selected location.
     */
    data class ResultSelected(val result: LocationSearchResult) : LocationSearchIntent

    /** Retry a failed search. */
    data object Retry : LocationSearchIntent
}

/** One-shot events from the location search screen. */
internal sealed interface LocationSearchEffect : UiEffect {

    /**
     * Confirms an add, so the user gets feedback without the screen closing under them — they
     * are likely adding more than one city in a sitting.
     *
     * @property name The name of the location that was added.
     */
    data class LocationAdded(val name: String) : LocationSearchEffect
}
