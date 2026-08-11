package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.presentation.SoundMeterState

/**
 * The last minute, as a strip.
 *
 * Usually the most useful thing on the screen: a single number cannot tell you whether a room is
 * steadily noisy or whether it is quiet with spikes, and those call for completely different
 * responses.
 *
 * ## Gaps are drawn as gaps
 *
 * A stretch that could not be measured — clipped, or below the noise floor — arrives as `null` and
 * breaks the line rather than being interpolated over. Joining across it would draw a smooth,
 * confident line through the exact moment the meter had nothing to say, and the loudest part of a
 * session is the most likely part to be missing.
 *
 * ## The threshold is a line on the chart, not just on the gauge
 *
 * Everything above 85 dB is shaded. Time above the limit is what actually matters for exposure —
 * a brief peak and a sustained level look identical as a maximum, and completely different here.
 */
@Composable
internal fun HistoryChart(
    history: State<List<Double?>>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val lineStroke = with(density) { dimensionResource(R.dimen.soundmeter_chart_stroke).toPx() }
    val gridStroke = with(density) { dimensionResource(R.dimen.soundmeter_chart_grid).toPx() }
    val description = stringResource(R.string.soundmeter_chart_description)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.soundmeter_chart_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.soundmeter_chart_height))
                // One description for the whole strip. Six hundred points read aloud individually
                // would be unusable, and the shape is what the chart is for.
                .semantics { contentDescription = description }
                .drawBehind {
                    drawThresholdBand(scheme.error.copy(alpha = THRESHOLD_ALPHA))
                    drawThresholdLine(scheme.error, gridStroke)
                    drawTrace(history.value, scheme.primary, lineStroke)
                },
        )
    }
}

// region Drawing

/** Everything at or above the exposure limit, tinted. */
private fun DrawScope.drawThresholdBand(color: Color) {
    val top = yFor(SoundMeterState.MAX_DISPLAY_DB)
    val bottom = yFor(SoundReference.EXPOSURE_THRESHOLD_DB)

    drawRect(
        color = color,
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
    )
}

private fun DrawScope.drawThresholdLine(color: Color, stroke: Float) {
    val y = yFor(SoundReference.EXPOSURE_THRESHOLD_DB)

    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
    )
}

/**
 * The trace, as one path per unbroken run of measurements.
 *
 * A new sub-path is started after every gap rather than moving the pen through it, which is what
 * makes an unmeasurable stretch visibly absent instead of silently bridged.
 */
private fun DrawScope.drawTrace(points: List<Double?>, color: Color, stroke: Float) {
    if (points.isEmpty()) return

    val path = Path()
    var penDown = false

    // Anchored to the full window rather than to the number of points held, so the trace grows in
    // from the left as a session starts instead of stretching to fill the width and appearing to
    // rewrite its own history.
    val step = size.width / (SoundMeterState.HISTORY_POINTS - 1).toFloat()
    val firstIndex = SoundMeterState.HISTORY_POINTS - points.size

    points.forEachIndexed { index, value ->
        if (value == null) {
            penDown = false
            return@forEachIndexed
        }

        val x = (firstIndex + index) * step
        val y = yFor(value)

        if (penDown) path.lineTo(x, y) else path.moveTo(x, y)
        penDown = true
    }

    drawPath(path = path, color = color, style = Stroke(width = stroke))
}

/** Where a level sits vertically, clamped so an out-of-scale value pins to an edge. */
private fun DrawScope.yFor(dbSpl: Double): Float {
    val span = SoundMeterState.MAX_DISPLAY_DB - SoundMeterState.MIN_DISPLAY_DB
    val fraction = ((dbSpl - SoundMeterState.MIN_DISPLAY_DB) / span).toFloat().coerceIn(0f, 1f)
    return size.height * (1f - fraction)
}

// endregion

private const val THRESHOLD_ALPHA = 0.12f
private const val DASH_ON = 8f
private const val DASH_OFF = 8f
