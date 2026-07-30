package com.minion.scaffold.core.text.model

/**
 * How to build a random password.
 *
 * [classes] is the set the user ticked. An empty set has nothing to draw from and a length shorter
 * than the number of classes cannot honour "one of each" — both are rejected rather than fudged.
 */
data class PasswordSpec(
    val length: Int,
    val classes: Set<CharacterClass>,
)

enum class CharacterClass { LOWERCASE, UPPERCASE, DIGITS, SYMBOLS }

/** The outcome of generating a password. */
sealed interface GenerateResult {

    data class Success(val value: String) : GenerateResult

    data class Invalid(val reason: PasswordProblem) : GenerateResult
}

enum class PasswordProblem {

    /** No character class was selected, so there is nothing to draw from. */
    NO_CHARACTER_CLASS,

    /** The length is below one, or below the number of selected classes. */
    LENGTH_TOO_SHORT,
}
