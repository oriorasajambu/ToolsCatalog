package com.minion.scaffold.feature.qrscan.presentation.report

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import com.minion.scaffold.core.designsystem.component.OffsetSpan

/**
 * How one run of characters in the diagnostic grid is drawn.
 *
 * These are the scanner's four *meanings*; the shared [OffsetSpan] renderer only knows colours, so
 * [toSpanStyle] resolves a meaning against the theme before handing it over.
 */
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

/** A run of characters and what it means. Later spans win where they overlap. */
internal data class GridSpan(
    val start: Int,
    val endExclusive: Int,
    val style: GridSpanStyle,
)

/** Resolves each meaning to a concrete [OffsetSpan] the shared grid can paint. */
internal fun List<GridSpan>.toOffsetSpans(scheme: ColorScheme): List<OffsetSpan> {
    val colors = scheme.toGridColors()
    return map { OffsetSpan(it.start, it.endExclusive, it.style.toSpanStyle(colors)) }
}

/** The four colours the grid draws with, resolved from the theme once. */
private data class GridColors(
    val consumed: Color,
    val marked: Color,
    val markedBackground: Color,
    val faulted: Color,
    val faultedBackground: Color,
    val unreached: Color,
)

/** Plain, not `@Composable` — it only reads the scheme it is handed, so it can run inside `remember`. */
private fun ColorScheme.toGridColors() = GridColors(
    consumed = onSurfaceVariant,
    marked = onSecondaryContainer,
    markedBackground = secondaryContainer,
    faulted = onErrorContainer,
    faultedBackground = errorContainer,
    unreached = outline,
)

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
