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
     *
     * @param payload The raw payload to frame. Assumed already trimmed by the caller.
     * @return [EmvParseResult.Success] with the framed segments, or [EmvParseResult.Failure]
     *         carrying the [QrParseError] describing the framing problem.
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
     *
     * @param segments The top-level segments, in payload order.
     * @param index    The position in [segments] whose span to compute.
     * @return The [PayloadSpan] the segment at [index] occupies in the payload.
     */
    fun spanOf(segments: List<TlvNode>, index: Int): PayloadSpan {
        var start = 0
        for (position in 0 until index) {
            start += HEADER_LENGTH + segments[position].length
        }
        return PayloadSpan(start, start + HEADER_LENGTH + segments[index].length)
    }

    /**
     * One tag from [flatten]: its dotted path, nesting depth, node and payload-absolute span.
     *
     * Interpretation is deliberately absent — framing is this object's only business, and what a
     * value *means* is [EmvTagCatalog]'s. The use case that adds it is what turns these into a
     * public `PayloadTag`.
     */
    data class FlatNode(
        val path: String,
        val depth: Int,
        val node: TlvNode,
        val span: PayloadSpan,
    )

    /**
     * Walks [segments] into a flat, positioned list: every node in payload order, a template
     * immediately before its children.
     *
     * The span arithmetic is the same rule [spanOf] applies — a node occupies its header plus its
     * declared length, and a child's offsets are measured from its parent's value start
     * (`start + HEADER_LENGTH`). Kept here rather than in a caller so that one rule has one home;
     * a highlighter recomputing it would be the second implementation the span note warns against.
     *
     * @param segments Top-level segments in payload order, as returned by [parse].
     * @return Every node, flattened, each carrying the payload-absolute [FlatNode.span] it occupies.
     */
    fun flatten(segments: List<TlvNode>): List<FlatNode> = buildList {
        fun walk(nodes: List<TlvNode>, baseOffset: Int, parentPath: String?, depth: Int) {
            var cursor = baseOffset
            for (node in nodes) {
                val span = PayloadSpan(cursor, cursor + HEADER_LENGTH + node.length)
                val path = if (parentPath == null) node.tag else "$parentPath.${node.tag}"
                add(FlatNode(path = path, depth = depth, node = node, span = span))
                if (node.children.isNotEmpty()) {
                    walk(node.children, cursor + HEADER_LENGTH, path, depth + 1)
                }
                cursor = span.endExclusive
            }
        }
        walk(segments, baseOffset = 0, parentPath = null, depth = 0)
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
            val header = when (val read = readHeader(data, cursor, baseOffset, lastGood)) {
                is HeaderParse.Invalid -> return EmvParseResult.Failure(read.error)
                is HeaderParse.Ok -> read
            }

            val rawValue = data.substring(header.valueStart, header.valueStart + header.length)
            val nesting = if (allowNesting && EmvTagCatalog.isTemplate(header.tag)) {
                readChildren(rawValue, baseOffset = baseOffset + header.valueStart)
            } else {
                ChildParse.NOT_A_TEMPLATE
            }

            segments += TlvNode(
                tag = header.tag,
                length = header.length,
                rawValue = rawValue,
                children = nesting.children,
                nesting = nesting.nesting,
            )

            lastGood = SegmentTrace(
                tag = header.tag,
                declaredLength = header.length,
                span = PayloadSpan(
                    baseOffset + cursor,
                    baseOffset + header.valueStart + header.length,
                ),
            )
            cursor = header.valueStart + header.length
        }

        return EmvParseResult.Success(segments)
    }

    /**
     * Reads the four-character header at [cursor], and the length it declares.
     *
     * Every way a segment can fail to frame lives here — a header that does not fit, a non-numeric
     * tag, a non-numeric length, and a length that runs past the end of [data]. Gathering them in
     * one place is what lets [readSegments] read as the loop it is; each carries [lastGood] so the
     * report can say where the payload was last making sense.
     *
     * @return [HeaderParse.Ok] with the framing, or [HeaderParse.Invalid] with the rejection.
     */
    private fun readHeader(
        data: String,
        cursor: Int,
        baseOffset: Int,
        lastGood: SegmentTrace?,
    ): HeaderParse {
        val headerStart = baseOffset + cursor

        if (data.length - cursor < HEADER_LENGTH) {
            return HeaderParse.Invalid(
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
            return HeaderParse.Invalid(
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
            return HeaderParse.Invalid(
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
            return HeaderParse.Invalid(
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

        return HeaderParse.Ok(tag = tag, length = length, valueStart = valueStart)
    }

    /** One header read: the framing it declares, or why it was refused. */
    private sealed interface HeaderParse {

        /**
         * The header framed.
         *
         * @property tag        The two-character tag.
         * @property length     The declared value length, in characters.
         * @property valueStart Where the value starts, relative to the data being read.
         */
        data class Ok(val tag: String, val length: Int, val valueStart: Int) : HeaderParse

        /**
         * The header did not frame.
         *
         * @property error What was wrong, positioned in the whole payload.
         */
        data class Invalid(val error: QrParseError) : HeaderParse
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
