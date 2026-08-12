package com.minion.scaffold.core.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/**
 * The zoom ratios this control offers, before the lens has its say.
 *
 * Steps rather than a slider: reading something at arm's length or across a room are the two cases,
 * and a tap gets there faster than dragging. Pinch remains available for anything between.
 */
private val ZOOM_STEPS = listOf(1f, 2f, 5f)

/**
 * Stepped zoom over the viewfinder.
 *
 * Steps beyond [maxRatio] are **omitted, not disabled**: a ×5 button on a phone whose lens stops at
 * ×3 is a control that lies about what the hardware can do, and a greyed-out one invites the
 * question of how to enable it.
 *
 * @param currentRatio The lens's current zoom ratio, for highlighting the nearest step.
 * @param maxRatio     The lens's maximum zoom ratio; steps above it are omitted.
 * @param onSelectRatio Called with the chosen ratio when a step is tapped.
 * @param modifier     The [Modifier] for the control row.
 */
@Composable
internal fun ZoomControls(
    currentRatio: Float,
    maxRatio: Float,
    onSelectRatio: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = ZOOM_STEPS.filter { it <= maxRatio }

    // One reachable step is no choice at all, so the row disappears rather than showing a single
    // button that does nothing.
    if (steps.size < 2) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.camera_spacing_tight),
        ),
    ) {
        for (step in steps) {
            ZoomStep(
                ratio = step,
                selected = step.isNearest(currentRatio, steps),
                onClick = { onSelectRatio(step) },
            )
        }
    }
}

@Composable
private fun ZoomStep(ratio: Float, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.camera_zoom_step, ratio.formatted())
    val description = stringResource(R.string.camera_zoom_select, ratio.formatted())

    val content: @Composable () -> Unit = {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }

    if (selected) {
        FilledIconButton(onClick = onClick, modifier = Modifier.semantics {
            contentDescription = description
        }) { content() }
    } else {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.semantics {
            contentDescription = description
        }) { content() }
    }
}

/**
 * Whether this step is the one the current ratio belongs to.
 *
 * Pinching lands on arbitrary ratios, so exact equality would leave no step marked for most of the
 * zoom range. The nearest step is highlighted instead, which keeps the row showing roughly where
 * the lens is rather than only when it is exactly on a stop.
 */
private fun Float.isNearest(currentRatio: Float, steps: List<Float>): Boolean =
    steps.minByOrNull { abs(it - currentRatio) } == this

/** `1` rather than `1.0`, and `1.5` when a step ever needs a fraction. */
private fun Float.formatted(): String =
    if (this == toInt().toFloat()) toInt().toString() else toString()
