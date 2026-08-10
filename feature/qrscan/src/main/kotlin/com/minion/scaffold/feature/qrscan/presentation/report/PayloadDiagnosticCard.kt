package com.minion.scaffold.feature.qrscan.presentation.report

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.presentation.QrScanError

/**
 * The payload, drawn so the reported position can be found and the damage seen.
 *
 * Renders against the payload that produced [error], never against whatever is currently in the
 * editor. The moment a character is typed every offset shifts, and a highlight that keeps pointing
 * at its old position is worse than none — so [stale] drops the highlighting rather than moving it.
 */
@Composable
internal fun PayloadDiagnosticCard(
    payload: String,
    error: QrScanError,
    stale: Boolean,
    onCopyDiagnostic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (payload.isEmpty()) return

    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val parseError = (error as? QrScanError.Parse)?.error

    val spans = remember(payload, parseError, stale) {
        if (stale || parseError == null) emptyList() else parseError.toGridSpans(payload.length)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(R.string.qrscan_diagnostic_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.qrscan_diagnostic_length, payload.length),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stale) {
                Text(
                    text = stringResource(R.string.qrscan_diagnostic_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OffsetGridText(
                text = payload,
                spans = spans,
                contentDescription = error.describe(resources),
                modifier = Modifier.padding(vertical = dimensionResource(R.dimen.qrscan_spacing_tight)),
            )

            Text(
                text = stringResource(R.string.qrscan_diagnostic_offsets_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (payload.any { it.code !in 0x20..0x7E }) {
                Text(
                    text = stringResource(R.string.qrscan_diagnostic_placeholder_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = { onCopyDiagnostic(diagnosticText(resources, payload, error)) },
            ) {
                Text(text = stringResource(R.string.qrscan_diagnostic_copy))
            }
        }
    }
}

/**
 * The error as spans over the payload.
 *
 * Three runs, painted in order so the later ones win: everything up to the fault reads as consumed,
 * the last good segment is marked, and the fault itself is drawn over the top. Anything after the
 * fault is dimmed, because the parser never looked at it — which is itself worth seeing, since a
 * payload that failed six characters in is a different problem from one that failed near the end.
 */
private fun QrParseError.toGridSpans(payloadLength: Int): List<GridSpan> {
    val lastGood = when (this) {
        is QrParseError.MalformedTlv -> lastGoodSegment
        is QrParseError.LengthOverrun -> lastGoodSegment
        else -> null
    }

    return buildList {
        if (span.endExclusive < payloadLength) {
            add(GridSpan(span.endExclusive, payloadLength, GridSpanStyle.Unreached))
        }
        lastGood?.let {
            add(GridSpan(it.span.start, it.span.endExclusive, GridSpanStyle.Marked))
        }
        add(GridSpan(span.start, span.endExclusive, GridSpanStyle.Faulted))
    }
}

/**
 * The whole diagnosis as plain text, for pasting into a message or a ticket.
 *
 * Built from the same [describe] the card renders, so what is pasted matches what was on screen —
 * the same contract the shareable report already holds itself to. Verbatim and unredacted: a
 * redacted payload cannot be re-parsed by whoever receives it, which is the only reason to send one.
 */
internal fun diagnosticText(
    resources: Resources,
    payload: String,
    error: QrScanError,
): String = buildString {
    appendLine(error.describe(resources))

    (error as? QrScanError.Parse)?.error?.describeContext(resources)?.let(::appendLine)

    appendLine()
    appendLine(resources.getString(R.string.qrscan_diagnostic_length, payload.length))
    appendLine(payload)
}
