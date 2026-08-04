package com.minion.scaffold.feature.weather.presentation.home

import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.weather.model.WeatherCondition

/**
 * The weather home screen: the location permission gate and the pinned current-location card
 * (SPEC.md §5/§7). Saved locations below the pinned card are a follow-up (see the plan) — this
 * slice renders the pinned card only.
 */
internal data class WeatherHomeState(
    val permission: PermissionState = PermissionState.Unknown,
    val content: ContentState = ContentState.Loading,
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
    val temperatureCelsius: Double,
    val condition: WeatherCondition,

    /** Hours since this forecast was fetched, only when it's being shown stale after a failed
     *  background refresh (SPEC.md §6) — `null` means freshly fetched, no label. */
    val staleHoursAgo: Long?,
)

/** See `QrScanContract.CameraPermissionState` — same shape, same reasoning, different permission. */
internal enum class PermissionState { Unknown, Granted, Denied, PermanentlyDenied }

internal sealed interface WeatherHomeIntent : UiIntent {

    /** See `QrScanIntent.PermissionResult` for why [shouldShowRationale] is read at the call site. */
    data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean) : WeatherHomeIntent

    data object AppSettingsRequested : WeatherHomeIntent

    /** Retries a GPS fix ([WeatherHomeState.ContentState.NoFix]) or a failed fetch. */
    data object Retry : WeatherHomeIntent

    data object PullToRefresh : WeatherHomeIntent

    data object CardClicked : WeatherHomeIntent
}

internal sealed interface WeatherHomeEffect : UiEffect {
    data object OpenAppSettings : WeatherHomeEffect
    data class NavigateToDetail(val locationId: String) : WeatherHomeEffect
}
