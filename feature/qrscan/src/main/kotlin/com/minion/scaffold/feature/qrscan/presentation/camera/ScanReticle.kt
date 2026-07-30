package com.minion.scaffold.feature.qrscan.presentation.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.material3.MaterialTheme
import com.minion.scaffold.feature.qrscan.R

/** Whether a code is visible, and whether it is the one being aimed at. */
internal enum class AimState {

    /** No code in frame. */
    Searching,

    /** A code is visible but not in the reticle — the state the box exists to make obvious. */
    OffTarget,

    /** A code is aimed at and about to be read. */
    Locked,
}

/**
 * The aiming box, and a scrim over everything outside it.
 *
 * Takes [reticle] rather than computing it, so the rectangle drawn here is the identical one
 * [isAimed] is tested against — deriving it twice is how a box comes to reject a code sitting
 * visibly inside it.
 *
 * The colour is animated because the transition is the feedback: a box that snaps between grey and
 * red on frame-rate detection jitter reads as flicker rather than as guidance.
 */
@Composable
internal fun ScanReticle(
    reticle: Rect,
    aim: AimState,
    modifier: Modifier = Modifier,
) {
    val outline by animateColorAsState(
        targetValue = when (aim) {
            AimState.Searching -> MaterialTheme.colorScheme.onSurfaceVariant
            AimState.OffTarget -> MaterialTheme.colorScheme.error
            AimState.Locked -> LOCKED_COLOR
        },
        label = "reticle",
    )

    val strokeWidth = with(LocalDensity.current) {
        dimensionResource(R.dimen.qrscan_reticle_stroke).toPx()
    }
    val cornerRadius = with(LocalDensity.current) {
        dimensionResource(R.dimen.qrscan_reticle_corner).toPx()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (reticle.isEmpty) return@Canvas

        val rounded = RoundRect(reticle, CornerRadius(cornerRadius))
        val hole = Path().apply { addRoundRect(rounded) }

        // Difference clip rather than four rectangles around the box: with rounded corners, four
        // rectangles leave the corner cut-outs unscrimmed and the box looks like it has notches.
        clipPath(hole, clipOp = ClipOp.Difference) {
            drawRect(color = SCRIM_COLOR)
        }

        drawRoundRect(
            color = outline,
            topLeft = Offset(reticle.left, reticle.top),
            size = reticle.size,
            cornerRadius = CornerRadius(cornerRadius),
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * Fixed colours, deliberately not theme ones.
 *
 * These sit over a live camera feed rather than a themed surface, so they answer to the image
 * behind them and not to the palette — a scrim taken from `surface` disappears against a dark wall,
 * and a "locked" colour from `primary` would change meaning with the theme.
 */
private val SCRIM_COLOR = Color.Black.copy(alpha = 0.55f)
private val LOCKED_COLOR = Color(0xFF4CAF50)
