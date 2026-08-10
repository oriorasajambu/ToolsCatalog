package com.minion.scaffold.core.emv

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.QrParseError
import org.junit.Assert.assertEquals

/** Payloads shared across the parser and use-case tests. */
internal object EmvSamples {

    /**
     * A live Indonesian QRIS payload: dynamic, two merchant account templates, IDR 15,000,000.00,
     * checksum `3D58`.
     *
     * Kept verbatim. Reformatting it — even inserting whitespace for readability — changes the
     * checksummed range and breaks every assertion that depends on it.
     */
    const val QRIS_DYNAMIC =
        "000201010212041553919900000019026710019ID.CO.CIMBNIAGA.WWW0118936000220000000282021" +
            "50000081600126050303UMI51450015ID.OR.QRNPG.WWW0215ID00000000001230303UMI5204078053" +
            "03360541115000000.005802ID5912PAK BOS QR 16006Bekasi61051715163043D58"

    /**
     * [QRIS_DYNAMIC] with the merchant name's last character changed.
     *
     * Same length, so the payload still frames identically and still declares checksum `3D58` —
     * only the recomputed value differs. That is what makes it a checksum test rather than a
     * parsing test.
     */
    val QRIS_TAMPERED = QRIS_DYNAMIC.replace("PAK BOS QR 1", "PAK BOS QR 2")

    /**
     * A Saudi payload whose tag `32` is missing its two length digits.
     *
     * The defect that motivated the diagnostic work, kept verbatim because every offset asserted
     * against it is absolute. What happens: the parser reaches tag `32` at offset 12, reads the
     * `00` that opens the nested `0011SA.GOV.SAMA` as the *length*, and so produces a tag 32 that
     * declares nothing. Framing then resumes two characters later at offset 16, where the tag `11`
     * is valid but the length `SA` is not.
     *
     * The value is worth spelling out: the break is reported at 16, the faulty characters are at
     * 18, and the actual damage is at 14 where `51` should have been. That gap between where a
     * parser stops and where a payload is wrong is the whole reason the error carries its last good
     * segment — "tag 32, declared length 00" points straight at it.
     *
     * Inserting `51` at offset 14 makes this frame cleanly as far as tag 57, where a second,
     * unrelated defect waits.
     */
    const val SAMA_MISSING_LENGTH_DIGITS =
        "000201010212320011SA.GOV.SAMA011612345678901234560206VVSSRR030212520412345303682540" +
            "31005502015703105802SA5925merchantNameUpTo25char1236015citynameupto15c611012345678" +
            "9062021296611234567809031231015312345678901234110300054xx0011SA.GOV.SAMA010210020" +
            "80100000063041234640002AR0125merchantNameUpTo25char1230215citynameupto15c6502SA"
}

/** Asserts success and returns the value, so a test can assert on it without casting. */
internal fun <T> EmvParseResult<T>.valueOrFail(): T = when (this) {
    is EmvParseResult.Success -> value
    is EmvParseResult.Failure -> throw AssertionError("expected Success but was $this")
}

/** Asserts the result failed with exactly [expected]. */
internal fun EmvParseResult<*>.assertFailedWith(expected: QrParseError) = when (this) {
    is EmvParseResult.Success -> throw AssertionError("expected Failure but was $this")
    is EmvParseResult.Failure -> assertEquals(expected, error)
}
