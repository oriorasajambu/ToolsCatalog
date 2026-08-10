package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import com.minion.scaffold.feature.qrscan.R

/** How one run of characters is drawn. */
internal enum class GridSpanStyle {

    /** Read successfully. The default for anything not otherwise claimed. */
    Consumed,

    /** Worth looking at, but not the fault — the last thing that worked. */
    Marked,

    /** The characters the error is about. */
    Faulted,

    /** Never reached, because parsing stopped before here. */
    Unreached,
}

/** A run of characters and how to draw it. Later spans win where they overlap. */
internal data class GridSpan(
    val start: Int,
    val endExclusive: Int,
    val style: GridSpanStyle,
)

/**
 * A long string laid out in fixed-width rows with a position gutter, so an offset can be found by
 * looking rather than by counting.
 *
 * Domain-free on purpose — it knows nothing about EMV. [PayloadDiagnosticCard] is what turns a parse
 * error into the spans this draws.
 *
 * ## Why it measures instead of guessing
 *
 * A monospace font and a modifier are not enough to guarantee a fixed number of characters per row:
 * `Text` still soft-wraps by measured width, so the count would change with screen size and font
 * scale and the gutter would immediately start lying. The advance width of one character is measured
 * once, and the row length derived from it — then **rounded down to a multiple of ten**, because
 * positions are decimal and "row 20, six along" should not require multiplication.
 *
 * ## Why the text is sanitised
 *
 * The grid's whole promise is that column N holds position N. Two things break that on real
 * payloads: a right-to-left run reorders visually, and a character outside the Basic Multilingual
 * Plane occupies two of the units the parser counts but one column on screen. Both are replaced
 * with a placeholder and the layout direction is pinned, so the promise holds. The real characters
 * are still readable in the segment rows and in the editable field — this is a ruler, not a viewer.
 */
@Composable
internal fun OffsetGridText(
    text: String,
    spans: List<GridSpan>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val style = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
    )

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Widest position label, plus a space. Derived from the text so a short payload does not carry
    // a gutter sized for one ten times longer.
    val gutterDigits = maxOf(text.length.toString().length, MIN_GUTTER_DIGITS)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val advancePx = remember(style, measurer) {
            val probe = measurer.measure(
                text = AnnotatedString("0".repeat(PROBE_LENGTH)),
                style = style,
                softWrap = false,
            )
            probe.size.width.toFloat() / PROBE_LENGTH
        }

        val charsPerRow = remember(advancePx, maxWidth, gutterDigits, density) {
            val availablePx = with(density) { maxWidth.toPx() } -
                advancePx * (gutterDigits + GUTTER_GAP_CHARS)
            val fits = (availablePx / advancePx).toInt()
            (fits / GRID_STEP * GRID_STEP).coerceAtLeast(GRID_STEP)
        }

        val annotated = remember(text, spans, scheme) {
            buildGrid(text = text, spans = spans, scheme = scheme.toGridColors())
        }

        val rows = remember(annotated, charsPerRow) {
            (annotated.indices step charsPerRow).map { start ->
                start to minOf(start + charsPerRow, annotated.length)
            }
        }

        // Pinned left-to-right. Without it, a payload containing an RTL run would render its
        // columns in visual rather than logical order and every position in the gutter would be
        // wrong for that row.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        // A screen reader announcing 300 digits one at a time is unusable. The
                        // sentence the card already carries is what a listener needs.
                        contentDescription?.let { this.contentDescription = it }
                    },
            ) {
                RulerRow(
                    charsPerRow = charsPerRow,
                    gutterDigits = gutterDigits,
                    style = style,
                    color = scheme.outline,
                )

                SelectionContainer {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        rows.forEach { (from, to) ->
                            Row(modifier = Modifier.clearAndSetSemantics { }) {
                                DisableSelection {
                                    Text(
                                        text = from.toString().padStart(gutterDigits, ' ') + " ",
                                        style = style,
                                        color = scheme.outline,
                                        softWrap = false,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    text = annotated.subSequence(from, to),
                                    style = style,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The column ruler: repeating `0123456789`, so a position within a row needs no counting. */
@Composable
private fun RulerRow(
    charsPerRow: Int,
    gutterDigits: Int,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    val ruler = remember(charsPerRow) {
        buildString { repeat(charsPerRow) { append(it % GRID_STEP) } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.qrscan_spacing_tight))
            .clearAndSetSemantics { },
    ) {
        Text(
            text = " ".repeat(gutterDigits) + " ",
            style = style,
            color = color,
            softWrap = false,
            maxLines = 1,
        )
        Text(text = ruler, style = style, color = color, softWrap = false, maxLines = 1)
    }
}

/** The four colours the grid draws with, resolved from the theme once. */
private data class GridColors(
    val consumed: androidx.compose.ui.graphics.Color,
    val marked: androidx.compose.ui.graphics.Color,
    val markedBackground: androidx.compose.ui.graphics.Color,
    val faulted: androidx.compose.ui.graphics.Color,
    val faultedBackground: androidx.compose.ui.graphics.Color,
    val unreached: androidx.compose.ui.graphics.Color,
)

/** Plain, not `@Composable` — it only reads the scheme it is handed, so it can run inside `remember`. */
private fun androidx.compose.material3.ColorScheme.toGridColors() = GridColors(
    consumed = onSurfaceVariant,
    marked = onSecondaryContainer,
    markedBackground = secondaryContainer,
    faulted = onErrorContainer,
    faultedBackground = errorContainer,
    unreached = outline,
)

/**
 * Builds the whole string once, so slicing it per row costs nothing and the spans cannot drift
 * between rows.
 */
private fun buildGrid(
    text: String,
    spans: List<GridSpan>,
    scheme: GridColors,
): AnnotatedString {
    val sanitised = text.map { character ->
        if (character.code in PRINTABLE_ASCII) character else PLACEHOLDER
    }

    return buildAnnotatedString {
        append(sanitised.joinToString(""))

        // Base coat first; the spans below paint over it in order, so a later one wins.
        addStyle(SpanStyle(color = scheme.consumed), 0, length)

        spans.forEach { span ->
            val start = span.start.coerceIn(0, length)
            val end = span.endExclusive.coerceIn(start, length)
            if (start == end) return@forEach

            addStyle(span.style.toSpanStyle(scheme), start, end)
        }
    }
}

private fun GridSpanStyle.toSpanStyle(scheme: GridColors): SpanStyle = when (this) {
    GridSpanStyle.Consumed -> SpanStyle(color = scheme.consumed)
    GridSpanStyle.Marked -> SpanStyle(color = scheme.marked, background = scheme.markedBackground)
    GridSpanStyle.Faulted -> SpanStyle(
        color = scheme.faulted,
        background = scheme.faultedBackground,
        textDecoration = TextDecoration.Underline,
    )

    GridSpanStyle.Unreached -> SpanStyle(color = scheme.unreached)
}

/** Space through tilde — everything that occupies exactly one monospace column. */
private val PRINTABLE_ASCII = 0x20..0x7E

/** Stands in for anything that does not, so a row keeps one column per position. */
private const val PLACEHOLDER = '·'

/** Row lengths are multiples of this, so a position within a row is read rather than calculated. */
private const val GRID_STEP = 10

/** Characters measured to derive one character's advance. Long enough to average out rounding. */
private const val PROBE_LENGTH = 20

/** Gutter width in characters beyond the digits themselves. */
private const val GUTTER_GAP_CHARS = 2

private const val MIN_GUTTER_DIGITS = 3
