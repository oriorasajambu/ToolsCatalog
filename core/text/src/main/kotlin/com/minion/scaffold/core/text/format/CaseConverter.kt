package com.minion.scaffold.core.text.format

import java.util.Locale

/**
 * Converts between the identifier casings a developer actually types.
 *
 * The real work is tokenising, and it is why this is not a `replace`: `getUserID`, `get_user_id`
 * and `get-user-id` all name the same three words, so they must all break into the same tokens
 * before being rejoined. That means splitting on spaces, `_` and `-` **and** on the camelCase
 * boundaries inside a run of letters — including the `User`→`ID` boundary, where an acronym meets a
 * word.
 */
internal object CaseConverter {

    fun toCamel(input: String): String {
        val words = tokenise(input)
        if (words.isEmpty()) return ""

        return words.first().lowercase(Locale.ROOT) +
            words.drop(1).joinToString(separator = "") { it.capitaliseAscii() }
    }

    fun toSnake(input: String): String = tokenise(input).joinToString(separator = "_") {
        it.lowercase(Locale.ROOT)
    }

    fun toKebab(input: String): String = tokenise(input).joinToString(separator = "-") {
        it.lowercase(Locale.ROOT)
    }

    /**
     * Splits [input] into words on separators and camelCase boundaries.
     *
     * Locale.ROOT throughout: a Turkish device lower-casing `I` to a dotless `ı` would corrupt an
     * identifier, which is machine text, not prose.
     */
    private fun tokenise(input: String): List<String> = input
        .replace(SEPARATORS, " ")
        .let { CAMEL_BOUNDARY.replace(it, "$1 $2") }
        .let { ACRONYM_BOUNDARY.replace(it, "$1 $2") }
        .split(" ")
        .filter { it.isNotBlank() }

    private fun String.capitaliseAscii(): String =
        replaceFirstChar { it.uppercase(Locale.ROOT) }

    private val SEPARATORS = Regex("[_\\-\\s]+")

    /** `aB` → `a B`: a lower or digit followed by an upper. */
    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")

    /** `IDCard` → `ID Card`: an acronym meeting a word. */
    private val ACRONYM_BOUNDARY = Regex("([A-Z]+)([A-Z][a-z])")
}
