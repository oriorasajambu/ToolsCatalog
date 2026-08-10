package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.EmvSamples
import com.minion.scaffold.core.emv.assertFailedWith
import com.minion.scaffold.core.emv.model.PayloadSpan
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.core.emv.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ParseEmvPayloadUseCaseTest {

    private val parse = ParseEmvPayloadUseCase()

    @Test
    fun `verifies the checksum of a live payload`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals("3D58", report.crc.expected)
        assertEquals("3D58", report.crc.actual)
        assertTrue(report.crc.passed)
    }

    @Test
    fun `reports every segment`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(13, report.segments.size)
    }

    @Test
    fun `decodes the payload format indicator`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(TagInterpretation.PayloadVersion("1"), report.interpretationOf("00"))
    }

    @Test
    fun `decodes the point of initiation method`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(
            TagInterpretation.InitiationMethod(PointOfInitiationMethod.DYNAMIC),
            report.interpretationOf("01"),
        )
    }

    @Test
    fun `decodes the merchant category code`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(
            TagInterpretation.MerchantCategory(
                code = "0780",
                name = "Landscaping and Horticultural Services",
            ),
            report.interpretationOf("52"),
        )
    }

    @Test
    fun `decodes the transaction currency`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(
            TagInterpretation.Currency(
                numericCode = "360",
                alphaCode = "IDR",
                name = "Indonesian Rupiah",
            ),
            report.interpretationOf("53"),
        )
    }

    /** An unlisted code is reported verbatim rather than guessed at. */
    @Test
    fun `leaves an unknown merchant category code undecoded`() {
        val report = parse(payloadWithMerchantCategory("9998")).valueOrFail()

        assertEquals(
            TagInterpretation.MerchantCategory(code = "9998", name = null),
            report.interpretationOf("52"),
        )
    }

    @Test
    fun `leaves text-bearing tags uninterpreted`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(TagInterpretation.None, report.interpretationOf("59"))
        assertEquals(TagInterpretation.None, report.interpretationOf("60"))
        assertEquals(TagInterpretation.None, report.interpretationOf("54"))
    }

    /**
     * A mismatched checksum is a finding, not a parse failure.
     *
     * Rejecting the payload here would hide the single most useful thing this tool can tell
     * someone holding a QR that a terminal refused.
     */
    @Test
    fun `reports a tampered payload as a checksum mismatch rather than a failure`() {
        val report = parse(EmvSamples.QRIS_TAMPERED).valueOrFail()

        assertFalse(report.crc.passed)
        assertEquals("3D58", report.crc.expected)
        assertEquals(13, report.segments.size)
        assertEquals("PAK BOS QR 2", report.segments.single { it.node.tag == "59" }.node.rawValue)
    }

    @Test
    fun `accepts a checksum in lower case`() {
        val lowerCase = EmvSamples.QRIS_DYNAMIC.dropLast(4) + "3d58"

        assertTrue(parse(lowerCase).valueOrFail().crc.passed)
    }

    @Test
    fun `trims surrounding whitespace before checksumming`() {
        val padded = "\n  ${EmvSamples.QRIS_DYNAMIC}  \n"

        assertTrue(parse(padded).valueOrFail().crc.passed)
    }

    @Test
    fun `rejects a payload that does not open with the format indicator`() {
        parse("0102126304ABCD").assertFailedWith(
            QrParseError.MissingPayloadFormatIndicator(
                span = PayloadSpan(0, 6),
                foundTag = "01",
            ),
        )
    }

    @Test
    fun `rejects a payload with no checksum segment`() {
        // The span covers the last segment, so the message can name what the payload ends on
        // instead of pointing at nothing past the final character.
        parse("000201010212").assertFailedWith(
            QrParseError.MissingCrc(
                span = PayloadSpan(6, 12),
                foundTag = "01",
                foundLength = 2,
            ),
        )
    }

    @Test
    fun `rejects a checksum segment that is not last`() {
        parse("00020163043D585802ID").assertFailedWith(
            QrParseError.MissingCrc(
                span = PayloadSpan(14, 20),
                foundTag = "58",
                foundLength = 2,
            ),
        )
    }

    @Test
    fun `rejects a checksum that is not four characters`() {
        // Tag 63 is present and last, so `foundTag` alone would look correct. The length is what
        // makes it wrong, which is why it is reported alongside.
        parse("0002016302AB").assertFailedWith(
            QrParseError.MissingCrc(
                span = PayloadSpan(6, 12),
                foundTag = "63",
                foundLength = 2,
            ),
        )
    }

    @Test
    fun `propagates a framing failure unchanged`() {
        parse("0099AB").assertFailedWith(
            QrParseError.LengthOverrun(
                tag = "00",
                declaredLength = 99,
                available = 2,
                offset = 0,
                span = PayloadSpan(0, 6),
                lastGoodSegment = null,
            ),
        )
    }

    @Test
    fun `rejects a blank payload`() {
        parse("   ").assertFailedWith(QrParseError.EmptyPayload)
    }

    private fun QrInquiryReport.interpretationOf(tag: String): TagInterpretation =
        segments.single { it.node.tag == tag }.interpretation

    /** [EmvSamples.QRIS_DYNAMIC] with tag 52's value swapped and the checksum left stale. */
    private fun payloadWithMerchantCategory(code: String): String =
        EmvSamples.QRIS_DYNAMIC.replace("52040780", "5204$code")
}
