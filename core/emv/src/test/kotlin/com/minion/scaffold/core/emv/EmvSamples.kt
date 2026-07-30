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
