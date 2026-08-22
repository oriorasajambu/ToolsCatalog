package com.minion.scaffold.core.text.model

/**
 * How to build a random password.
 *
 * [classes] is the set the user ticked. An empty set has nothing to draw from and a length shorter
 * than the number of classes cannot honour "one of each" — both are rejected rather than fudged.
 */
data class PasswordSpec(
    /** How many characters the password should have. */
    val length: Int,
    /** The character classes to draw from. At least one; each is guaranteed present in the output. */
    val classes: Set<CharacterClass>,
)

/** A group of characters a password can draw from. */
enum class CharacterClass { LOWERCASE, UPPERCASE, DIGITS, SYMBOLS }

/** The outcome of generating a password. */
sealed interface GenerateResult {

    /**
     * A password was generated.
     *
     * @property value The generated password.
     */
    data class Success(val value: String) : GenerateResult

    /**
     * The spec could not produce a password.
     *
     * @property reason Why the spec was rejected.
     */
    data class Invalid(val reason: PasswordProblem) : GenerateResult
}

/**
 * Why a password spec could not be honoured.
 *
 * Both cases are the caller asking for something impossible rather than the generator failing, so
 * they arrive as [GenerateResult.Invalid] in the success channel.
 */
enum class PasswordProblem {

    /** No character class was selected, so there is nothing to draw from. */
    NO_CHARACTER_CLASS,

    /** The length is below one, or below the number of selected classes. */
    LENGTH_TOO_SHORT,
}
