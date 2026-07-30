package com.minion.scaffold.feature.texttools.presentation.transform

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.text.model.TextResult
import com.minion.scaffold.core.text.usecase.TransformTextUseCase
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class TextToolsViewModel @Inject constructor(
    private val transformText: TransformTextUseCase,
) : MviViewModel<TextToolsState, TextToolsIntent, TextToolsEffect>(TextToolsState()) {

    override fun onIntent(intent: TextToolsIntent) {
        when (intent) {
            is TextToolsIntent.InputChanged -> reduce {
                copy(input = intent.value).transformed()
            }

            is TextToolsIntent.OperationChanged -> reduce {
                copy(operation = intent.operation).transformed()
            }

            TextToolsIntent.CopyOutputRequested -> currentState.output
                .takeIf { it.isNotEmpty() }
                ?.let { output -> viewModelScope.launch { emitEffect(TextToolsEffect.CopyText(output)) } }
        }
    }

    /**
     * Recomputes the output for the current input and operation.
     *
     * Runs on the state itself rather than in the intent handler, so the input change and the result
     * it produces are one atomic update — the screen never renders a new input against a stale
     * output for a frame. A failed decode clears the output and shows why, rather than leaving the
     * last successful result sitting under a red field as if it were still valid.
     */
    private fun TextToolsState.transformed(): TextToolsState =
        when (val result = transformText(operation, input)) {
            is TextResult.Success -> copy(output = result.output, error = null)
            is TextResult.Failure -> copy(output = "", error = result.reason)
        }
}

/** Every transform, in the order the picker lists them. */
internal val TEXT_OPERATIONS: List<TextOperation> = TextOperation.entries
