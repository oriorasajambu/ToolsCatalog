package com.minion.scaffold.feature.qrscan.presentation.compare

import android.content.res.Resources
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.core.emv.model.EmvComparison
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.compare.FieldComparison
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison
import com.minion.scaffold.feature.qrscan.presentation.report.ReportRow
import com.minion.scaffold.feature.qrscan.presentation.report.rows
import com.minion.scaffold.feature.qrscan.presentation.report.tagLabel
import com.minion.scaffold.feature.qrscan.presentation.report.webLinkRows

/**
 * The rows a flat-format code shows, whichever flat format it is.
 *
 * The report views' own row builders, not a second set. That is the whole reason those three were
 * widened from `private` to `internal`: a comparison that derived its own fields would be a second
 * rendering of every format, and the first time either was reworded the two would disagree about
 * what a scanned code contains.
 *
 * A payment code has structure rather than rows and never reaches here; the caller has already
 * branched on [FieldComparison].
 */
internal fun ScannedContent.flatReportRows(resources: Resources): List<ReportRow> = when (this) {
    is ScannedContent.Wifi -> credentials.rows(resources)
    is ScannedContent.Web -> webLinkRows(url, resources)
    is ScannedContent.Contact -> card.rows(resources)
    is ScannedContent.Payment -> emptyList()
}

/**
 * How many fields differ, for whichever kind of comparison this is.
 *
 * The payment count comes from `:core:emv`, which computed it while aligning. The flat count has to
 * be taken here, because the rows it counts only exist once string resources have been applied.
 */
internal fun QrComparison.changedCount(resources: Resources): Int = when (fields) {
    is FieldComparison.Payment -> fields.comparison.changedCount
    FieldComparison.Flat -> diffRows(
        baseline = baseline.flatReportRows(resources),
        candidate = candidate.flatReportRows(resources),
    ).changedCount()
}

/** The verdict for this comparison. */
internal fun QrComparison.verdict(resources: Resources): CompareVerdict =
    verdictOf(bytesIdentical = bytesIdentical, changedCount = changedCount(resources))

/**
 * Every field of an EMV comparison as a flat list of labelled changes, for the clipboard.
 *
 * Flattened only for the text export. On screen the segments keep their structure, because a
 * merchant account and the four subtags inside it are one thing a reader is looking at — but a
 * pasted comparison is read as a list, and nesting it would put the interesting line three
 * indents deep.
 */
internal fun EmvComparison.exportLines(resources: Resources): List<ExportLine> = buildList {
    for (segment in segments) {
        val heading = resources.getString(
            R.string.qrscan_segment_heading,
            segment.tag,
            tagLabel(resources, segment.tag),
        )

        if (segment.subtags.isEmpty()) {
            add(
                ExportLine(
                    label = heading,
                    status = segment.status,
                    baselineValue = segment.baseline?.node?.rawValue,
                    candidateValue = segment.candidate?.node?.rawValue,
                ),
            )
            continue
        }

        // A template's own line carries only its identity: its value is the concatenation of the
        // subtags printed under it, and repeating it would double the length of the export.
        for (subtag in segment.subtags) {
            add(
                ExportLine(
                    label = heading + SUBTAG_SEPARATOR + resources.getString(
                        R.string.qrscan_compare_subtag,
                        subtag.tag,
                    ),
                    status = subtag.status,
                    baselineValue = subtag.baselineValue,
                    candidateValue = subtag.candidateValue,
                ),
            )
        }
    }
}

/** The same, for a flat format. */
internal fun List<RowDiff>.exportLines(): List<ExportLine> = map { row ->
    ExportLine(
        label = row.label,
        status = row.status,
        baselineValue = row.baselineValue,
        candidateValue = row.candidateValue,
    )
}

/**
 * One line of a shareable comparison.
 *
 * @property label          What the field is called.
 * @property status         How the two sides relate.
 * @property baselineValue  The first code's value, or null when it has none.
 * @property candidateValue The second code's value, or null when it has none.
 */
internal data class ExportLine(
    val label: String,
    val status: DiffStatus,
    val baselineValue: String?,
    val candidateValue: String?,
)

private const val SUBTAG_SEPARATOR = " / "
