package com.minion.scaffold.core.text.format

/**
 * Text ↔ lowercase hexadecimal, over UTF-8 bytes.
 *
 * Decode is strict where Base64's is lenient, because hex has no lenient reading: an odd number of
 * digits describes half a byte, and a non-hex character has no value. Both are failures, not
 * something to paper over.
 */
internal object HexCodec {

    /**
     * [input]'s UTF-8 bytes as lowercase hexadecimal.
     *
     * @param input The text to encode.
     * @return The hex string, two characters per byte.
     */
    fun encode(input: String): String =
        input.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and BYTE_MASK)
        }

    /**
     * The decoded text, or null when [input] is not an even run of hex digits.
     *
     * @param input A hex string, whitespace ignored.
     * @return The decoded UTF-8 text, or `null` for an odd length or a non-hex character.
     */
    fun decode(input: String): String? {
        // Empty is valid — zero bytes decode to the empty string, which is the inverse of encoding
        // it. Only an odd length or a non-hex character is malformed.
        val cleaned = input.trim().filterNot(Char::isWhitespace)
        if (cleaned.length % 2 != 0) return null
        if (!cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null

        val bytes = ByteArray(cleaned.length / 2) { index ->
            cleaned.substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    private const val BYTE_MASK = 0xFF
    private const val HEX_RADIX = 16
}
