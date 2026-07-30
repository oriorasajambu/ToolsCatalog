package com.minion.scaffold.feature.qrcreate.presentation.url

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.url.model.UrlViolationReason

/**
 * What the link authoring screen renders.
 *
 * One field, so one nullable [violation] rather than the list the multi-field screens carry.
 * [payload] is non-null only between a successful **Generate** and the next edit.
 */
internal data class UrlCreateState(
    val link: String = "",
    val violation: UrlViolationReason? = null,
    val payload: String? = null,
    val exporting: Boolean = false,
    val editing: Boolean = false,
    val prefillFailed: Boolean = false,
) : UiState

internal sealed interface UrlCreateIntent : UiIntent {

    data class LinkChanged(val value: String) : UrlCreateIntent

    data object GenerateRequested : UrlCreateIntent

    data object CopyPayloadRequested : UrlCreateIntent

    data object ShareImageRequested : UrlCreateIntent

    data object SaveImageRequested : UrlCreateIntent
}
