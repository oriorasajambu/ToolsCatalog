package com.minion.scaffold.feature.texttools.presentation.transform

import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.text.model.TextError
import com.minion.scaffold.core.text.model.TextOperation

/**
 * What the transform screen renders.
 *
 * The output re-runs as the input or the operation changes — there is no Generate button, because a
 * transform is cheap and instant and watching the result update as you type is the point of the
 * tool. [error] is set only when a decode fails; a successful transform clears it.
 *
 * @property input     The text being transformed.
 * @property operation The transform currently selected.
 * @property output    The result of applying [operation] to [input].
 * @property error     The decode failure to show, or `null` when the transform succeeded.
 */
internal data class TextToolsState(
    val input: String = "",
    val operation: TextOperation = TextOperation.BASE64_ENCODE,
    val output: String = "",
    val error: TextError? = null,
) : UiState

/** Everything the user can do on the transform screen. */
internal sealed interface TextToolsIntent : UiIntent {

    /**
     * The input text changed.
     *
     * @property value The new input.
     */
    data class InputChanged(val value: String) : TextToolsIntent

    /**
     * A different transform was selected.
     *
     * @property operation The newly selected transform.
     */
    data class OperationChanged(val operation: TextOperation) : TextToolsIntent

    /** The user asked to copy the output. */
    data object CopyOutputRequested : TextToolsIntent
}

/** One-shot events from the transform screen. */
internal sealed interface TextToolsEffect : com.minion.scaffold.core.common.mvi.UiEffect {

    /**
     * Put text on the clipboard.
     *
     * @property text The text to copy.
     */
    data class CopyText(val text: String) : TextToolsEffect
}
