package com.minion.scaffold.core.emv.parser

import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.emv.model.HeaderDefect
import com.minion.scaffold.core.emv.model.Nesting
import com.minion.scaffold.core.emv.model.PayloadSpan
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.model.SegmentTrace
import com.minion.scaffold.core.emv.model.TlvNode

/**
 * Splits an EMV payload into its tag-length-value segments.
 *
 * Framing only — this object assigns no meaning to any tag beyond knowing which ones may nest.
 * What a value *is* belongs to [EmvTagCatalog].
 */
internal object EmvTlvParser {

    private const val TAG_LENGTH = 2
    private const val LENGTH_LENGTH = 2

    /** Four characters: two of tag, two of length. */
    const val HEADER_LENGTH = TAG_LENGTH + LENGTH_LENGTH

    /**
     * Reads [payload] into a flat list of segments, each with its sub-segments where the tag is a
     * template.
     *
     * Fails on framing problems only. A structurally valid payload that is missing mandatory tags
     * still parses — those are the caller's checks, because a payload with no tag `63` is still
     * worth showing to whoever is trying to work out why it is broken.
     */
    fun parse(payload: String): EmvParseResult<List<TlvNode>> {
        if (payload.isBlank()) return EmvParseResult.Failure(QrParseError.EmptyPayload)

        // An EMV payload opens with a numeric tag. Anything else — a URL, a WiFi block, plain
        // text — is a different kind of barcode rather than a damaged one, and saying so lets the
        // UI tell the user to scan a different code instead of implying this one is corrupt.
        val opening = payload.take(TAG_LENGTH)
        if (payload.length < TAG_LENGTH || !opening.all(Char::isDigit)) {
            return EmvParseResult.Failure(
                QrParseError.NotAnEmvPayload(
                    span = PayloadSpan(0, minOf(TAG_LENGTH, payload.length)),
                    found = opening,
                ),
            )
        }

        return readSegments(data = payload, baseOffset = 0, allowNesting = true)
    }

    /**
     * The span a segment occupies, given the segments before it.
     *
     * Segments are contiguous and each occupies its header plus its declared length, so a forward
     * sum is exact. Lives here because framing arithmetic is this object's business — a caller
     * recomputing it would be a second implementation of the one rule that must not drift.
     */
    fun spanOf(segments: List<TlvNode>, index: Int): PayloadSpan {
        var start = 0
        for (position in 0 until index) {
            start += HEADER_LENGTH + segments[position].length
        }
        return PayloadSpan(start, start + HEADER_LENGTH + segments[index].length)
    }

    /**
     * Reads [data] to exhaustion.
     *
     * Exact consumption is structural rather than checked: the loop advances by exactly the
     * declared length each time and a length that would run past the end is rejected, so
     * returning [EmvParseResult.Success] already means every character was accounted for. That is
     * what makes the result trustworthy as a nesting test in [readChildren].
     *
     * [baseOffset] is where `data[0]` sits in the whole payload — zero for the top-level call, and
     * a template's value offset for a nested one. Every span this produces is therefore
     * payload-absolute **by construction**. That used to be true only by accident, because nested
     * failures were discarded before any caller could see their relative offsets; making it
     * structural means a nested offset can be surfaced later without re-deriving anything.
     */
    private fun readSegments(
        data: String,
        baseOffset: Int,
        allowNesting: Boolean,
    ): EmvParseResult<List<TlvNode>> {
        val segments = mutableListOf<TlvNode>()
        var cursor = 0
        var lastGood: SegmentTrace? = null

        while (cursor < data.length) {
            val headerStart = baseOffset + cursor

            if (data.length - cursor < HEADER_LENGTH) {
                return EmvParseResult.Failure(
                    QrParseError.MalformedTlv(
                        offset = headerStart,
                        span = PayloadSpan(headerStart, baseOffset + data.length),
                        defect = HeaderDefect.TRUNCATED,
                        found = data.substring(cursor),
                        lastGoodSegment = lastGood,
                    ),
                )
            }

            val tag = data.substring(cursor, cursor + TAG_LENGTH)
            val declaredLength = data.substring(cursor + TAG_LENGTH, cursor + HEADER_LENGTH)

            // Checked separately, not as one condition. A tag of "11" followed by a length of "SA"
            // is a bad *length*; blaming the whole header accuses two characters that are fine and
            // sends the reader looking in the wrong place.
            if (!tag.all(Char::isDigit)) {
                return EmvParseResult.Failure(
                    QrParseError.MalformedTlv(
                        offset = headerStart,
                        span = PayloadSpan(headerStart, headerStart + TAG_LENGTH),
                        defect = HeaderDefect.NON_NUMERIC_TAG,
                        found = tag,
                        lastGoodSegment = lastGood,
                    ),
                )
            }

            if (!declaredLength.all(Char::isDigit)) {
                return EmvParseResult.Failure(
                    QrParseError.MalformedTlv(
                        offset = headerStart,
                        span = PayloadSpan(
                            headerStart + TAG_LENGTH,
                            headerStart + HEADER_LENGTH,
                        ),
                        defect = HeaderDefect.NON_NUMERIC_LENGTH,
                        found = declaredLength,
                        lastGoodSegment = lastGood,
                    ),
                )
            }

            // Decimal, not hexadecimal. A hex reading of "15" is 21, which puts the cursor six
            // characters past where the next tag starts and turns the rest of the payload into
            // convincing nonsense.
            val length = declaredLength.toInt()
            val valueStart = cursor + HEADER_LENGTH
            val available = data.length - valueStart

            if (length > available) {
                return EmvParseResult.Failure(
                    QrParseError.LengthOverrun(
                        tag = tag,
                        declaredLength = length,
                        available = available,
                        offset = headerStart,
                        span = PayloadSpan(headerStart, baseOffset + data.length),
                        lastGoodSegment = lastGood,
                    ),
                )
            }

            val rawValue = data.substring(valueStart, valueStart + length)
            val nesting = if (allowNesting && EmvTagCatalog.isTemplate(tag)) {
                readChildren(rawValue, baseOffset = baseOffset + valueStart)
            } else {
                ChildParse.NOT_A_TEMPLATE
            }

            segments += TlvNode(
                tag = tag,
                length = length,
                rawValue = rawValue,
                children = nesting.children,
                nesting = nesting.nesting,
            )

            lastGood = SegmentTrace(
                tag = tag,
                declaredLength = length,
                span = PayloadSpan(headerStart, baseOffset + valueStart + length),
            )
            cursor = valueStart + length
        }

        return EmvParseResult.Success(segments)
    }

    /**
     * Attempts to read [value] as nested segments, reporting whether it framed.
     *
     * A template tag is an invitation to nest, not a guarantee. Tag `26` in a live QRIS payload
     * holds four subtags; the same range also carries plain merchant identifiers that happen to
     * begin with digits, and forcing those through a sub-parse yields garbage subtags with
     * nonsense lengths. Requiring the sub-parse to consume the value exactly is what separates
     * the two, and it costs nothing: a plain identifier essentially never frames cleanly by
     * accident.
     *
     * One level deep, deliberately. Every template the specification defines holds plain values,
     * so recursing further can only invent structure that is not there.
     *
     * A failure inside is still discarded — the segment is reported flat, and the payload as a
     * whole still parses. What is no longer discarded is the *fact* of it, which reaches the report
     * as [Nesting.Unframed].
     */
    private fun readChildren(value: String, baseOffset: Int): ChildParse = when {
        // Zero characters frame as zero segments, vacuously. Distinguished from one-to-three
        // characters, which cannot possibly hold a header and so genuinely did not frame.
        value.isEmpty() -> ChildParse(emptyList(), Nesting.Framed)

        value.length < HEADER_LENGTH -> ChildParse(emptyList(), Nesting.Unframed)

        else -> when (
            val result = readSegments(value, baseOffset = baseOffset, allowNesting = false)
        ) {
            is EmvParseResult.Success -> ChildParse(result.value, Nesting.Framed)
            is EmvParseResult.Failure -> ChildParse(emptyList(), Nesting.Unframed)
        }
    }

    /** A nesting attempt's outcome: what was read, and whether reading it worked. */
    private data class ChildParse(val children: List<TlvNode>, val nesting: Nesting) {

        companion object {
            val NOT_A_TEMPLATE = ChildParse(emptyList(), Nesting.NotApplicable)
        }
    }
}
