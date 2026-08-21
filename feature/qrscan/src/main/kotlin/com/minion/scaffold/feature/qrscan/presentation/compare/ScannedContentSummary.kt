package com.minion.scaffold.feature.qrscan.presentation.compare

import android.content.res.Resources
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import com.minion.scaffold.feature.qrscan.domain.ScannedFormat
import com.minion.scaffold.feature.qrscan.domain.format

/**
 * A pinned code, named in a few words.
 *
 * Takes a [Resources] rather than being `@Composable`, for the reason `EmvLabels` gives: the same
 * wording has to reach the shareable plain text, and two implementations of one label drift the
 * first time either is reworded.
 *
 * The identifying field, not an excerpt of the payload. Every Indonesian QRIS code opens with the
 * same fourteen characters, so a prefix of the payload would tell a user nothing about which of
 * the two stickers on the counter they had pinned.
 */
internal fun ScannedContent.summaryTitle(resources: Resources): String = resources.getString(
    R.string.qrscan_compare_summary,
    resources.getString(format.labelRes()),
    identifyingValue().ifBlank { payload.take(EXCERPT_LENGTH) },
)

/** What this kind of code is called, in the middle of a sentence. */
internal fun ScannedFormat.labelRes(): Int = when (this) {
    ScannedFormat.Payment -> R.string.qrscan_format_payment
    ScannedFormat.Wifi -> R.string.qrscan_format_wifi
    ScannedFormat.Web -> R.string.qrscan_format_web
    ScannedFormat.Contact -> R.string.qrscan_format_contact
}

/** The one field most likely to tell two codes of the same kind apart. */
private fun ScannedContent.identifyingValue(): String = when (this) {
    is ScannedContent.Payment -> report.segments
        .firstOrNull { it.node.tag == TAG_MERCHANT_NAME }
        ?.node
        ?.rawValue
        .orEmpty()

    is ScannedContent.Wifi -> credentials.ssid
    is ScannedContent.Web -> url.hostForDisplay()
    is ScannedContent.Contact -> card.formattedName
}

/**
 * The host part of a link, for a label.
 *
 * Deliberately a display trim rather than a parse: `:core:url` keeps its scheme and host helpers
 * `internal`, and widening them so a banner can shorten a string would expose format internals to
 * every consumer of that module. Nothing depends on this being exactly right — it falls back to the
 * whole address, which is never wrong, only long.
 */
private fun String.hostForDisplay(): String = substringAfter("://")
    .substringBefore('/')
    .ifBlank { this }

/**
 * The merchant name.
 *
 * Spelled out rather than imported: `:core:emv` keeps its tag catalog `internal`, so the numbers
 * a presentation label needs are declared where they are used — the same way `EmvLabels` does.
 */
private const val TAG_MERCHANT_NAME = "59"

/** Enough of a payload to tell two apart when there is no field worth naming. */
private const val EXCERPT_LENGTH = 24
