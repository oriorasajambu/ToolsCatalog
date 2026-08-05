package com.minion.scaffold.feature.weather.presentation.home

import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit

/**
 * The weather home screen (SPEC.md §5/§7.2): the location permission gate, the pinned
 * current-location card, and the reorderable saved-location list beneath it.
 */
internal data class WeatherHomeState(
    val permission: PermissionState = PermissionState.Unknown,

    /** The pinned GPS card. Separate from [savedCards] because it has phases they do not — it can
     *  be waiting on a fix — and because it can be neither removed nor reordered. */
    val content: ContentState = ContentState.Loading,

    val savedCards: List<SavedCardUi> = emptyList(),

    /**
     * The display unit every temperature in this state has *already* been converted into. The
     * numbers above are render-ready; this is here so the composable knows which suffix to print,
     * not so it can do the conversion itself.
     */
    val unit: WeatherUnit = WeatherUnit.METRIC,
) : UiState {

    /** Mirrors `QrScanContract.ContentState` — mutually exclusive phases, not sibling booleans. */
    sealed interface ContentState {

        /** Fetching for the first time, or the permission dialog hasn't resolved yet. */
        data object Loading : ContentState

        /**
         * Permission granted, but no GPS fix yet (deep indoors, first cold start). SPEC.md §5:
         * shown indefinitely with a manual retry, never treated as a hard failure.
         */
        data object NoFix : ContentState

        data class Success(val card: LocationCardUi) : ContentState

        /** A fix was resolved but the fetch failed with nothing cached — the only real failure. */
        data class Failure(val error: DomainError) : ContentState
    }
}

/**
 * What the pinned card renders. Carries raw values, not formatted text — building the "Updated Xh
 * ago" string is the screen's job (SPEC.md's own convention: only the composable turns data into
 * words, mirroring [DomainError.toMessageRes] in `:core:ui`).
 */
internal data class LocationCardUi(
    val locationId: String,
    val displayName: String,

    /** Already converted into [WeatherHomeState.unit]. */
    val temperature: Double,
    val condition: WeatherCondition,

    /** Hours since this forecast was fetched, only when it's being shown stale after a failed
     *  background refresh (SPEC.md §6) — `null` means freshly fetched, no label. */
    val staleHoursAgo: Long?,
)

/**
 * One saved location's card.
 *
 * Its forecast is a nested phase rather than a nullable field because each card loads
 * independently (SPEC.md §5) — one city failing must not blank out the others, so every card
 * carries its own phase instead of the screen having a single shared one.
 */
internal data class SavedCardUi(
    val locationId: String,
    val displayName: String,
    val forecast: ForecastState,
) {

    sealed interface ForecastState {

        data object Loading : ForecastState

        data class Ready(
            /** Already converted into [WeatherHomeState.unit]. */
            val temperature: Double,
            val condition: WeatherCondition,
            val staleHoursAgo: Long?,
        ) : ForecastState

        /**
         * Deliberately carries no [DomainError]: a saved card is one row in a list with no room
         * for a message, and the user's recourse is the same whatever went wrong — pull to
         * refresh. The detail screen is where the typed error gets shown.
         */
        data object Failed : ForecastState
    }
}

internal sealed interface WeatherHomeIntent : UiIntent {

    /** See `QrScanIntent.PermissionResult` for why [shouldShowRationale] is read at the call site. */
    data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean) : WeatherHomeIntent

    data object AppSettingsRequested : WeatherHomeIntent

    /** Retries a GPS fix ([WeatherHomeState.ContentState.NoFix]) or a failed fetch. */
    data object Retry : WeatherHomeIntent

    data object PullToRefresh : WeatherHomeIntent

    data object CardClicked : WeatherHomeIntent

    data class SavedCardClicked(val locationId: String) : WeatherHomeIntent

    data class SavedCardRemoved(val locationId: String) : WeatherHomeIntent

    /**
     * One step of an in-progress drag. Reorders the list in state only — the write is deferred to
     * [SavedCardOrderCommitted], so a drag across five positions is one database transaction at
     * the end rather than five mid-gesture.
     */
    data class SavedCardMoved(val fromIndex: Int, val toIndex: Int) : WeatherHomeIntent

    /** The drag ended: persist whatever order the list is now in. */
    data object SavedCardOrderCommitted : WeatherHomeIntent

    data object AddLocationClicked : WeatherHomeIntent

    data object SettingsClicked : WeatherHomeIntent
}

internal sealed interface WeatherHomeEffect : UiEffect {
    data object OpenAppSettings : WeatherHomeEffect
    data class NavigateToDetail(val locationId: String) : WeatherHomeEffect
    data object NavigateToSearch : WeatherHomeEffect
    data object NavigateToSettings : WeatherHomeEffect
}
