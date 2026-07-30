package com.minion.scaffold.core.text.format

/**
 * HTML entity escaping, for the five characters that actually break markup.
 *
 * The named table is deliberately tiny — `& < > " '` are the ones that must be escaped to paste
 * text safely into HTML, and the hundreds of others (`&copy;`, `&mdash;`) are typography a text
 * tool has no reason to invent. Decode is broader than encode: it resolves the five names plus any
 * numeric reference, and **leaves an entity it does not know verbatim** rather than dropping it,
 * because a mangled unknown is worse than an unexpanded one.
 */
internal object HtmlEntityCodec {

    // Order matters: the ampersand is escaped first, or it would double-escape the `&` in the
    // entities added after it.
    private val ENCODE = listOf(
        "&" to "&amp;",
        "<" to "&lt;",
        ">" to "&gt;",
        "\"" to "&quot;",
        "'" to "&#39;",
    )

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "#39" to "'",
    )

    fun encode(input: String): String =
        ENCODE.fold(input) { acc, (raw, entity) -> acc.replace(raw, entity) }

    fun decode(input: String): String = ENTITY.replace(input) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(HEX_RADIX)?.toChar()?.toString() ?: match.value

            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.toChar()?.toString() ?: match.value

            else -> NAMED[body] ?: match.value
        }
    }

    private val ENTITY = Regex("&(#x?[0-9A-Fa-f]+|[A-Za-z]+);")
    private const val HEX_RADIX = 16
}
