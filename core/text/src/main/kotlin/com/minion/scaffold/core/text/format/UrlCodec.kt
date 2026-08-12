package com.minion.scaffold.core.text.format

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Percent-encoding for a URL *component*.
 *
 * The footgun `URLEncoder` sets: it emits `application/x-www-form-urlencoded`, where a space becomes
 * `+`. That is correct for a form body and wrong for a path or query value, where a bare `+` is a
 * literal plus and a space is `%20`. So the `+` is rewritten to `%20` on the way out — and on the
 * way in, both a `+` and a `%20` are read as a space, because input arrives in either convention.
 */
internal object UrlCodec {

    /**
     * [input] percent-encoded for use as a URL path or query component (space becomes `%20`).
     *
     * @param input The text to encode.
     * @return The percent-encoded value.
     */
    fun encode(input: String): String =
        URLEncoder.encode(input, Charsets.UTF_8.name())
            .replace("+", "%20")

    /**
     * The decoded text, or null when the input has a malformed `%` escape.
     *
     * @param input A percent-encoded value; both `+` and `%20` are read as a space.
     * @return The decoded text, or `null` on a malformed `%` escape.
     */
    fun decode(input: String): String? = try {
        // `+` is left for URLDecoder to turn into a space, which is what a form-encoded input means;
        // a component-encoded input has no bare `+`, so nothing is lost either way.
        URLDecoder.decode(input, Charsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        // A stray `%` with no two hex digits after it.
        null
    }
}
