package com.minion.scaffold.feature.qrscan.presentation.compare

import android.content.res.Resources
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.presentation.CompareRejection

/**
 * The one line a reader takes in before anything else.
 *
 * Three states rather than two. A payment code re-encoded with its tags in a different order is a
 * different string carrying identical values, and acquirers do that routinely — so a two-state
 * verdict would report "different" on a pair that is functionally the same code, which is the
 * false alarm that would cost this tool its credibility.
 */
internal sealed interface CompareVerdict {

    /** The same string on both sides. */
    data object Identical : CompareVerdict

    /** Every field matched; the bytes did not. Reordered tags, a moved account, a new checksum. */
    data object Equivalent : CompareVerdict

    /**
     * The two codes say different things.
     *
     * @property changedCount How many fields changed, were added, or were removed. The checksum is
     *   not among them: it is derived, so counting it would report every one-field edit as two.
     */
    data class Different(val changedCount: Int) : CompareVerdict
}

/**
 * Reaches a verdict from a difference count.
 *
 * One implementation, two callers: the payment path passes the count `:core:emv` produced, the flat
 * path passes the count of its own changed rows. Deriving the verdict twice is how the two would
 * come to disagree about what "equivalent" means.
 *
 * @param bytesIdentical Whether the two payloads are the same string.
 * @param changedCount   How many fields differ.
 */
internal fun verdictOf(bytesIdentical: Boolean, changedCount: Int): CompareVerdict = when {
    bytesIdentical -> CompareVerdict.Identical
    changedCount == 0 -> CompareVerdict.Equivalent
    else -> CompareVerdict.Different(changedCount)
}

/** The verdict in words. */
internal fun CompareVerdict.describe(resources: Resources): String = when (this) {
    CompareVerdict.Identical -> resources.getString(R.string.qrscan_compare_verdict_identical)
    CompareVerdict.Equivalent -> resources.getString(R.string.qrscan_compare_verdict_equivalent)
    is CompareVerdict.Different -> resources.getQuantityString(
        R.plurals.qrscan_compare_verdict_different,
        changedCount,
        changedCount,
    )
}

/** What one aligned pair's status is called. */
internal fun DiffStatus.describe(resources: Resources): String = resources.getString(
    when (this) {
        DiffStatus.SAME -> R.string.qrscan_compare_status_same
        DiffStatus.CHANGED -> R.string.qrscan_compare_status_changed
        DiffStatus.ONLY_IN_BASELINE -> R.string.qrscan_compare_status_only_baseline
        DiffStatus.ONLY_IN_CANDIDATE -> R.string.qrscan_compare_status_only_candidate
    },
)

/** Why a second code was turned away, in words. */
internal fun CompareRejection.describe(resources: Resources): String = when (this) {
    is CompareRejection.FormatMismatch ->
        resources.getString(
            R.string.qrscan_compare_reject_format,
            resources.getString(found.labelRes()),
            resources.getString(expected.labelRes()),
        )

    CompareRejection.Unreadable ->
        resources.getString(R.string.qrscan_compare_reject_unreadable)
}
