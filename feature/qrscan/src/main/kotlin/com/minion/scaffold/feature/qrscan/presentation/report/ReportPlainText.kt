package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TlvNode

/**
 * The report as plain text, for the clipboard and the share sheet.
 *
 * Built from the same [tagLabel] and [describe] functions the screen renders with, so what gets
 * pasted into a bug report is what the person filing it was looking at.
 */
internal fun QrInquiryReport.toPlainText(resources: Resources): String = buildString {
    appendLine(resources.getString(R.string.qrscan_report_title))
    appendLine()
    appendLine(resources.getString(R.string.qrscan_report_payload))
    appendLine(payload)
    appendLine()

    appendLine(resources.getString(R.string.qrscan_report_segments))
    for (segment in segments) {
        val heading = resources.getString(
            R.string.qrscan_segment_heading,
            segment.node.tag,
            tagLabel(resources, segment.node.tag),
        )

        // Value and meaning on one line, matching the cards on screen. Pasting a report into a
        // chat should read the way the screen did.
        val meaning = segment.interpretation.describe(resources)
            ?.let { " " + resources.getString(R.string.qrscan_report_meaning, it) }
            .orEmpty()

        appendLine("- $heading: ${segment.node.rawValue}$meaning")

        for (child in segment.node.children) {
            appendLine("  - ${child.subtagLabel(resources)}: ${child.rawValue}")
        }
    }

    appendLine()
    appendLine(resources.getString(R.string.qrscan_report_integrity))
    appendLine(
        "- " + resources.getString(
            if (crc.passed) R.string.qrscan_crc_passed else R.string.qrscan_crc_failed,
        ),
    )
    appendLine(integrityClipText(resources, this@toPlainText).prependIndent("  "))
}

/**
 * The checksum verdict on its own, for the integrity card's copy button.
 *
 * Shared with [toPlainText] so the two cannot disagree about what a checksum result looks like.
 */
internal fun integrityClipText(resources: Resources, report: QrInquiryReport): String = listOf(
    resources.getString(R.string.qrscan_crc_expected, report.crc.expected),
    resources.getString(R.string.qrscan_crc_actual, report.crc.actual),
).joinToString(separator = "\n")

/**
 * A subtag's heading: its tag and the length the template declared for it.
 *
 * Subtag are not named. Their meaning depends on which template they sit in — `01` under a
 * merchant account template is a merchant identifier, under tag `62` it is a bill number — so a
 * single label per tag number would be wrong more often than right.
 */
internal fun TlvNode.subtagLabel(resources: Resources): String =
    resources.getString(R.string.qrscan_subtag, tag, length)
