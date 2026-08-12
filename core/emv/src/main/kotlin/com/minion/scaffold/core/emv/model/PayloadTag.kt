package com.minion.scaffold.core.emv.model

/**
 * One tag of a parsed payload, flattened out of the [TlvNode] tree and located in the payload.
 *
 * Where [TlvNode] is a tree — a template carries its children inside it — this is the flat,
 * positioned view the UI highlights against: every tag, template and sub-tag alike, in payload
 * order, each carrying the absolute [span] it occupies. A template appears as its own entry
 * *followed by* its children, so painting them in list order lets a child's colour land on top of
 * the framing bytes its parent covers.
 *
 * @property path           The tag's position, dotted for nesting: `"00"`, `"26"`, `"26.00"`.
 * @property tag            The two-character tag id, e.g. `"26"` or `"00"`.
 * @property depth          Nesting depth: `0` for a top-level tag, `1` for a template's sub-tag.
 * @property isTemplate     Whether this tag frames sub-tags (so its own [span] mostly overlaps them).
 * @property rawValue       The segment's raw value, exactly as it appears in the payload.
 * @property span           The payload-absolute range this tag occupies, header and value together.
 * @property interpretation What the catalog decoded a top-level value into, or [TagInterpretation.None].
 *   Always [TagInterpretation.None] for a sub-tag: a template's `00` is a globally-unique
 *   identifier, not the payload-format indicator that the same code means at the top level.
 */
data class PayloadTag(
    val path: String,
    val tag: String,
    val depth: Int,
    val isTemplate: Boolean,
    val rawValue: String,
    val span: PayloadSpan,
    val interpretation: TagInterpretation,
)
