package com.minion.scaffold.core.emv.parser

import java.util.Locale

/**
 * CRC-16/CCITT-FALSE, the checksum EMVCo specifies for tag `63`.
 *
 * Polynomial `0x1021`, initial value `0xFFFF`, no input or output reflection, no final XOR. The
 * name is worth being precise about: there are at least five widely used CRC-16 variants sharing
 * the `0x1021` polynomial — XMODEM starts from `0x0000`, KERMIT reflects both directions — and
 * picking the wrong one produces a plausible-looking four-digit checksum that never matches.
 *
 * Verified against the standard check vector: `"123456789"` produces `0x29B1`.
 */
internal object EmvCrc16 {

    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF
    private const val WIDTH_MASK = 0xFFFF
    private const val HIGH_BIT = 0x8000
    private const val BYTE_MASK = 0xFF
    private const val BYTE_SHIFT = 8

    /**
     * The checksum of [data], as four uppercase hexadecimal characters.
     *
     * [data] is the whole payload up to *and including* the `6304` header of the checksum tag
     * itself — the checksum covers its own tag and length, and omitting them is the most common
     * way an implementation ends up computing a consistent but wrong value.
     *
     * Encoded as UTF-8. EMV payloads are ASCII in practice, where the choice is immaterial; it
     * matters only if a merchant name carries a non-ASCII character, and UTF-8 is what such a
     * payload would have been generated from.
     *
     * @param data The payload up to and including the `6304` header of the checksum tag.
     * @return The checksum as four uppercase hexadecimal characters, e.g. `"29B1"`.
     */
    fun compute(data: String): String {
        var crc = INITIAL_VALUE
        for (byte in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and BYTE_MASK) shl BYTE_SHIFT)
            repeat(Byte.SIZE_BITS) {
                crc = if (crc and HIGH_BIT != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and WIDTH_MASK
                } else {
                    (crc shl 1) and WIDTH_MASK
                }
            }
        }
        // Locale.ROOT: the default locale formats hexadecimal digits with its own numerals in a
        // handful of locales, which would silently corrupt the comparison on those devices.
        return String.format(Locale.ROOT, "%04X", crc)
    }
}
