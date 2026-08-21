package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.EmvSamples
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.core.emv.model.EmvSegmentDiff
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads here are assembled tag by tag rather than taken verbatim, because what is under test is
 * the *relationship* between two of them — and a real pair differing only in a moved merchant
 * account is not something a sample file can hold readably.
 *
 * The checksums are placeholders. A failed checksum is deliberately not a parse error (it is the
 * point of the tool), so a payload carrying `6304ABCD` parses cleanly and the comparison never has
 * to care that the four characters are invented.
 */
internal class CompareEmvPayloadsUseCaseTest {

    private val parse = ParseEmvPayloadUseCase()
    private val compare = CompareEmvPayloadsUseCase()

    @Test
    fun `a payload compared with itself reports no differences`() {
        val report = parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        val comparison = compare(report, report)

        assertEquals(0, comparison.changedCount)
        assertTrue(comparison.valuesMatch)
        assertTrue(comparison.segments.all { it.status == DiffStatus.SAME })
    }

    @Test
    fun `a changed amount is one difference`() {
        val comparison = compare(
            report(FORMAT, tlv(TAG_AMOUNT, "15000.00"), tlv(TAG_MERCHANT_NAME, "PAK BOS")),
            report(FORMAT, tlv(TAG_AMOUNT, "25000.00"), tlv(TAG_MERCHANT_NAME, "PAK BOS")),
        )

        assertEquals(1, comparison.changedCount)
        assertEquals(DiffStatus.CHANGED, comparison.of(TAG_AMOUNT).status)
        assertEquals(DiffStatus.SAME, comparison.of(TAG_MERCHANT_NAME).status)
    }

    @Test
    fun `reordering tags is not a difference`() {
        val comparison = compare(
            report(FORMAT, tlv(TAG_AMOUNT, "15000.00"), tlv(TAG_MERCHANT_NAME, "PAK BOS")),
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS"), tlv(TAG_AMOUNT, "15000.00")),
        )

        assertEquals(0, comparison.changedCount)
        assertTrue(comparison.valuesMatch)
    }

    @Test
    fun `a merchant account that moved slot keeps its identity`() {
        val account = account(guid = SCHEME_A, pan = "936000220000000282")

        val comparison = compare(
            report(FORMAT, tlv("26", account)),
            report(FORMAT, tlv("27", account)),
        )

        val moved = comparison.of("27")
        assertEquals(DiffStatus.SAME, moved.status)
        assertEquals("26", moved.movedFromTag)
        assertEquals(0, comparison.changedCount)
        // The format indicator and the account, and nothing else: a moved account is one row, not
        // a removal beside an addition.
        assertEquals(listOf(FORMAT_TAG, "27"), comparison.segments.map { it.tag })
    }

    @Test
    fun `an account that moved and changed reports the change once`() {
        val comparison = compare(
            report(FORMAT, tlv("26", account(guid = SCHEME_A, pan = "111111111111"))),
            report(FORMAT, tlv("27", account(guid = SCHEME_A, pan = "222222222222"))),
        )

        val moved = comparison.of("27")
        assertEquals(DiffStatus.CHANGED, moved.status)
        assertEquals("26", moved.movedFromTag)
        assertEquals(1, comparison.changedCount)
    }

    @Test
    fun `two accounts that swapped slots are both moved rather than rewritten`() {
        val first = account(guid = SCHEME_A, pan = "111111111111")
        val second = account(guid = SCHEME_B, pan = "222222222222")

        val comparison = compare(
            report(FORMAT, tlv("26", first), tlv("27", second)),
            report(FORMAT, tlv("26", second), tlv("27", first)),
        )

        assertEquals(0, comparison.changedCount)
        assertEquals("26", comparison.of("27").movedFromTag)
        assertEquals("27", comparison.of("26").movedFromTag)
    }

    @Test
    fun `a different scheme in the same slot is a change, not a swap`() {
        val comparison = compare(
            report(FORMAT, tlv("26", account(guid = SCHEME_A, pan = "111111111111"))),
            report(FORMAT, tlv("26", account(guid = SCHEME_B, pan = "111111111111"))),
        )

        val changed = comparison.of("26")
        assertEquals(DiffStatus.CHANGED, changed.status)
        assertNull(changed.movedFromTag)
        assertEquals(DiffStatus.CHANGED, changed.subtag(SUBTAG_GUID).status)
        assertEquals(DiffStatus.SAME, changed.subtag(SUBTAG_PAN).status)
        assertEquals(1, comparison.changedCount)
    }

    @Test
    fun `a different scheme in a different slot is an addition and a removal`() {
        val comparison = compare(
            report(FORMAT, tlv("26", account(guid = SCHEME_A, pan = "111111111111"))),
            report(FORMAT, tlv("27", account(guid = SCHEME_B, pan = "222222222222"))),
        )

        assertEquals(DiffStatus.ONLY_IN_BASELINE, comparison.of("26").status)
        assertEquals(DiffStatus.ONLY_IN_CANDIDATE, comparison.of("27").status)
        assertEquals(2, comparison.changedCount)
    }

    @Test
    fun `a subtag added to a template is reported inside it`() {
        val comparison = compare(
            report(FORMAT, tlv("26", account(guid = SCHEME_A, pan = "111111111111"))),
            report(
                FORMAT,
                tlv("26", account(guid = SCHEME_A, pan = "111111111111") + tlv("03", "UMI")),
            ),
        )

        val template = comparison.of("26")
        assertEquals(DiffStatus.CHANGED, template.status)
        assertEquals(DiffStatus.SAME, template.subtag(SUBTAG_GUID).status)
        assertEquals(DiffStatus.ONLY_IN_CANDIDATE, template.subtag("03").status)
        // One difference, not two: the template changed *because* the subtag was added.
        assertEquals(1, comparison.changedCount)
    }

    @Test
    fun `an unframed template falls back to its raw value`() {
        // Tag 53 declaring 91 characters it does not have, so the value never frames as sub-TLVs —
        // a shape live payloads genuinely carry, and one with no identifier subtag to match on.
        val flat = "539199000000190"

        val comparison = compare(
            report(FORMAT, tlv("26", flat)),
            report(FORMAT, tlv("27", flat)),
        )

        val moved = comparison.of("27")
        assertEquals(DiffStatus.SAME, moved.status)
        assertEquals("26", moved.movedFromTag)
        assertTrue(moved.subtags.isEmpty())
        assertEquals(0, comparison.changedCount)
    }

    @Test
    fun `a differing checksum is reported but never counted`() {
        val fields = listOf(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS"))

        val comparison = compare(
            parse(fields.joinToString(separator = "") + "6304ABCD").valueOrFail(),
            parse(fields.joinToString(separator = "") + "6304BEEF").valueOrFail(),
        )

        assertEquals(0, comparison.changedCount)
        assertTrue(comparison.valuesMatch)
        assertFalse(comparison.crc.same)
        assertTrue(comparison.segments.none { it.tag == "63" })
    }

    @Test
    fun `a checksum neither payload validates is visible in the diff`() {
        val comparison = compare(
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS")),
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS")),
        )

        assertTrue(comparison.crc.same)
        assertFalse(comparison.crc.bothValid)
    }

    @Test
    fun `a repeated tag aligns by occurrence`() {
        val comparison = compare(
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "FIRST"), tlv(TAG_MERCHANT_NAME, "SECOND")),
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "FIRST"), tlv(TAG_MERCHANT_NAME, "THIRD")),
        )

        val names = comparison.segments.filter { it.tag == TAG_MERCHANT_NAME }
        assertEquals(2, names.size)
        assertEquals(DiffStatus.SAME, names[0].status)
        assertEquals(DiffStatus.CHANGED, names[1].status)
        assertEquals(1, comparison.changedCount)
    }

    @Test
    fun `a tag present only in the second payload is appended`() {
        val comparison = compare(
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS")),
            report(FORMAT, tlv(TAG_MERCHANT_NAME, "PAK BOS"), tlv(TAG_AMOUNT, "15000.00")),
        )

        assertEquals(listOf(FORMAT_TAG, TAG_MERCHANT_NAME, TAG_AMOUNT), comparison.segments.map { it.tag })
        assertEquals(DiffStatus.ONLY_IN_CANDIDATE, comparison.of(TAG_AMOUNT).status)
        assertEquals(1, comparison.changedCount)
    }

    private fun report(vararg segments: String): QrInquiryReport =
        parse(segments.joinToString(separator = "") + "6304ABCD").valueOrFail()

    private fun EmvComparison.of(tag: String): EmvSegmentDiff =
        segments.first { it.tag == tag }

    private fun EmvSegmentDiff.subtag(tag: String) = subtags.first { it.tag == tag }

    private companion object {
        const val FORMAT_TAG = "00"
        const val TAG_AMOUNT = "54"
        const val TAG_MERCHANT_NAME = "59"
        const val SUBTAG_GUID = "00"
        const val SUBTAG_PAN = "01"
        const val SCHEME_A = "ID.CO.CIMBNIAGA.WWW"
        const val SCHEME_B = "ID.OR.QRNPG.WWW"

        /** Every payload has to open with the payload format indicator or it will not parse. */
        val FORMAT = tlv(FORMAT_TAG, "01")

        fun tlv(tag: String, value: String): String =
            tag + value.length.toString().padStart(2, '0') + value

        fun account(guid: String, pan: String): String = tlv("00", guid) + tlv("01", pan)
    }
}
