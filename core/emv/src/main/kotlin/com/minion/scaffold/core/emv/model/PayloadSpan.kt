package com.minion.scaffold.core.emv.model

/**
 * A half-open range of payload characters, `start` inclusive and `endExclusive` exclusive.
 *
 * **Zero-based, and indexing the trimmed payload.** Both matter. `ParseEmvPayloadUseCase` trims
 * before parsing, so an offset is relative to the trimmed string rather than to whatever the
 * scanner or clipboard handed over — a single leading space would otherwise put every number on
 * screen one character out.
 *
 * Character offsets, not glyphs. A merchant name holding an emoji is two UTF-16 units here and one
 * glyph on screen, which is why anything drawing a ruler against these has to say so.
 */
data class PayloadSpan(val start: Int, val endExclusive: Int) {

    val length: Int get() = endExclusive - start

    /**
     * Whether this points *between* two characters rather than at any.
     *
     * Happens for [QrParseError.EmptyPayload], and for a break at the very end of a payload.
     * A renderer that styles a range will draw nothing at all for these, so it has to check.
     */
    val isEmpty: Boolean get() = length <= 0

    companion object {

        /** A zero-width position — "here", with nothing to highlight. */
        fun at(offset: Int): PayloadSpan = PayloadSpan(offset, offset)
    }
}

/**
 * The last segment that read cleanly before a break, for context.
 *
 * Carried as the **segment** rather than as an offset, and the distinction is the whole point. The
 * parse loop reports its failure at the cursor, and the cursor already *is* the end of the last good
 * segment — so an offset would be identical to the failure offset every time and would bracket
 * nothing.
 *
 * The segment itself does bracket it. For a payload whose tag `32` is missing its two length digits,
 * this reads "tag 32, declared length 00, characters 12–16" — and "tag 32 length 00" is the fact
 * that gives the game away, since a template that large cannot hold nothing. No offset can say that.
 */
data class SegmentTrace(
    val tag: String,
    val declaredLength: Int,
    val span: PayloadSpan,
)

/**
 * Which half of a segment header could not be read.
 *
 * The parser checks the tag and the length separately so this can be specific. Fusing them — as it
 * once did — means reporting "a tag or length could not be read", which is vague in the common case
 * where one of the two is perfectly valid and the other is not.
 */
enum class HeaderDefect {

    /** Fewer than four characters remain, so there is no complete header to read. */
    TRUNCATED,

    /** The two tag characters are not both digits. */
    NON_NUMERIC_TAG,

    /** The tag is fine; the two length characters are not both digits. */
    NON_NUMERIC_LENGTH,
}
