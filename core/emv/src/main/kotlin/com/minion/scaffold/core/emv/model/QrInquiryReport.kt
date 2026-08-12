package com.minion.scaffold.core.emv.model

/**
 * A fully decoded EMV payload: every segment in encounter order, plus the checksum verdict.
 *
 * [payload] is retained because the report is shareable and a reader comparing it against the
 * original QR needs the source string alongside the interpretation.
 *
 * @property payload  The trimmed source payload the report was built from.
 * @property segments Every segment in the order it appeared in [payload].
 * @property crc      The verdict of recomputing the payload's checksum.
 */
data class QrInquiryReport(
    val payload: String,
    val segments: List<EmvSegment>,
    val crc: CrcVerification,
)

/**
 * One segment of the report: the structure the parser recovered, and what the catalog makes of it.
 *
 * Interpretation is kept beside [node] rather than inside [TlvNode] so that the parser stays
 * concerned only with framing. Adding a decoder for a new tag then touches the catalog and
 * nothing else.
 *
 * @property node           The tag-length-value structure the parser recovered.
 * @property interpretation What the catalog decoded [node]'s value into, or [TagInterpretation.None].
 */
data class EmvSegment(
    val node: TlvNode,
    val interpretation: TagInterpretation,
)

/**
 * The result of recomputing the payload's checksum.
 *
 * Both sides are kept, not just the verdict: a mismatch is only actionable if the reader can see
 * what was expected against what the payload actually claims.
 *
 * @property expected The checksum the payload carries in its tag `63`.
 * @property actual   The checksum recomputed over the payload's own bytes.
 */
data class CrcVerification(
    val expected: String,
    val actual: String,
) {
    /**
     * Case-insensitive: the checksum is four hexadecimal characters and issuers are not
     * consistent about their case. `3d58` and `3D58` are the same checksum, and failing one of
     * them would be a false alarm on a perfectly valid payload.
     */
    val passed: Boolean get() = expected.equals(actual, ignoreCase = true)
}
