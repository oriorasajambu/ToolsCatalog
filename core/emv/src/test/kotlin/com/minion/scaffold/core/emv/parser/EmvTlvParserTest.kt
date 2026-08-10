package com.minion.scaffold.core.emv.parser

import com.minion.scaffold.core.emv.EmvSamples
import com.minion.scaffold.core.emv.assertFailedWith
import com.minion.scaffold.core.emv.model.HeaderDefect
import com.minion.scaffold.core.emv.model.Nesting
import com.minion.scaffold.core.emv.model.PayloadSpan
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.SegmentTrace
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EmvTlvParserTest {

    @Test
    fun `reads every segment of a live payload in encounter order`() {
        val segments = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()

        assertEquals(
            listOf("00", "01", "04", "26", "51", "52", "53", "54", "58", "59", "60", "61", "63"),
            segments.map(TlvNode::tag),
        )
    }

    @Test
    fun `reads top-level values`() {
        val segments = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail().associateBy { it.tag }

        assertEquals("01", segments.getValue("00").rawValue)
        assertEquals("12", segments.getValue("01").rawValue)
        assertEquals("0780", segments.getValue("52").rawValue)
        assertEquals("360", segments.getValue("53").rawValue)
        assertEquals("15000000.00", segments.getValue("54").rawValue)
        assertEquals("ID", segments.getValue("58").rawValue)
        assertEquals("PAK BOS QR 1", segments.getValue("59").rawValue)
        assertEquals("Bekasi", segments.getValue("60").rawValue)
        assertEquals("17151", segments.getValue("61").rawValue)
        assertEquals("3D58", segments.getValue("63").rawValue)
    }

    @Test
    fun `nests the acquirer merchant account template`() {
        val segments = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()
        val acquirer = segments.single { it.tag == "26" }

        assertEquals(71, acquirer.length)
        assertEquals(
            listOf(
                TlvNode("00", 19, "ID.CO.CIMBNIAGA.WWW"),
                TlvNode("01", 18, "936000220000000282"),
                TlvNode("02", 15, "000008160012605"),
                TlvNode("03", 3, "UMI"),
            ),
            acquirer.children,
        )
    }

    @Test
    fun `nests the national switch merchant account template`() {
        val segments = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()
        val switch = segments.single { it.tag == "51" }

        assertEquals(45, switch.length)
        assertEquals(
            listOf(
                TlvNode("00", 15, "ID.OR.QRNPG.WWW"),
                TlvNode("02", 15, "ID0000000000123"),
                TlvNode("03", 3, "UMI"),
            ),
            switch.children,
        )
    }

    /**
     * Tag `04` is Merchant Account Information, same family as tag `26`, but carries a bare
     * network identifier rather than subtags.
     *
     * The regression guard for the nesting rule: a parser that nests the whole `02`–`51` range
     * reads `539199000000190` as tag `53` declaring 91 characters, and either fails outright or
     * invents subtags out of a phone-number-shaped string.
     */
    @Test
    fun `leaves a plain merchant account identifier flat`() {
        val segments = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()
        val identifier = segments.single { it.tag == "04" }

        assertEquals("539199000000190", identifier.rawValue)
        assertTrue(identifier.children.isEmpty())
    }

    @Test
    fun `nests a template whose value frames cleanly`() {
        val segments = EmvTlvParser.parse("26080004TEST").valueOrFail()

        assertEquals(listOf(TlvNode("00", 4, "TEST")), segments.single().children)
    }

    /**
     * A template tag is an invitation to nest, not a guarantee. When the value is not TLV at all
     * the segment is reported flat rather than as a failure — a payload the tool cannot fully
     * decompose is still worth showing to whoever is trying to diagnose it.
     */
    @Test
    fun `leaves a template flat when its value is not well-formed`() {
        val segments = EmvTlvParser.parse("2608ABCDEFGH").valueOrFail()

        assertEquals("ABCDEFGH", segments.single().rawValue)
        assertTrue(segments.single().children.isEmpty())
    }

    @Test
    fun `does not nest beyond one level`() {
        // Tag 26 holds tag 26, which would itself be a template if recursion were unbounded.
        val segments = EmvTlvParser.parse("261226080004TEST").valueOrFail()
        val inner = segments.single().children.single()

        assertEquals("26", inner.tag)
        assertTrue(inner.children.isEmpty())
    }

    @Test
    fun `accepts a zero-length value`() {
        val segments = EmvTlvParser.parse("0000").valueOrFail()

        assertEquals(listOf(TlvNode("00", 0, "")), segments)
    }

    @Test
    fun `rejects a blank payload`() {
        EmvTlvParser.parse("   ").assertFailedWith(QrParseError.EmptyPayload)
    }

    @Test
    fun `rejects a barcode that is not EMV at all`() {
        EmvTlvParser.parse("https://example.com").assertFailedWith(
            QrParseError.NotAnEmvPayload(span = PayloadSpan(0, 2), found = "ht"),
        )
    }

    @Test
    fun `reports a non-numeric tag at its offset`() {
        EmvTlvParser.parse("000201XY0212").assertFailedWith(
            QrParseError.MalformedTlv(
                offset = 6,
                span = PayloadSpan(6, 8),
                defect = HeaderDefect.NON_NUMERIC_TAG,
                found = "XY",
                lastGoodSegment = SegmentTrace("00", 2, PayloadSpan(0, 6)),
            ),
        )
    }

    @Test
    fun `reports a truncated segment header at its offset`() {
        EmvTlvParser.parse("000201012").assertFailedWith(
            QrParseError.MalformedTlv(
                offset = 6,
                span = PayloadSpan(6, 9),
                defect = HeaderDefect.TRUNCATED,
                found = "012",
                lastGoodSegment = SegmentTrace("00", 2, PayloadSpan(0, 6)),
            ),
        )
    }

    @Test
    fun `reports a length that runs past the end of the payload`() {
        EmvTlvParser.parse("0099AB").assertFailedWith(
            QrParseError.LengthOverrun(
                tag = "00",
                declaredLength = 99,
                available = 2,
                offset = 0,
                span = PayloadSpan(0, 6),
                // Nothing parsed before it, so there is no last good segment to report.
                lastGoodSegment = null,
            ),
        )
    }

    /**
     * Lengths are decimal. Read as hexadecimal, `15` becomes 21 and the cursor lands six
     * characters past the next tag, so the rest of the payload decodes into convincing nonsense
     * instead of failing loudly.
     */
    @Test
    fun `reads lengths as decimal rather than hexadecimal`() {
        val segments = EmvTlvParser.parse("0015ABCDEFGHIJKLMNO").valueOrFail()

        assertEquals(15, segments.single().length)
        assertEquals("ABCDEFGHIJKLMNO", segments.single().rawValue)
    }

    /**
     * A template's own declared length is checked like any other. Only its *value* gets the
     * lenient treatment — a template that overruns the payload is a broken payload, not a
     * template holding something unexpected.
     */
    @Test
    fun `reports a template whose declared length overruns the payload`() {
        EmvTlvParser.parse("00020126080004TES").assertFailedWith(
            QrParseError.LengthOverrun(
                tag = "26",
                declaredLength = 8,
                available = 7,
                offset = 6,
                span = PayloadSpan(6, 17),
                lastGoodSegment = SegmentTrace("00", 2, PayloadSpan(0, 6)),
            ),
        )
    }

    @Test
    fun `reports offsets against the payload rather than the current segment`() {
        // Three valid segments (24 characters, the third a nested template) then a bad tag. The
        // last good span covers the whole template including its base offset, which is what shows
        // nested framing has not shifted the outer arithmetic.
        EmvTlvParser.parse("00020101021226080004TESTZZ0201").assertFailedWith(
            QrParseError.MalformedTlv(
                offset = 24,
                span = PayloadSpan(24, 26),
                defect = HeaderDefect.NON_NUMERIC_TAG,
                found = "ZZ",
                lastGoodSegment = SegmentTrace("26", 8, PayloadSpan(12, 24)),
            ),
        )
    }

    /**
     * The regression guard for the whole diagnostic feature.
     *
     * A payload whose tag `32` lost its two length digits. Every number here is load-bearing and no
     * two are the same: the break is reported at **16**, the characters at fault are at **18**, and
     * the actual damage is at **14**, where `51` should have been. Only the last good segment — tag
     * 32 declaring zero characters, which a template that size cannot possibly do — points at the
     * real defect. That is why the error carries it.
     */
    @Test
    fun `brackets a missing length between the break and the last good segment`() {
        EmvTlvParser.parse(EmvSamples.SAMA_MISSING_LENGTH_DIGITS).assertFailedWith(
            QrParseError.MalformedTlv(
                offset = 16,
                span = PayloadSpan(18, 20),
                defect = HeaderDefect.NON_NUMERIC_LENGTH,
                found = "SA",
                lastGoodSegment = SegmentTrace("32", 0, PayloadSpan(12, 16)),
            ),
        )
    }

    /**
     * A valid tag followed by an invalid length is a *length* defect.
     *
     * Guards the split of what used to be one fused condition: reporting "a tag or length could not
     * be read" points at four characters when two of them are fine.
     */
    @Test
    fun `distinguishes a bad length from a bad tag`() {
        EmvTlvParser.parse("00020111SA12").assertFailedWith(
            QrParseError.MalformedTlv(
                offset = 6,
                span = PayloadSpan(8, 10),
                defect = HeaderDefect.NON_NUMERIC_LENGTH,
                found = "SA",
                lastGoodSegment = SegmentTrace("00", 2, PayloadSpan(0, 6)),
            ),
        )
    }

    // --- Nesting -----------------------------------------------------------------------------

    @Test
    fun `marks a template that framed`() {
        val acquirer = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()
            .single { it.tag == "26" }

        assertEquals(Nesting.Framed, acquirer.nesting)
    }

    @Test
    fun `marks a template that did not frame`() {
        val segments = EmvTlvParser.parse("2608ABCDEFGH").valueOrFail()

        assertEquals(Nesting.Unframed, segments.single().nesting)
    }

    @Test
    fun `leaves a non-template unmarked`() {
        // Tag 04 sits below the 26..51 template range, so nesting was never attempted — a
        // different statement from having attempted it and failed.
        val identifier = EmvTlvParser.parse(EmvSamples.QRIS_DYNAMIC).valueOrFail()
            .single { it.tag == "04" }

        assertEquals(Nesting.NotApplicable, identifier.nesting)
    }

    @Test
    fun `treats an empty template value as framed`() {
        // Zero characters frame as zero segments, vacuously. Calling that unframed would flag every
        // legitimately empty template as suspicious.
        val segments = EmvTlvParser.parse("2600").valueOrFail()

        assertEquals(Nesting.Framed, segments.single().nesting)
        assertTrue(segments.single().children.isEmpty())
    }
}
