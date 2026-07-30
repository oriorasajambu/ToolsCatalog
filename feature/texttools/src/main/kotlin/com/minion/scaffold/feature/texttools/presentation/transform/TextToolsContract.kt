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
 */
internal data class TextToolsState(
    val input: String = "",
    val operation: TextOperation = TextOperation.BASE64_ENCODE,
    val output: String = "",
    val error: TextError? = null,
) : UiState

internal sealed interface TextToolsIntent : UiIntent {

    data class InputChanged(val value: String) : TextToolsIntent

    data class OperationChanged(val operation: TextOperation) : TextToolsIntent

    data object CopyOutputRequested : TextToolsIntent
}

internal sealed interface TextToolsEffect : com.minion.scaffold.core.common.mvi.UiEffect {

    data class CopyText(val text: String) : TextToolsEffect
}
