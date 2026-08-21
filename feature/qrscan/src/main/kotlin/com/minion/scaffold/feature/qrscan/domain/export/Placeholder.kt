package com.minion.scaffold.feature.qrscan.domain.export

/**
 * One substitution a schema template can ask for.
 *
 * Two kinds, and the difference decides what happens when one comes back empty. A [Named] value the
 * app does not recognise is a mistake — a typo, or a name an update removed — and is reported. A
 * [TagPath] that finds nothing is ordinary: `tag:62.05` on a domestic code is a field that code
 * simply does not carry, and the honest answer is null.
 */
internal sealed interface Placeholder {

    /** The text between the braces, as written. */
    val token: String

    /**
     * A value the app derives, named in [PlaceholderVocabulary].
     *
     * @property token The name, e.g. `merchant_pan`.
     */
    data class Named(override val token: String) : Placeholder

    /**
     * A raw address into the payload, e.g. `tag:26.01`.
     *
     * Prefer a [Named] value where one exists. The primary merchant account sits at tag `26` on a
     * domestic code and tag `32` on a cross-border one, so a template written with `tag:26.01`
     * silently yields nothing on half the codes it will meet, while `merchant_pan` is right on
     * both. The escape hatch is for the fields nobody has named yet.
     *
     * @property token    The whole token including the prefix, e.g. `tag:26.01`.
     * @property segments The two-character tags, outermost first: `["26", "01"]`.
     */
    data class TagPath(
        override val token: String,
        val segments: List<String>,
    ) : Placeholder
}

/**
 * Reads a placeholder token, or says why it is not one.
 *
 * The `tag:` prefix namespaces the raw form so that no name added to the vocabulary later can ever
 * collide with a path — without it, a future value called `tag` or one starting with a digit would
 * be ambiguous against `26.01`.
 */
internal object PlaceholderSyntax {

    /** Opens a placeholder. */
    const val OPEN = "{{"

    /** Closes a placeholder. */
    const val CLOSE = "}}"

    /** Marks the raw escape hatch. */
    const val TAG_PREFIX = "tag:"

    /**
     * Every placeholder in [text], in the order they appear.
     *
     * Both braces are escaped at both ends, and the closing pair is the one that matters: an
     * unescaped `}}` compiles fine on the JVM — so every unit test passes — and is rejected
     * outright by Android's ICU-backed engine, which throws `PatternSyntaxException` the first
     * time anything touches this object. The failure therefore cannot appear anywhere but on a
     * device.
     */
    private val TOKEN = Regex("""\{\{([^{}]*)\}\}""")

    /** A named value: lower case, digits and underscores. */
    private val NAME = Regex("""[a-z0-9_]+""")

    /** A tag path: two-character segments separated by dots. */
    private val TAG_SEGMENTS = Regex("""[0-9A-Za-z]{2}(\.[0-9A-Za-z]{2})*""")

    /** The token this string is entirely made of, or null when it is not one placeholder alone. */
    fun soleToken(text: String): String? {
        val match = TOKEN.matchEntire(text.trim()) ?: return null
        return match.groupValues[1].trim()
    }

    /** Every token in [text], in order. Empty when there are none. */
    fun tokensIn(text: String): List<String> =
        TOKEN.findAll(text).map { it.groupValues[1].trim() }.toList()

    /** Replaces each token in [text] using [substitute]. */
    fun interpolate(text: String, substitute: (String) -> String): String =
        TOKEN.replace(text) { match -> substitute(match.groupValues[1].trim()) }

    /**
     * Reads [token] into a [Placeholder], or null when it is malformed.
     *
     * Malformed means the shape is wrong — empty braces, capitals in a name, a tag path with a
     * three-character segment. Whether a well-formed *name* actually exists is a separate question,
     * and one only [PlaceholderVocabulary] can answer.
     */
    fun parse(token: String): Placeholder? = when {
        token.startsWith(TAG_PREFIX) -> {
            val path = token.removePrefix(TAG_PREFIX)
            if (TAG_SEGMENTS.matches(path)) {
                Placeholder.TagPath(token = token, segments = path.split('.'))
            } else {
                null
            }
        }

        NAME.matches(token) -> Placeholder.Named(token)

        else -> null
    }
}
