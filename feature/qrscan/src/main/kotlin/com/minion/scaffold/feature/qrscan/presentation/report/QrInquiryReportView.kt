package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.minion.scaffold.core.designsystem.component.QrCodeImage
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.EmvSegment
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TlvNode

/**
 * The decoded payload: the source QR, every segment, then the checksum verdict.
 *
 * @param onCopy receives the exact text to put on the clipboard. Every copy control on this
 *   screen resolves its own text and hands over a finished string, so there is one place that
 *   touches the clipboard rather than one per control.
 */
@Composable
internal fun QrInquiryReportView(
    report: QrInquiryReport,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = PAYLOAD_KEY) {
            PayloadCard(payload = report.payload, onCopy = onCopy)
        }

        item(key = SEGMENTS_HEADER_KEY) {
            Text(
                text = stringResource(R.string.qrscan_report_segments),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Keyed by position as well as tag. A tag is unique in a valid payload, but this tool
        // exists to be pointed at invalid ones, and a duplicate key crashes the list.
        itemsIndexed(
            items = report.segments,
            key = { index, segment -> "$index-${segment.node.tag}" },
        ) { _, segment ->
            SegmentCard(segment = segment, onCopy = onCopy)
        }

        item(key = INTEGRITY_HEADER_KEY) {
            Text(
                text = stringResource(R.string.qrscan_report_integrity),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item(key = INTEGRITY_KEY) {
            IntegrityCard(report = report, onCopy = onCopy)
        }
    }
}

@Composable
private fun PayloadCard(
    payload: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            CardHeading(
                text = stringResource(R.string.qrscan_report_payload),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                onCopy = { onCopy(payload) },
            )

            QrCodeImage(
                payload = payload,
                contentDescription = stringResource(R.string.qrscan_report_qr_image),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(dimensionResource(R.dimen.qrscan_qr_size)),
            )

            Text(
                text = payload,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SegmentCard(
    segment: EmvSegment,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val subtagIndent = dimensionResource(R.dimen.qrscan_subtag_indent)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            CardHeading(
                text = stringResource(
                    R.string.qrscan_segment_heading,
                    segment.node.tag,
                    tagLabel(resources, segment.node.tag),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                onCopy = { onCopy(segment.node.rawValue) },
            )

            Text(
                text = segment.valueText(),
                style = MaterialTheme.typography.bodyMedium,
            )

            for (child in segment.node.children) {
                SubtagRow(
                    child = child,
                    onCopy = onCopy,
                    modifier = Modifier.padding(start = subtagIndent),
                )
            }
        }
    }
}

/**
 * The raw value and what it means, on one line: `01 → V1`.
 *
 * One [Text] rather than two stacked, with the value in monospace and the meaning not — a single
 * annotated string keeps them on the same line while still letting the value stay column-aligned
 * for anyone counting characters.
 */
@Composable
private fun EmvSegment.valueText(): AnnotatedString {
    val resources = LocalResources.current
    val meaning = interpretation.describe(resources)

    return buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(node.rawValue)
        }
        if (meaning != null) {
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append(stringResource(R.string.qrscan_report_meaning, meaning))
            }
        }
    }
}

@Composable
private fun SubtagRow(
    child: TlvNode,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = child.subtagLabel(resources),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = child.rawValue,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Subtag get their own copy button because they are usually the thing worth copying —
        // the merchant identifier under tag 26 is what someone is actually chasing, not the whole
        // concatenated template.
        CopyButton(onClick = { onCopy(child.rawValue) })
    }
}

@Composable
private fun IntegrityCard(
    report: QrInquiryReport,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val passed = report.crc.passed

    val expected = stringResource(R.string.qrscan_crc_expected, report.crc.expected)
    val actual = stringResource(R.string.qrscan_crc_actual, report.crc.actual)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (passed) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            CardHeading(
                text = stringResource(
                    if (passed) R.string.qrscan_crc_passed else R.string.qrscan_crc_failed,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                onCopy = { onCopy(integrityClipText(resources, report)) },
            )
            Text(
                text = expected,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = actual,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/** A title with its copy control, laid out so long titles wrap rather than push the button off. */
@Composable
private fun CardHeading(
    text: String,
    style: TextStyle,
    color: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
        CopyButton(onClick = onCopy)
    }
}

@Composable
private fun CopyButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = stringResource(R.string.qrscan_copy_value),
        )
    }
}

private const val PAYLOAD_KEY = "payload"
private const val SEGMENTS_HEADER_KEY = "segments-header"
private const val INTEGRITY_HEADER_KEY = "integrity-header"
private const val INTEGRITY_KEY = "integrity"
