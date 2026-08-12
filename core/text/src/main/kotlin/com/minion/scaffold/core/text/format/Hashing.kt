package com.minion.scaffold.core.text.format

import java.security.MessageDigest

/**
 * Cryptographic and legacy digests, as lowercase hex over the input's UTF-8 bytes.
 *
 * MD5 and SHA-1 are here despite being broken for security, because a text tool's job is to compute
 * the digest someone else is asking for — a file checksum, a legacy API signature — not to police
 * which one they need.
 */
internal object Hashing {

    /**
     * The MD5 digest of [input]'s UTF-8 bytes, as lowercase hex.
     *
     * @param input The text to hash.
     * @return The 32-character lowercase hex digest.
     */
    fun md5(input: String): String = digest(input, "MD5")

    /**
     * The SHA-1 digest of [input]'s UTF-8 bytes, as lowercase hex.
     *
     * @param input The text to hash.
     * @return The 40-character lowercase hex digest.
     */
    fun sha1(input: String): String = digest(input, "SHA-1")

    /**
     * The SHA-256 digest of [input]'s UTF-8 bytes, as lowercase hex.
     *
     * @param input The text to hash.
     * @return The 64-character lowercase hex digest.
     */
    fun sha256(input: String): String = digest(input, "SHA-256")

    private fun digest(input: String, algorithm: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private const val BYTE_MASK = 0xFF
}
