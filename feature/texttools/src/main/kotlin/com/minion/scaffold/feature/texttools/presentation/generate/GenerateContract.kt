package com.minion.scaffold.feature.texttools.presentation.generate

import com.minion.scaffold.core.common.mvi.UiEffect
import com.minion.scaffold.core.common.mvi.UiIntent
import com.minion.scaffold.core.common.mvi.UiState
import com.minion.scaffold.core.text.model.CharacterClass
import com.minion.scaffold.core.text.model.PasswordProblem

/** Which kind of value the generator makes. */
internal enum class GenerateKind {

    /** A random version-4 UUID. */
    UUID,

    /** A random password matching the selected classes and length. */
    PASSWORD,

    /** A run of random bytes, as hex. */
    RANDOM_HEX,
}

/**
 * What the generator screen renders.
 *
 * Unlike a transform, generating is a deliberate act — the output appears only after **Generate**,
 * and does not react to the options changing. Changing an option after generating clears the
 * output, so a shown value always matches the options above it.
 *
 * @property kind            Which kind of value to generate.
 * @property passwordLength  The requested password length.
 * @property passwordClasses The character classes a password may draw from.
 * @property hexByteCount    How many random bytes to generate for hex.
 * @property output          The generated value, or `null` before Generate has run.
 * @property problem         Why a password could not be generated, or `null`.
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

/** Everything the user can do on the generator screen. */
internal sealed interface GenerateIntent : UiIntent {

    /**
     * A different generator kind was selected.
     *
     * @property kind The newly selected kind.
     */
    data class KindChanged(val kind: GenerateKind) : GenerateIntent

    /**
     * The password length changed.
     *
     * @property length The new length.
     */
    data class PasswordLengthChanged(val length: Int) : GenerateIntent

    /**
     * A character class was toggled on or off.
     *
     * @property characterClass The class that was toggled.
     */
    data class PasswordClassToggled(val characterClass: CharacterClass) : GenerateIntent

    /**
     * The hex byte count changed.
     *
     * @property bytes The new byte count.
     */
    data class HexByteCountChanged(val bytes: Int) : GenerateIntent

    /** The user asked to generate a value. */
    data object GenerateRequested : GenerateIntent

    /** The user asked to copy the output. */
    data object CopyOutputRequested : GenerateIntent
}

/** One-shot events from the generator screen. */
internal sealed interface GenerateEffect : UiEffect {

    /**
     * Put text on the clipboard.
     *
     * @property text The text to copy.
     */
    data class CopyText(val text: String) : GenerateEffect
}
