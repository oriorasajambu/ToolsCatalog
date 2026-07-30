package com.minion.scaffold.core.url.format

/**
 * The little that a URL payload's shape amounts to, shared by the writer and the reader so the two
 * cannot disagree about what counts as a link.
 *
 * Deliberately **not** `java.net.URI`. `URI` rejects any non-ASCII character outright, which would
 * make the scanner report a perfectly good `https://example.com/café` as an unrecognised code — and
 * refuse to write one it would happily read. A QR carries UTF-8; the reader has to cope.
 */
internal object UrlFormat {

    const val SCHEME_SEPARATOR = "://"
    const val DEFAULT_SCHEME = "https"

    val SUPPORTED_SCHEMES = listOf("http", DEFAULT_SCHEME)

    /**
     * An explicit scheme at the start of [value], or null when there is not one.
     *
     * A dot is excluded from the scheme's characters even though RFC 3986 permits it, so that
     * `example.com:8080` reads as a host and port rather than as a scheme called `example.com`.
     * No scheme in practical use contains a dot, and the alternative misreports the commoner case.
     */
    fun schemeOf(value: String): String? =
        EXPLICIT_SCHEME.find(value)?.groupValues?.get(1)?.lowercase()

    /** The authority between the scheme and the first `/`, `?` or `#`. */
    fun hostOf(value: String): String {
        val separatorIndex = value.indexOf(SCHEME_SEPARATOR)
        if (separatorIndex < 0) return ""

        return value
            .drop(separatorIndex + SCHEME_SEPARATOR.length)
            .takeWhile { it != '/' && it != '?' && it != '#' }
    }

    private val EXPLICIT_SCHEME = Regex("""^([A-Za-z][A-Za-z0-9+\-]*):""")
}
