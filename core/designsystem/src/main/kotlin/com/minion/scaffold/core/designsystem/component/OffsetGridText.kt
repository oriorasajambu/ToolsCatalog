package com.minion.scaffold.core.designsystem.component

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.minion.scaffold.core.designsystem.R

/** A run of characters and the style to draw it in. Later spans win where they overlap. */
data class OffsetSpan(
    val start: Int,
    val endExclusive: Int,
    val style: SpanStyle,
)

/**
 * A long string laid out in fixed-width rows with a position gutter, so an offset can be found by
 * looking rather than by counting.
 *
 * Domain-free on purpose — it knows nothing about EMV, QR payloads or what any run of characters
 * *means*. Callers hand it resolved [OffsetSpan]s (the scanner turns a parse error into faulted
 * runs; the creator turns each tag into a coloured band), so the grid itself stays a ruler that any
 * feature can reuse.
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
 * Plane occupies two of the units the caller counts but one column on screen. Both are replaced
 * with a placeholder and the layout direction is pinned, so the promise holds. The real characters
 * are still readable elsewhere — this is a ruler, not a viewer.
 *
 * @param text              The string to lay out.
 * @param spans             Runs to style, painted in order so a later one wins on overlap.
 * @param modifier          The [Modifier] for the grid.
 * @param baseColor         The colour any character not covered by a span is drawn in.
 * @param contentDescription Announced in place of the characters, which read one-by-one are useless.
 * @param onOffsetTapped    When non-null, a tap resolves to the payload offset under it and the grid
 *   stops offering text selection (the two gestures conflict); null keeps it a selectable display.
 */
@Composable
fun OffsetGridText(
    text: String,
    spans: List<OffsetSpan>,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
    onOffsetTapped: ((Int) -> Unit)? = null,
) {
    val style = LocalTextStyle.current.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
    )

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The ruler and gutter are guidance, not content, but they still have to be readable: `outline`
    // is a hairline colour that all but vanishes on a dark ground, so the position ruler uses the
    // muted-but-legible `onSurfaceVariant` instead.
    val rulerColor = MaterialTheme.colorScheme.onSurfaceVariant

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

        val annotated = remember(text, spans, baseColor) {
            buildGrid(text = text, spans = spans, baseColor = baseColor)
        }

        val rows = remember(annotated, charsPerRow) {
            (annotated.indices step charsPerRow).map { start ->
                start to minOf(start + charsPerRow, annotated.length)
            }
        }

        // Pinned left-to-right. Without it, a payload containing an RTL run would render its
        // columns in visual rather than logical order and every position in the gutter would be
        // wrong for that row.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        // A screen reader announcing 300 digits one at a time is unusable. The
                        // sentence the caller carries is what a listener needs.
                        contentDescription?.let { this.contentDescription = it }
                    },
            ) {
                RulerRow(
                    charsPerRow = charsPerRow,
                    gutterDigits = gutterDigits,
                    style = style,
                    color = rulerColor,
                )

                GridRows(
                    annotated = annotated,
                    rows = rows,
                    gutterDigits = gutterDigits,
                    style = style,
                    gutterColor = rulerColor,
                    onOffsetTapped = onOffsetTapped,
                )
            }
        }
    }
}

/** The data rows: a position gutter and the styled characters. Selectable unless made tappable. */
@Composable
// A reusable design-system widget -- see the note on PickerField in FormFields.kt.
@Suppress("LongParameterList")
private fun GridRows(
    annotated: AnnotatedString,
    rows: List<Pair<Int, Int>>,
    gutterDigits: Int,
    style: TextStyle,
    gutterColor: Color,
    onOffsetTapped: ((Int) -> Unit)?,
) {
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { (from, to) ->
                Row(modifier = Modifier.clearAndSetSemantics { }) {
                    DisableSelection {
                        Text(
                            text = from.toString().padStart(gutterDigits, ' ') + " ",
                            style = style,
                            color = gutterColor,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }

                    if (onOffsetTapped == null) {
                        Text(
                            text = annotated.subSequence(from, to),
                            style = style,
                            softWrap = false,
                            maxLines = 1,
                        )
                    } else {
                        val layout = remember(from, to) {
                            mutableStateOf<TextLayoutResult?>(null)
                        }
                        Text(
                            text = annotated.subSequence(from, to),
                            style = style,
                            softWrap = false,
                            maxLines = 1,
                            onTextLayout = { layout.value = it },
                            modifier = Modifier.pointerInput(from, to) {
                                detectTapGestures { position ->
                                    val local = layout.value?.getOffsetForPosition(position)
                                        ?: return@detectTapGestures
                                    onOffsetTapped(from + local)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // Selection and tap-to-locate are mutually exclusive gestures; a tappable grid drops selection
    // so a tap lands on a character rather than starting a drag-select.
    if (onOffsetTapped == null) SelectionContainer { body() } else body()
}

/** The column ruler: repeating `0123456789`, so a position within a row needs no counting. */
@Composable
private fun RulerRow(
    charsPerRow: Int,
    gutterDigits: Int,
    style: TextStyle,
    color: Color,
) {
    val ruler = remember(charsPerRow) {
        buildString { repeat(charsPerRow) { append(it % GRID_STEP) } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.ds_grid_ruler_gap))
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

/**
 * Builds the whole string once, so slicing it per row costs nothing and the spans cannot drift
 * between rows.
 */
private fun buildGrid(
    text: String,
    spans: List<OffsetSpan>,
    baseColor: Color,
): AnnotatedString {
    val sanitised = text.map { character ->
        if (character.code in PRINTABLE_ASCII) character else PLACEHOLDER
    }

    return buildAnnotatedString {
        append(sanitised.joinToString(""))

        // Base coat first; the spans below paint over it in order, so a later one wins.
        addStyle(SpanStyle(color = baseColor), 0, length)

        spans.forEach { span ->
            val start = span.start.coerceIn(0, length)
            val end = span.endExclusive.coerceIn(start, length)
            if (start == end) return@forEach

            addStyle(span.style, start, end)
        }
    }
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
