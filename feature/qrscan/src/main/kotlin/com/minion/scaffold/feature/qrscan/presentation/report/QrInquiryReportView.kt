package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.minion.scaffold.core.designsystem.component.OffsetGridText
import com.minion.scaffold.core.designsystem.component.OffsetSpan
import com.minion.scaffold.core.designsystem.component.QrCodeImage
import com.minion.scaffold.core.designsystem.theme.LocalTagHighlightPalette
import com.minion.scaffold.core.designsystem.theme.cycle
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.core.emv.model.EmvSegment
import com.minion.scaffold.core.emv.model.Nesting
import com.minion.scaffold.core.emv.model.PayloadTag
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.emv.model.TlvNode
import com.minion.scaffold.core.emv.usecase.highlightTags

/**
 * The decoded payload: the source QR, every segment, then the checksum verdict.
 *
 * The payload and its segment cards share one colour language with the create screen — each tag is
 * a coloured band in the payload and a matching chip on its card. Tapping a segment card (or a run
 * in the payload) focuses that tag: its band stays lit while the rest dim, so a card and the
 * characters it decodes can be found from one another.
 *
 * @param onCopy receives the exact text to put on the clipboard. Every copy control on this
 *   screen resolves its own text and hands over a finished string, so there is one place that
 *   touches the clipboard rather than one per control.
 */
@Composable
internal fun QrInquiryReportView(
    report: QrInquiryReport,
    onCopy: (String) -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val palette = LocalTagHighlightPalette.current

    val tags = remember(report) { report.highlightTags() }

    // One band per tag in payload order, the same cycled-and-nudged assignment the create screen
    // uses. Empty outside a theme, which the payload and chips read as "no colour, plain text".
    val bandFor: Map<String, Color> = remember(tags, palette) {
        if (palette.bands.isEmpty()) {
            emptyMap()
        } else {
            val colors = palette.cycle(tags.size)
            tags.indices.associate { index -> tags[index].path to colors[index] }
        }
    }

    // Which tag is focused, if any. View state, so it lives here rather than in the ViewModel; keyed
    // on the payload so a freshly scanned code starts with nothing focused.
    var focusedPath by rememberSaveable(report.payload) { mutableStateOf<String?>(null) }
    val onFocus: (String?) -> Unit = { path -> focusedPath = if (focusedPath == path) null else path }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        item(key = PAYLOAD_KEY) {
            PayloadCard(
                payload = report.payload,
                tags = tags,
                bandFor = bandFor,
                focusedPath = focusedPath,
                onFocus = onFocus,
                onCopy = onCopy,
            )
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
            SegmentCard(
                segment = segment,
                bandFor = bandFor,
                focusedPath = focusedPath,
                onFocus = onFocus,
                onCopy = onCopy,
            )
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

        item(key = FOOTER_KEY) {
            ReportFooter(onCompare = onCompare)
        }
    }
}

@Composable
// The payload with its tag spans, the highlight colours, and which span is focused. The colours
// are computed once by the caller for the whole report, so they arrive rather than being derived.
@Suppress("LongParameterList")
private fun PayloadCard(
    payload: String,
    tags: List<PayloadTag>,
    bandFor: Map<String, Color>,
    focusedPath: String?,
    onFocus: (String?) -> Unit,
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

            if (bandFor.isEmpty()) {
                Text(
                    text = payload,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                val onSurface = MaterialTheme.colorScheme.onSurface
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

                val spans = remember(tags, bandFor, focusedPath, onSurface, onSurfaceVariant) {
                    tags.map { tag ->
                        val band = bandFor.getValue(tag.path)
                        // Highlighted: opaque band and crisp text. Dimmed: both fade, so the focused
                        // tag reads at a glance without transparency touching it.
                        val style = if (tag.isFocusedBy(focusedPath)) {
                            SpanStyle(background = band, color = onSurface)
                        } else {
                            SpanStyle(background = band.copy(alpha = DIM_ALPHA), color = onSurfaceVariant)
                        }
                        OffsetSpan(tag.span.start, tag.span.endExclusive, style)
                    }
                }

                OffsetGridText(
                    text = payload,
                    spans = spans,
                    onOffsetTapped = { offset ->
                        // The deepest tag under the tap: children follow their parent in the list,
                        // so the last match is the most specific.
                        onFocus(
                            tags.lastOrNull {
                                offset >= it.span.start && offset < it.span.endExclusive
                            }?.path,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: EmvSegment,
    bandFor: Map<String, Color>,
    focusedPath: String?,
    onFocus: (String?) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val subtagIndent = dimensionResource(R.dimen.qrscan_subtag_indent)

    val path = segment.node.tag
    val band = bandFor[path]
    val selected = focusedPath == path

    val border = if (selected) {
        BorderStroke(dimensionResource(R.dimen.qrscan_selected_border), MaterialTheme.colorScheme.onSurface)
    } else {
        null
    }

    // Clickable only when there is a colour to focus — a palette-less preview leaves the card inert.
    val cardModifier = modifier.fillMaxWidth().let {
        if (band != null) it.clickable { onFocus(path) } else it
    }

    Card(modifier = cardModifier, border = border) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            CardHeading(
                text = tagLabel(resources, segment.node.tag),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                onCopy = { onCopy(segment.node.rawValue) },
                leading = band?.let { { TagChip(text = path, band = it) } },
            )

            Text(
                text = segment.valueText(),
                style = MaterialTheme.typography.bodyMedium,
            )

            // Low emphasis and deliberately not an error tint. A tag in the 26–51 range holding a
            // plain identifier rather than sub-segments is ordinary in live payloads, so this fires
            // on perfectly good codes — dressed as a warning it would train people to ignore it.
            // What it is for is the case where a template's insides *are* damaged, which used to
            // be completely silent.
            if (segment.node.nesting == Nesting.Unframed) {
                Text(
                    text = stringResource(R.string.qrscan_report_unframed_template),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (child in segment.node.children) {
                val childPath = "$path.${child.tag}"
                SubtagRow(
                    child = child,
                    band = bandFor[childPath],
                    selected = focusedPath == childPath,
                    onFocus = { onFocus(childPath) },
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
    band: Color?,
    selected: Boolean,
    onFocus: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val gap = dimensionResource(R.dimen.qrscan_chip_gap)

    val rowModifier = modifier
        .fillMaxWidth()
        .let { if (band != null) it.clickable { onFocus() } else it }
        .let {
            if (selected) {
                it.border(
                    width = dimensionResource(R.dimen.qrscan_selected_border),
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.small,
                )
            } else {
                it
            }
        }

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (band != null) {
                    TagChip(text = child.tag, band = band)
                    Spacer(modifier = Modifier.width(gap))
                }
                Text(
                    text = child.subtagLabel(resources),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** A small filled chip carrying a tag's path in its band colour, tying a card to its payload run. */
@Composable
private fun TagChip(text: String, band: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = band,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.qrscan_chip_padding_horizontal),
                vertical = dimensionResource(R.dimen.qrscan_chip_padding_vertical),
            ),
        )
    }
}

/** A title with its copy control, and an optional leading chip, laid out so long titles wrap. */
@Composable
private fun CardHeading(
    text: String,
    style: TextStyle,
    color: Color,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.qrscan_chip_gap)))
        }
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

/** Whether this tag is emphasised: nothing focused (all lit), itself, or its template's focus. */
private fun PayloadTag.isFocusedBy(focusedPath: String?): Boolean =
    focusedPath == null || path == focusedPath || path.startsWith("$focusedPath.")

/** How faint a non-focused tag's band and text go when one tag is focused. */
private const val DIM_ALPHA = 0.25f

private const val PAYLOAD_KEY = "payload"
private const val SEGMENTS_HEADER_KEY = "segments-header"
private const val INTEGRITY_HEADER_KEY = "integrity-header"
private const val INTEGRITY_KEY = "integrity"
private const val FOOTER_KEY = "footer"
