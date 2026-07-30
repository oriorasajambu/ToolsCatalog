package com.minion.scaffold.core.emv.usecase

import com.minion.scaffold.core.emv.EmvSamples
import com.minion.scaffold.core.emv.model.EmvBuildResult
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract between reading a payload and writing it back: `build(fromPayload(p)) == p`.
 *
 * This is the test the edit tool rests on. A mapper that quietly discarded a tag it did not
 * recognise would still produce a payload that scans and whose checksum verifies — the loss would
 * only show up when the money went to the wrong merchant. Byte equality is the only assertion that
 * catches that, and asserting on individual fields would not.
 */
internal class EmvRoundTripTest {

    private val fromPayload = EmvDraftFromPayloadUseCase(ParseEmvPayloadUseCase())
    private val build = BuildEmvPayloadUseCase()

    @Test
    fun `a live payload survives a read and a write unchanged`() {
        val rebuilt = build(fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()).payloadOrFail()

        assertEquals(EmvSamples.QRIS_DYNAMIC, rebuilt)
    }

    /**
     * Tag `04` is a flat merchant account. It sits inside no field on the draft, so it survives
     * only by being carried through — and it has to land back between tags `01` and `26`.
     */
    @Test
    fun `a flat merchant account is carried through and reordered correctly`() {
        val draft = fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(
            listOf(TlvNode("04", 15, "539199000000190")),
            draft.passthrough,
        )
        assertTrue(build(draft).payloadOrFail().contains("0102120415539199000000190" + "2671"))
    }

    /** Subtag `02` is a field now, not passthrough — the acquirer's merchant identifier. */
    @Test
    fun `merchant identifiers map to fields rather than passthrough`() {
        val draft = fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        val acquirer = draft.merchantAccounts.single { it.tag == "26" }
        assertEquals("ID.CO.CIMBNIAGA.WWW", acquirer.globallyUniqueIdentifier)
        assertEquals("936000220000000282", acquirer.merchantPan)
        assertEquals("000008160012605", acquirer.merchantId)
        assertEquals("UMI", acquirer.merchantCriteria)
        assertTrue(acquirer.passthroughSubtags.isEmpty())

        val switch = draft.merchantAccounts.single { it.tag == "51" }
        assertEquals("ID.OR.QRNPG.WWW", switch.globallyUniqueIdentifier)
        assertEquals("ID0000000000123", switch.merchantId)
        assertEquals(null, switch.merchantPan)
    }

    /**
     * The edit the tool exists for: change one field, and nothing else moves.
     *
     * Asserted as a whole-payload comparison against the original with the same substitution
     * applied, so a tag lost anywhere else in the payload fails this test too.
     */
    @Test
    fun `editing the merchant name changes only that segment and the checksum`() {
        val draft = fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        val edited = build(draft.copy(merchantName = "PAK BOS QR 2")).payloadOrFail()

        val expectedBody = EmvSamples.QRIS_DYNAMIC
            .dropLast(CRC_LENGTH)
            .replace("PAK BOS QR 1", "PAK BOS QR 2")
        assertTrue(edited.startsWith(expectedBody))
        assertEquals(expectedBody.length + CRC_LENGTH, edited.length)
    }

    /** The rewritten payload must still verify, or the edit tool ships broken codes. */
    @Test
    fun `an edited payload carries a valid checksum`() {
        val draft = fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        val edited = build(draft.copy(merchantCity = "Depok")).payloadOrFail()

        assertTrue(ParseEmvPayloadUseCase()(edited).valueOrFail().crc.passed)
    }

    /** A template subtag outside `00`–`03` is carried through its own template. */
    @Test
    fun `an unrecognised subtag survives inside its template`() {
        val withExtraSubtag = payloadWithAcquirerSubtag(tag = "07", value = "EXTRA")

        val rebuilt = build(fromPayload(withExtraSubtag).valueOrFail()).payloadOrFail()

        assertEquals(withExtraSubtag, rebuilt)
    }

    @Test
    fun `no tag is emitted twice after a round trip`() {
        val rebuilt = build(fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()).payloadOrFail()

        val tags = ParseEmvPayloadUseCase()(rebuilt).valueOrFail().segments.map { it.node.tag }

        assertEquals(tags.size, tags.distinct().size)
    }

    /** A payload the parser rejects cannot produce a draft. */
    @Test
    fun `an unreadable payload does not map`() {
        assertTrue(fromPayload("https://example.com") is EmvParseResult.Failure)
    }

    /**
     * Rebuilds [EmvSamples.QRIS_DYNAMIC] with an extra subtag inside the acquirer template, by
     * going through the builder so the lengths and checksum stay consistent.
     */
    private fun payloadWithAcquirerSubtag(tag: String, value: String): String {
        val draft = fromPayload(EmvSamples.QRIS_DYNAMIC).valueOrFail()
        val accounts = draft.merchantAccounts.map { account ->
            if (account.tag == "26") {
                account.copy(passthroughSubtags = listOf(TlvNode(tag, value.length, value)))
            } else {
                account
            }
        }
        return build(draft.copy(merchantAccounts = accounts)).payloadOrFail()
    }

    private fun EmvBuildResult.payloadOrFail(): String = when (this) {
        is EmvBuildResult.Success -> payload
        is EmvBuildResult.Invalid -> throw AssertionError("expected Success but was $violations")
    }

    private companion object {
        const val CRC_LENGTH = 4
    }
}
