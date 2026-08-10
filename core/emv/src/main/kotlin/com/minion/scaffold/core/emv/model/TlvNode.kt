package com.minion.scaffold.core.emv.model

/**
 * One tag-length-value segment of an EMV payload.
 *
 * [length] is the length the payload *declared*, kept separate from `rawValue.length` so the
 * report can show what the merchant encoded rather than what was recovered. The parser rejects a
 * payload where the two would disagree, so in a successful parse they are equal — but the
 * declared value is what a reader is checking when something looks wrong.
 *
 * [children] is empty for a plain value and populated for a template (tag `26`–`51`, `62`, `64`,
 * `80`–`99`). Emptiness alone does not say *why* it is empty, which is what [nesting] adds.
 */
data class TlvNode(
    val tag: String,
    val length: Int,
    val rawValue: String,
    val children: List<TlvNode> = emptyList(),
    val nesting: Nesting = Nesting.NotApplicable,
)

/**
 * Whether a segment's value was read as sub-segments, and if not, why not.
 *
 * Exists because empty [TlvNode.children] previously meant three different things at once: not a
 * template, a template holding nothing, and a template whose contents could not be framed. The
 * last of those is a real diagnostic — a payload can parse "successfully" while a template's
 * insides are damaged — and it was silently indistinguishable from the other two.
 *
 * [NotApplicable] is the default so that a hand-constructed node, of which there are several in the
 * build path and in tests, still compares equal to what the parser produces for a non-template tag.
 */
enum class Nesting {

    /** Not a template tag — nesting was never attempted. */
    NotApplicable,

    /** A template whose value read cleanly as sub-segments. A zero-length value frames vacuously. */
    Framed,

    /**
     * A template whose value did not frame, so it is reported flat.
     *
     * Not necessarily damage. Plenty of live payloads put a plain merchant identifier in a tag the
     * specification reserves for a template, and those never frame — which is exactly why the
     * parser keeps going rather than failing, and why anything surfacing this must word it as an
     * observation rather than a warning.
     */
    Unframed,
}
