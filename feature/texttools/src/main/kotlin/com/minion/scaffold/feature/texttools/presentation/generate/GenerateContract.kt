package com.minion.scaffold.feature.texttools.presentation.generate

import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.text.model.CharacterClass
import com.minion.scaffold.core.text.model.PasswordProblem

/** Which kind of value the generator makes. */
internal enum class GenerateKind { UUID, PASSWORD, RANDOM_HEX }

/**
 * What the generator screen renders.
 *
 * Unlike a transform, generating is a deliberate act — the output appears only after **Generate**,
 * and does not react to the options changing. Changing an option after generating clears the
 * output, so a shown value always matches the options above it.
 */
internal data class GenerateState(
    val kind: GenerateKind = GenerateKind.PASSWORD,
    val passwordLength: Int = DEFAULT_PASSWORD_LENGTH,
    val passwordClasses: Set<CharacterClass> = DEFAULT_CLASSES,
    val hexByteCount: Int = DEFAULT_HEX_BYTES,
    val output: String? = null,
    val problem: PasswordProblem? = null,
) : UiState {

    companion object {
        const val DEFAULT_PASSWORD_LENGTH = 16
        const val DEFAULT_HEX_BYTES = 16
        val DEFAULT_CLASSES = setOf(
            CharacterClass.LOWERCASE,
            CharacterClass.UPPERCASE,
            CharacterClass.DIGITS,
        )
    }
}

internal sealed interface GenerateIntent : UiIntent {

    data class KindChanged(val kind: GenerateKind) : GenerateIntent

    data class PasswordLengthChanged(val length: Int) : GenerateIntent

    data class PasswordClassToggled(val characterClass: CharacterClass) : GenerateIntent

    data class HexByteCountChanged(val bytes: Int) : GenerateIntent

    data object GenerateRequested : GenerateIntent

    data object CopyOutputRequested : GenerateIntent
}

internal sealed interface GenerateEffect : UiEffect {

    data class CopyText(val text: String) : GenerateEffect
}
