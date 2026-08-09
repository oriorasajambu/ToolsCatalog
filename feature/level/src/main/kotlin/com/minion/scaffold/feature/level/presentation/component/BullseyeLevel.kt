package com.minion.scaffold.feature.level.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.minion.scaffold.feature.level.R
import kotlin.math.hypot

/**
 * The bubble, drawn as a bullseye vial.
 *
 * ## Reading state in the draw phase, not the composable body
 *
 * This is the first screen in the app driven at ~50Hz, and that changes how the state has to reach
 * it. The tilt is passed as `State<T>` **lambdas that are read inside [Modifier.drawBehind]**, so
 * the read is deferred to the draw phase: a new reading re-runs drawing only, skipping composition
 * and layout entirely. Reading the same values in the composable body would recompose this subtree
 * fifty times a second for a picture that could have been redrawn directly.
 *
 * ## The bubble position needs no trigonometry
 *
 * A real spherical vial's bubble displaces by `R·sin θ` towards the high side — and `sin θ` in each
 * axis is exactly the in-plane part of the up-vector. So the offset is linear in the numbers the
 * pipeline already produced, and the drawing cannot drift from the angles printed beside it.
 *
 * The `-y`: Compose's canvas has `+y` pointing **down**, while the device's `+y` axis points up the
 * screen. Without the negation the bubble moves the wrong way when the phone is tipped away, which
 * looks almost right and is completely wrong.
 *
 * ## Two scales
 *
 * A single linear scale wide enough to be useful (±10°) makes the last tenth of a degree a
 * sub-pixel movement, so the bubble appears dead exactly where all the adjusting happens. The scale
 * therefore expands as the reading approaches level, animated so the change is legible rather than
 * a jump.
 */
@Composable
internal fun BullseyeLevel(
    bubbleX: () -> Double,
    bubbleY: () -> Double,
    isLevel: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val ringStroke = with(density) { dimensionResource(R.dimen.level_ring_stroke).toPx() }
    val crosshair = with(density) { dimensionResource(R.dimen.level_crosshair).toPx() }
    val bubbleRadius = with(density) { dimensionResource(R.dimen.level_bubble_radius).toPx() }

    // Read once here rather than inside draw: this one genuinely should recompose, because the
    // animation target changes rarely and the animation itself drives its own frames.
    val level by animateFloatAsState(
        targetValue = if (isLevel()) 1f else 0f,
        label = "level",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .drawBehind {
                val deviation = hypot(bubbleX(), bubbleY())
                val scale = scaleFor(deviation)

                drawVial(scheme.surfaceContainerHighest, scheme.outlineVariant, ringStroke)
                drawToleranceRing(
                    color = lerpColor(scheme.outline, scheme.primary, level),
                    scale = scale,
                    stroke = ringStroke,
                )
                drawCrosshair(scheme.outlineVariant, crosshair)
                drawBubble(
                    x = bubbleX(),
                    y = bubbleY(),
                    scale = scale,
                    radius = bubbleRadius,
                    color = lerpColor(scheme.tertiary, scheme.primary, level),
                )
            },
    )
}

/** The glass: a filled circle with a rim. */
private fun DrawScope.drawVial(fill: Color, rim: Color, stroke: Float) {
    val radius = size.minDimension / 2f - stroke
    drawCircle(color = fill, radius = radius)
    drawCircle(color = rim, radius = radius, style = Stroke(width = stroke))
}

/**
 * The ring the bubble has to sit inside to count as level.
 *
 * Sized from the same scale the bubble moves on, so "inside the ring" and "the number says level"
 * can never disagree — the alternative is a bubble that is visibly centred while the readout says
 * otherwise, which destroys trust in both.
 */
private fun DrawScope.drawToleranceRing(color: Color, scale: Float, stroke: Float) {
    val toleranceOffset = kotlin.math.sin(Math.toRadians(TOLERANCE_DEGREES)).toFloat()
    val radius = toleranceOffset * scale * (size.minDimension / 2f)

    drawCircle(color = color, radius = radius.coerceAtLeast(stroke * 2), style = Stroke(stroke))
}

private fun DrawScope.drawCrosshair(color: Color, stroke: Float) {
    val half = size.minDimension / 2f
    drawLine(
        color = color,
        start = Offset(center.x - half, center.y),
        end = Offset(center.x + half, center.y),
        strokeWidth = stroke,
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - half),
        end = Offset(center.x, center.y + half),
        strokeWidth = stroke,
    )
}

private fun DrawScope.drawBubble(
    x: Double,
    y: Double,
    scale: Float,
    radius: Float,
    color: Color,
) {
    val extent = size.minDimension / 2f - radius
    val offsetX = (x * scale).toFloat() * extent
    // Negated: the canvas grows downward, the device's y-axis grows up the screen.
    val offsetY = -(y * scale).toFloat() * extent

    val clamped = clampToCircle(offsetX, offsetY, extent)

    drawCircle(
        color = color,
        radius = radius,
        center = center + clamped,
    )
}

/** Keeps the bubble inside the glass, sliding around the rim rather than escaping it. */
private fun clampToCircle(x: Float, y: Float, extent: Float): Offset {
    val distance = hypot(x, y)
    if (distance <= extent || distance == 0f) return Offset(x, y)

    val factor = extent / distance
    return Offset(x * factor, y * factor)
}

/**
 * How much the vial magnifies, given how far off level it currently is.
 *
 * Coarse while the phone is visibly tilted, fine once it is close — so the last fraction of a degree
 * is a movement the eye can see rather than a sub-pixel nudge.
 */
private fun scaleFor(deviation: Double): Float {
    val coarse = 1f / kotlin.math.sin(Math.toRadians(COARSE_RANGE_DEGREES)).toFloat()
    val fine = 1f / kotlin.math.sin(Math.toRadians(FINE_RANGE_DEGREES)).toFloat()

    val fineThreshold = kotlin.math.sin(Math.toRadians(FINE_ENTER_DEGREES))
    if (deviation >= fineThreshold) return coarse

    val progress = (1.0 - deviation / fineThreshold).toFloat()
    return coarse + (fine - coarse) * progress
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction)

/** Matches `LevelState.LEVEL_TOLERANCE_DEGREES`; the ring is that band drawn. */
private const val TOLERANCE_DEGREES = 0.2

private const val COARSE_RANGE_DEGREES = 10.0
private const val FINE_RANGE_DEGREES = 0.5
private const val FINE_ENTER_DEGREES = 1.0
