package com.minion.scaffold.feature.qrcreate.presentation.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.minion.scaffold.core.designsystem.component.OffsetGridText
import com.minion.scaffold.core.designsystem.component.OffsetSpan
import com.minion.scaffold.core.designsystem.theme.LocalTagHighlightPalette
import com.minion.scaffold.core.designsystem.theme.cycle
import com.minion.scaffold.core.emv.model.PayloadTag
import com.minion.scaffold.feature.qrcreate.R

/**
 * The generated payload, with every TLV tag in its own colour, and a legend of those tags.
 *
 * Tapping a legend chip focuses that tag — its band stays full-strength while the rest dim, and a
 * detail line spells the tag out — and tapping a run in the payload does the same from the other
 * side. Focusing a template lights its sub-tags too, since a template's own span is mostly the
 * bytes its children occupy.
 *
 * @param payload The generated payload, shown as the offset grid.
 * @param tags    The payload's tags, flattened and located; assumed non-empty by the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagBreakdownView(
    payload: String,
    tags: List<PayloadTag>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTagHighlightPalette.current

    // Nothing to colour with — a palette-less preview, or a payload that did not break down — so
    // fall back to the plain string rather than an empty grid.
    if (palette.bands.isEmpty() || tags.isEmpty()) {
        Text(text = payload, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        return
    }

    var focusedPath by rememberSaveable(payload) { mutableStateOf<String?>(null) }

    // One band per tag in payload order, cycled and nudged so no tag shares a colour with its
    // neighbour — the same assignment the scan report uses.
    val bandFor = remember(tags, palette) {
        val colors = palette.cycle(tags.size)
        tags.indices.associate { index -> tags[index].path to colors[index] }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val spans = remember(bandFor, focusedPath, onSurface, onSurfaceVariant) {
        tags.map { tag ->
            val band = bandFor.getValue(tag.path)
            // Highlighted: fully opaque band and crisp text. Dimmed (something else focused): the
            // band fades and the text mutes, so emphasis reads at a glance without transparency
            // touching the focused tag.
            val style = if (tag.isFocusedBy(focusedPath)) {
                SpanStyle(background = band, color = onSurface)
            } else {
                SpanStyle(background = band.copy(alpha = DIM_ALPHA), color = onSurfaceVariant)
            }
            OffsetSpan(tag.span.start, tag.span.endExclusive, style)
        }
    }

    val tight = dimensionResource(R.dimen.qrcreate_spacing_tight)

    Column(verticalArrangement = Arrangement.spacedBy(tight), modifier = modifier) {
        OffsetGridText(
            text = payload,
            spans = spans,
            contentDescription = stringResource(R.string.qrcreate_breakdown_a11y),
            onOffsetTapped = { offset ->
                // The deepest tag under the tap: children follow their parent in the list, so the
                // last match is the most specific.
                focusedPath = tags.lastOrNull { offset >= it.span.start && offset < it.span.endExclusive }?.path
            },
        )

        // The chips opt out of Material's 48dp minimum touch target.
        //
        // `Surface(onClick)` reserves 48dp of height whatever the chip is padded to, so with a
        // dozen tags the legend was mostly empty space — rows sat 48dp apart while painting about
        // 30dp, which read as several loose lists rather than one block. The rule is there for a
        // good reason and is not worth overriding for a primary control; these are a secondary
        // legend, each chip is far wider than it is tall, and tapping one only highlights a span
        // that tapping the payload above already selects. Padding alone could not fix it: the
        // minimum wins over padding, so the reserved height has to go.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tight),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.qrcreate_chip_row_gap),
                ),
            ) {
                tags.forEach { tag ->
                    TagChip(
                        text = stringResource(R.string.qrcreate_tag_chip, tag.path),
                        band = bandFor.getValue(tag.path),
                        selected = focusedPath == tag.path,
                        onClick = { focusedPath = if (focusedPath == tag.path) null else tag.path },
                    )
                }
            }
        }

        focusedPath?.let { path ->
            tags.firstOrNull { it.path == path }?.let { tag ->
                Text(
                    text = stringResource(
                        R.string.qrcreate_tag_detail,
                        tag.path,
                        tag.label(),
                        tag.describeValue(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A compact, tappable legend entry: the tag's colour behind its id and truncated value. */
@Composable
private fun TagChip(
    text: String,
    band: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = band,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(dimensionResource(R.dimen.qrcreate_chip_border), MaterialTheme.colorScheme.onSurface)
        } else {
            null
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.qrcreate_chip_padding_horizontal),
                vertical = dimensionResource(R.dimen.qrcreate_chip_padding_vertical),
            ),
        )
    }
}

/** Whether this tag is emphasised: nothing focused (all lit), itself, or its template's focus. */
private fun PayloadTag.isFocusedBy(focusedPath: String?): Boolean =
    focusedPath == null || path == focusedPath || path.startsWith("$focusedPath.")

/** How faint a non-focused tag's band goes when one tag is focused. */
private const val DIM_ALPHA = 0.25f
