package com.minion.scaffold.feature.qrscan.presentation.compare

import android.content.res.Resources
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.compare.FieldComparison
import com.minion.scaffold.feature.qrscan.domain.compare.QrComparison

/**
 * A comparison as plain text, for the clipboard and the share sheet.
 *
 * Built from the same labels and the same verdict the screen renders with, so what somebody pastes
 * into a chat is what they were looking at — the reason `ReportPlainText` takes a [Resources] as
 * well.
 *
 * Only the differences are listed. A pasted comparison exists to be read by someone who was not
 * holding either code, and sixty unchanged rows would bury the two lines that are the message. The
 * verdict at the top already states how many there are, so nothing is hidden by their absence.
 */
internal fun QrComparison.toPlainText(resources: Resources): String = buildString {
    appendLine(resources.getString(R.string.qrscan_compare_heading))
    appendLine(verdict(resources).describe(resources))
    appendLine()

    appendLine(
        "${resources.getString(R.string.qrscan_compare_side_baseline)}: " +
            baseline.summaryTitle(resources),
    )
    appendLine(
        "${resources.getString(R.string.qrscan_compare_side_candidate)}: " +
            candidate.summaryTitle(resources),
    )

    val differences = exportLines(resources).filter { it.status != DiffStatus.SAME }
    if (differences.isEmpty()) return@buildString

    appendLine()
    for (line in differences) {
        appendLine("- ${line.label}: ${line.valueText(resources)}")
    }
}

/** Every field of this comparison, whichever kind it is. */
private fun QrComparison.exportLines(resources: Resources): List<ExportLine> = when (fields) {
    is FieldComparison.Payment -> fields.comparison.exportLines(resources)

    FieldComparison.Flat -> diffRows(
        baseline = baseline.flatReportRows(resources),
        candidate = candidate.flatReportRows(resources),
    ).exportLines()
}

/**
 * The two sides on one line.
 *
 * A one-sided field says which side it is on rather than printing a blank against a dash: a reader
 * of the pasted text has neither code in front of them, and "(only in the second)" is the whole
 * finding.
 */
private fun ExportLine.valueText(resources: Resources): String = when (status) {
    DiffStatus.CHANGED -> resources.getString(
        R.string.qrscan_compare_change,
        baselineValue.orEmpty(),
        candidateValue.orEmpty(),
    )

    DiffStatus.ONLY_IN_BASELINE, DiffStatus.ONLY_IN_CANDIDATE -> resources.getString(
        R.string.qrscan_compare_line_only,
        baselineValue ?: candidateValue.orEmpty(),
        status.describe(resources),
    )

    // Unchanged rows are filtered out before this is reached; a value on its own is the only
    // sensible thing to print if one ever is not.
    DiffStatus.SAME -> baselineValue.orEmpty()
}
