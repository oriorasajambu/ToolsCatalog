package com.minion.scaffold.core.camera

import android.graphics.PointF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import kotlinx.coroutines.delay

/**
 * A ring where the user last tapped to focus.
 *
 * Without it a tap gives no feedback at all and reads as a dead touch — the image sharpens a moment
 * later with nothing to connect it to. [tapPoint] comes from the camera controller, already in
 * viewfinder coordinates, so no mapping is needed.
 *
 * @param tapPoint Where the user last tapped, in viewfinder coordinates, or `null` for no ring.
 * @param modifier The [Modifier] for the drawing canvas.
 */
@Composable
internal fun FocusRing(
    tapPoint: PointF?,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(tapPoint) {
        val point = tapPoint ?: return@LaunchedEffect
        shown = Offset(point.x, point.y)
        delay(VISIBLE_MILLIS)
        shown = null
    }

    val alpha by animateFloatAsState(
        targetValue = if (shown == null) 0f else 1f,
        label = "focusRing",
    )

    val radius = with(LocalDensity.current) {
        dimensionResource(R.dimen.camera_focus_ring_radius).toPx()
    }
    val strokeWidth = with(LocalDensity.current) {
        dimensionResource(R.dimen.camera_focus_ring_stroke).toPx()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centre = shown ?: return@Canvas

        drawCircle(
            color = RING_COLOR.copy(alpha = alpha),
            radius = radius,
            center = centre,
            style = Stroke(width = strokeWidth),
        )
    }
}

/** Long enough to connect the ring to the tap, short enough not to sit over the subject. */
private const val VISIBLE_MILLIS = 800L

/** Over a live camera feed, so a fixed colour rather than a theme one. */
private val RING_COLOR = Color.White
