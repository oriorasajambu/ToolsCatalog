package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import com.minion.scaffold.feature.qrscan.domain.ScannedContent

/**
 * Whatever was scanned, as text for the clipboard and the share sheet.
 *
 * One entry point so the copy and share effects do not each need to know which formats exist —
 * adding a third means adding a branch here and nowhere else.
 */
internal fun ScannedContent.toPlainText(resources: Resources): String = when (this) {
    is ScannedContent.Payment -> report.toPlainText(resources)
    is ScannedContent.Wifi -> credentials.toPlainText(resources)
    is ScannedContent.Web -> webLinkPlainText(url, resources)
    is ScannedContent.Contact -> card.toPlainText(resources)
}
