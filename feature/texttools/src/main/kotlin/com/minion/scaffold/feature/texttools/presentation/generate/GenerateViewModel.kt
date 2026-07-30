package com.minion.scaffold.feature.texttools.presentation.generate

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.text.model.GenerateResult
import com.minion.scaffold.core.text.model.PasswordSpec
import com.minion.scaffold.core.text.usecase.GenerateTextUseCase
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class GenerateViewModel @Inject constructor(
    private val generateText: GenerateTextUseCase,
) : MviViewModel<GenerateState, GenerateIntent, GenerateEffect>(GenerateState()) {

    override fun onIntent(intent: GenerateIntent) {
        when (intent) {
            // Every option change clears the output, so a shown value is never stale against the
            // controls above it — the same rule the QR create screens follow.
            is GenerateIntent.KindChanged -> reduce {
                copy(kind = intent.kind, output = null, problem = null)
            }

            is GenerateIntent.PasswordLengthChanged -> reduce {
                copy(passwordLength = intent.length, output = null, problem = null)
            }

            is GenerateIntent.PasswordClassToggled -> reduce {
                copy(passwordClasses = passwordClasses.toggled(intent.characterClass), output = null, problem = null)
            }

            is GenerateIntent.HexByteCountChanged -> reduce {
                copy(hexByteCount = intent.bytes, output = null, problem = null)
            }

            GenerateIntent.GenerateRequested -> generate()

            GenerateIntent.CopyOutputRequested -> currentState.output
                ?.let { output -> viewModelScope.launch { emitEffect(GenerateEffect.CopyText(output)) } }
        }
    }

    private fun generate() {
        when (currentState.kind) {
            GenerateKind.UUID -> reduce { copy(output = generateText.uuid(), problem = null) }

            GenerateKind.RANDOM_HEX -> reduce {
                copy(output = generateText.randomHex(hexByteCount), problem = null)
            }

            GenerateKind.PASSWORD -> {
                val spec = PasswordSpec(currentState.passwordLength, currentState.passwordClasses)
                when (val result = generateText.password(spec)) {
                    is GenerateResult.Success -> reduce {
                        copy(output = result.value, problem = null)
                    }

                    is GenerateResult.Invalid -> reduce {
                        copy(output = null, problem = result.reason)
                    }
                }
            }
        }
    }

    private fun <T> Set<T>.toggled(item: T): Set<T> =
        if (item in this) this - item else this + item
}
