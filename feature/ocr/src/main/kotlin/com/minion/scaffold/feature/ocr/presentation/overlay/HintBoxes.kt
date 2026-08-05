package com.minion.scaffold.feature.ocr.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Outlines where text is in the live viewfinder.
 *
 * Aiming feedback only — the recognised strings are deliberately never drawn here, because
 * analysis-resolution readings of small text are wrong often enough that showing them would
 * mislead the user in the moment before they capture. See `OcrAnalyzer`.
 *
 * Fixed colour rather than a theme one: it sits over a live camera feed, where a surface-derived
 * colour has nothing to contrast against.
 */
@Composable
internal fun HintBoxes(
    boxes: List<Rect>,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = with(LocalDensity.current) { STROKE.toPx() }
    val corner = with(LocalDensity.current) { CORNER.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        for (box in boxes) {
            drawRoundRect(
                color = BOX_COLOR,
                topLeft = Offset(box.left, box.top),
                size = Size(box.width, box.height),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private val STROKE = 2.dp
private val CORNER = 4.dp

/** Over a live camera feed, so a fixed colour rather than a theme one. */
private val BOX_COLOR = Color.White.copy(alpha = 0.85f)
