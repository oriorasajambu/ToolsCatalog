package com.minion.scaffold.core.emv.model

/**
 * What two EMV payloads have in common, and where they part company.
 *
 * Pure structure and raw values — no words. Labels for a tag stay `@StringRes` in the presentation
 * layer for the same reason they are absent from the tag catalog: this module has to stay a
 * translation-free lookup that a unit test can assert against.
 *
 * @property segments Every top-level tag from either payload, in the baseline's order with the
 *   candidate's extras appended.
 * @property crc      The two checksum verdicts, side by side.
 */
data class EmvComparison(
    val segments: List<EmvSegmentDiff>,
    val crc: CrcDiff,
) {

    /**
     * How many substantive fields differ.
     *
     * Tag `63` is not among them, and cannot be: it is *derived* from everything else, so a single
     * edited field necessarily changes the checksum too. Counting it would report every one-field
     * edit as two, and there would be no honest way to say "nothing differs" about a pair that had
     * been re-encoded. It gets [crc] instead, where the expected and computed values can be read
     * against each other.
     *
     * A segment counts once when the segment itself changed shape, and otherwise once per changed
     * subtag — so a merchant account with one edited PAN reports one difference, not two.
     */
    val changedCount: Int = segments.sumOf(EmvSegmentDiff::differenceCount)

    /**
     * Whether every field lined up with an equal value.
     *
     * True does **not** mean the payloads are the same string: reordered tags, a merchant account
     * that moved slot, and the checksum that follows from either all leave this true. That gap is
     * the whole reason the verdict distinguishes "identical" from "equivalent".
     */
    val valuesMatch: Boolean get() = changedCount == 0
}

/**
 * One top-level tag, as it appears in one payload, the other, or both.
 *
 * @property tag          Where the field sits in the *candidate* — the code in front of you — or
 *   the baseline's tag when the candidate has no counterpart for it.
 * @property status       How the two sides relate.
 * @property movedFromTag The baseline's tag, when a merchant account matched at a *different* slot.
 *   An annotation rather than a status, because a moved account can equally be unchanged (same
 *   values, new slot) or changed (both) — folding the two into one `MOVED` case would lose which.
 * @property baseline     The baseline's segment, absent when this tag is only in the candidate.
 * @property candidate    The candidate's segment, absent when this tag is only in the baseline.
 * @property subtags      The template's children, aligned. Empty for a plain value.
 */
data class EmvSegmentDiff(
    val tag: String,
    val status: DiffStatus,
    val baseline: EmvSegment?,
    val candidate: EmvSegment?,
    val subtags: List<EmvSubtagDiff> = emptyList(),
    val movedFromTag: String? = null,
) {

    /** Whether the slot changed even though the values did not. */
    val moved: Boolean get() = movedFromTag != null

    /**
     * This segment's contribution to [EmvComparison.changedCount].
     *
     * A template delegates to its subtags so that an account with one edited field reports one
     * difference. A template whose subtags did not frame has none to delegate to, so it answers
     * for itself.
     */
    internal val differenceCount: Int
        get() = when {
            subtags.isNotEmpty() -> subtags.count { it.status != DiffStatus.SAME }
            status == DiffStatus.SAME -> 0
            else -> 1
        }
}

/**
 * One child of a template, aligned against its counterpart.
 *
 * @property tag            The subtag's own two-character tag.
 * @property status         How the two sides relate.
 * @property baselineValue  The baseline's raw value, absent when only the candidate has this subtag.
 * @property candidateValue The candidate's raw value, absent when only the baseline has this subtag.
 */
data class EmvSubtagDiff(
    val tag: String,
    val status: DiffStatus,
    val baselineValue: String?,
    val candidateValue: String?,
)

/** How one aligned pair of fields relates. */
enum class DiffStatus {

    /** Present on both sides with the same raw value. */
    SAME,

    /** Present on both sides with different raw values. */
    CHANGED,

    /** In the first payload only. */
    ONLY_IN_BASELINE,

    /** In the second payload only. */
    ONLY_IN_CANDIDATE,
}

/**
 * The two checksum verdicts.
 *
 * Kept whole rather than reduced to "the checksums differ": a payload whose own tag `63` does not
 * validate is the finding this tool exists to surface, and that is invisible in a comparison of
 * the two expected values alone.
 *
 * @property baseline  The first payload's checksum verdict.
 * @property candidate The second payload's checksum verdict.
 */
data class CrcDiff(
    val baseline: CrcVerification,
    val candidate: CrcVerification,
) {

    /** Whether both payloads carry the checksum their own bytes produce. */
    val bothValid: Boolean get() = baseline.passed && candidate.passed

    /** Whether the two payloads claim the same checksum. */
    val same: Boolean get() = baseline.expected.equals(candidate.expected, ignoreCase = true)
}
