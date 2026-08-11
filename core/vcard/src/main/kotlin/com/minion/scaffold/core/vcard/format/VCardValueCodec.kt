package com.minion.scaffold.core.vcard.format

/**
 * Escaping, unescaping and unfolding for vCard 3.0 values (RFC 2426 §5).
 *
 * Three rules, each of which quietly corrupts a card when skipped:
 *
 * **`\`, `;`, `,` and newlines are escaped.** An organisation called `Smith, Jones & Co` written
 * unescaped becomes two values, and the contacts app shows `Smith`.
 *
 * **A colon is not escaped.** Only the *first* colon on a line separates the property name from its
 * value, so a job title of `Engineer: Platform` needs no escaping and must not get any — escaping
 * it produces a literal backslash in the contact.
 *
 * **Folded lines are joined on read.** The spec wraps content lines past 75 octets with a line break
 * and one space; a reader that does not unfold sees a truncated value and a mystery property.
 */
internal object VCardValueCodec {

    private const val ESCAPE = '\\'
    private val SPECIAL_CHARACTERS = setOf(ESCAPE, ';', ',')

    /** Removes a line break followed by one space or tab — the spec's folding. */
    private val FOLD = Regex("\\r?\\n[ \\t]")

    /**
     * A value with the special characters and newlines escaped, ready to place after a property name.
     *
     * @param value The raw value to escape.
     * @return The escaped value.
     */
    fun encode(value: String): String = buildString {
        for (character in value) {
            when {
                character in SPECIAL_CHARACTERS -> append(ESCAPE).append(character)
                character == '\n' -> append(ESCAPE).append('n')
                // A lone CR would be a line break inside a value with no meaning; drop it and let
                // the following LF become the escape sequence.
                character == '\r' -> Unit
                else -> append(character)
            }
        }
    }

    /**
     * The inverse of [encode]: resolves escape sequences back to their characters.
     *
     * @param value An escaped value from a payload.
     * @return The decoded value.
     */
    fun decode(value: String): String = buildString {
        var escaped = false

        for (character in value) {
            when {
                escaped -> {
                    // `\n` and `\N` are both a newline; anything else escaped is itself.
                    append(if (character == 'n' || character == 'N') '\n' else character)
                    escaped = false
                }

                character == ESCAPE -> escaped = true
                else -> append(character)
            }
        }

        // A trailing lone backslash is malformed input; keeping it loses less than dropping it.
        if (escaped) append(ESCAPE)
    }

    /**
     * Splits on [delimiter], ignoring escaped occurrences and leaving the escapes for [decode].
     *
     * `String.split` cannot do this, which is the whole reason this exists: it would cut a family
     * name in half at its first escaped semicolon.
     *
     * @param value     The string to split, escapes intact.
     * @param delimiter The character to split on when it is not escaped.
     * @return The parts between unescaped delimiters, escapes still in place.
     */
    fun splitUnescaped(value: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false

        for (character in value) {
            when {
                escaped -> {
                    current.append(ESCAPE).append(character)
                    escaped = false
                }

                character == ESCAPE -> escaped = true
                character == delimiter -> {
                    parts += current.toString()
                    current.clear()
                }

                else -> current.append(character)
            }
        }

        if (escaped) current.append(ESCAPE)
        parts += current.toString()
        return parts
    }

    /**
     * Joins folded continuation lines back onto the line they belong to.
     *
     * @param text The raw payload text, possibly containing folded lines.
     * @return The text with the spec's line folding removed.
     */
    fun unfold(text: String): String = text.replace(FOLD, "")
}
