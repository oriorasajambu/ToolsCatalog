package com.minion.scaffold.core.wifi.format

/**
 * Escaping and quoting for the values inside a Wi-Fi payload.
 *
 * The two rules that matter, and that a naive implementation gets wrong:
 *
 * **Special characters are backslash-escaped.** `\`, `;`, `,`, `:` and `"` all mean something to
 * the reader. An SSID of `Joe's Café; Guest` written unescaped ends the field early, and the code
 * either fails to scan or joins a network called `Joe's Café`.
 *
 * **A value that looks like hexadecimal is wrapped in quotes.** A reader is entitled to treat a
 * bare `S:ABCDEF` as six hex digits describing three bytes, so an SSID that happens to spell
 * itself in `0-9A-F` has to say it is literal text. `S:"ABCDEF"` does that.
 */
internal object WifiValueCodec {

    private const val ESCAPE = '\\'
    private const val QUOTE = '"'
    private val SPECIAL_CHARACTERS = setOf(ESCAPE, ';', ',', ':', QUOTE)
    private val HEXADECIMAL = Regex("[0-9A-Fa-f]+")

    /**
     * A value as it appears in a payload: escaped, and quoted if it would read as hexadecimal.
     *
     * @param value The raw field value, e.g. an SSID or passphrase.
     * @return The encoded value ready to place after a `K:` key.
     */
    fun encode(value: String): String {
        val escaped = buildString {
            for (character in value) {
                if (character in SPECIAL_CHARACTERS) append(ESCAPE)
                append(character)
            }
        }

        // The quotes are structure, not content, so they go outside the escaping. A hex value
        // contains no quotes of its own, so there is nothing to confuse.
        return if (value.isNotEmpty() && HEXADECIMAL.matches(value)) "$QUOTE$escaped$QUOTE"
        else escaped
    }

    /**
     * The inverse of [encode].
     *
     * @param value An encoded field value from a payload.
     * @return The decoded value with quoting and escapes removed.
     */
    fun decode(value: String): String {
        // An unescaped quote at both ends is the structural quoting; an escaped one is content
        // and still begins with a backslash at this point, so the two cannot be confused.
        val unquoted = if (value.length >= 2 && value.first() == QUOTE && value.last() == QUOTE) {
            value.substring(1, value.length - 1)
        } else {
            value
        }

        return buildString {
            var escaped = false
            for (character in unquoted) {
                when {
                    escaped -> {
                        append(character)
                        escaped = false
                    }

                    character == ESCAPE -> escaped = true
                    else -> append(character)
                }
            }
            // A trailing lone backslash is malformed input; keeping it loses less than dropping it.
            if (escaped) append(ESCAPE)
        }
    }

    /**
     * Splits on [delimiter], ignoring escaped occurrences, leaving the escapes in place for
     * [decode] to remove afterwards.
     *
     * `String.split` cannot do this, and that is the whole reason this function exists: it would
     * cut an SSID in half at its first escaped semicolon.
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
     * Splits `K:V` at its first unescaped colon, or null when there is not one.
     *
     * @param field A single `key:value` field from a payload.
     * @return The key-to-value pair, or `null` if [field] has no unescaped colon.
     */
    fun splitKeyValue(field: String): Pair<String, String>? {
        var escaped = false

        for ((index, character) in field.withIndex()) {
            when {
                escaped -> escaped = false
                character == ESCAPE -> escaped = true
                character == ':' -> return field.take(index) to field.substring(index + 1)
            }
        }

        return null
    }
}
