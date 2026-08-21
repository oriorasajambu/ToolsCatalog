package com.minion.scaffold.feature.qrscan.domain.compare

import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.feature.qrscan.domain.ScannedContent

/**
 * Two scanned codes, read against each other.
 *
 * Both sides are always the same format. A comparison across formats is refused before it gets
 * here, because "a Wi-Fi code differs from a payment code in every field" is a true statement that
 * tells nobody anything.
 *
 * @property baseline  The first code scanned — the one being compared *against*.
 * @property candidate The second code scanned.
 * @property fields    How the two decompose, which depends on the format.
 */
internal data class QrComparison(
    val baseline: ScannedContent,
    val candidate: ScannedContent,
    val fields: FieldComparison,
) {

    /**
     * Whether the two codes are the same string.
     *
     * The stronger claim than "every field matches": a payload whose tags were re-encoded in a
     * different order has identical fields and different bytes, and the verdict says so.
     */
    val bytesIdentical: Boolean get() = baseline.payload == candidate.payload

    /**
     * How many fields differ, checksum excluded.
     *
     * Zero for a flat format is not something this type can answer — those rows are built from
     * string resources in the report layer, which owns them — so the flat case reports null and the
     * screen fills it in. See [FieldComparison.Flat].
     */
    val changedCount: Int?
        get() = when (fields) {
            is FieldComparison.Payment -> fields.comparison.changedCount
            FieldComparison.Flat -> null
        }
}

/**
 * What there is to line up, which is not the same question for every format.
 *
 * A payment code has structure worth aligning by tag; the other three are a handful of labelled
 * facts. That asymmetry already exists in the single-code report, where `QrInquiryReportView` has
 * its own layout and the other three share `ReportRowList`.
 */
internal sealed interface FieldComparison {

    /**
     * Both sides are payment codes.
     *
     * @property comparison The structural diff, from `:core:emv`.
     */
    data class Payment(val comparison: EmvComparison) : FieldComparison

    /**
     * Both sides are Wi-Fi credentials, a link, or a contact card.
     *
     * Carries nothing on purpose. For those three the fields *are* the `ReportRow` lists the report
     * views already build from `Resources`, so the diff is taken over those rows where they are
     * defined. Rebuilding them here would be a second rendering of every format, to be kept in step
     * with the first forever — and the one nobody looks at is the one that would go stale.
     */
    data object Flat : FieldComparison
}
