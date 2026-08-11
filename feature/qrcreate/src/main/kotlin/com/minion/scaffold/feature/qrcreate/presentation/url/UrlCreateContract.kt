package com.minion.scaffold.feature.qrcreate.presentation.url

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.url.model.UrlViolationReason

/**
 * What the link authoring screen renders.
 *
 * One field, so one nullable [violation] rather than the list the multi-field screens carry.
 * [payload] is non-null only between a successful **Generate** and the next edit.
 *
 * @property link          The typed link.
 * @property violation     Why the link was rejected, or `null` when valid.
 * @property payload       The generated payload, or `null` before Generate or after an edit.
 * @property exporting     Whether an export is in progress.
 * @property editing       Whether the screen opened pre-filled for editing.
 * @property prefillFailed Whether a pre-fill payload could not be parsed.
 */
internal data class UrlCreateState(
    val link: String = "",
    val violation: UrlViolationReason? = null,
    val payload: String? = null,
    val exporting: Boolean = false,
    val editing: Boolean = false,
    val prefillFailed: Boolean = false,
) : UiState

/** Everything the user can do on the link authoring screen. */
internal sealed interface UrlCreateIntent : UiIntent {

    /**
     * The link field changed.
     *
     * @property value The new link text.
     */
    data class LinkChanged(val value: String) : UrlCreateIntent

    /** Generate the QR from the current link. */
    data object GenerateRequested : UrlCreateIntent

    /** Copy the generated payload. */
    data object CopyPayloadRequested : UrlCreateIntent

    /** Share the generated QR image. */
    data object ShareImageRequested : UrlCreateIntent

    /** Save the generated QR image to the gallery. */
    data object SaveImageRequested : UrlCreateIntent
}
