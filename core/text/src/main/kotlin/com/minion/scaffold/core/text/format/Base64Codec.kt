package com.minion.scaffold.core.text.format

import java.util.Base64

/**
 * Base64, writing the standard alphabet and reading anything close to it.
 *
 * Encode is unremarkable — standard alphabet, padded, UTF-8 in. Decode is where the care goes: real
 * Base64 in the wild is as often URL-safe (`-_` for `+/`) and stripped of its `=` padding as it is
 * canonical, so a decoder that only accepts the strict form rejects strings that came from a JWT or
 * a URL. This normalises first, then decodes, and reports failure only on what is genuinely not
 * Base64 at all.
 */
internal object Base64Codec {

    /**
     * The standard, padded Base64 encoding of [input]'s UTF-8 bytes.
     *
     * @param input The text to encode.
     * @return The Base64 string.
     */
    fun encode(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))

    /**
     * The decoded text, or null when the input is not Base64 in any accepted form.
     *
     * @param input A Base64 string, standard or URL-safe, padded or not.
     * @return The decoded UTF-8 text, or `null` when [input] is not Base64.
     */
    fun decode(input: String): String? {
        val normalised = input
            .trim()
            .replace('-', '+')
            .replace('_', '/')
            .filterNot(Char::isWhitespace)

        // Re-pad to a multiple of four. A URL-safe token drops its `=`, and the strict decoder
        // treats a missing pad as corruption rather than inferring it.
        val padded = normalised.padEnd(
            normalised.length + (PAD_TO - normalised.length % PAD_TO) % PAD_TO,
            '=',
        )

        return try {
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private const val PAD_TO = 4
}
