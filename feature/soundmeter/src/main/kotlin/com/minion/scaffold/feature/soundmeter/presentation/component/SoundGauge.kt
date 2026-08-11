package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.presentation.SoundMeterState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The level as an arc, with the figure at its centre.
 *
 * ## Reading state in the draw phase
 *
 * The reading arrives as a `State<T>` **read inside [Modifier.drawBehind]**, so the read is deferred
 * to the draw phase and a new block redraws the arc without recomposing or re-laying-out anything.
 * At roughly 47 blocks a second the difference is not academic. Same construction, and same reason,
 * as `BullseyeLevel`.
 *
 * ## The number is quantised, and that is the throttle
 *
 * The centre figure goes through [derivedStateOf] on the value rounded to a tenth of a dB, which
 * does two jobs at once: it stops the text recomposing at block rate, and it gives the display a
 * deadband so the last digit is not a permanent blur. Text cannot be drawn in `drawBehind` without a
 * `TextMeasurer`, and it should not be — it is the one part of this that genuinely needs layout.
 *
 * ## The arc answers "is this bad?" before the number does
 *
 * Bands from [NoiseBand], which exist so the colours are backed by published figures rather than by
 * taste. The threshold tick is drawn separately and always, even when the reading is well below it,
 * because its position is the context that makes the rest of the arc mean something.
 */
@Composable
internal fun SoundGauge(
    reading: State<SoundMeterState.Reading>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    val arcStroke = with(density) { dimensionResource(R.dimen.soundmeter_arc_stroke).toPx() }
    val tickLength = with(density) { dimensionResource(R.dimen.soundmeter_tick_length).toPx() }

    val bandColours = listOf(
        scheme.tertiary,
        scheme.primary,
        scheme.secondary,
        scheme.error,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GAUGE_ASPECT)
            .drawBehind {
                drawTrack(scheme.surfaceContainerHighest, arcStroke)
                drawBands(bandColours, arcStroke)
                drawThreshold(scheme.onSurfaceVariant, arcStroke, tickLength)

                // The one read of the live value, and it happens here in the draw phase rather
                // than in the composable body — which is what keeps a new block from recomposing
                // and re-laying-out this subtree forty-seven times a second.
                (reading.value as? SoundMeterState.Reading.Level)
                    ?.let { drawNeedle(scheme.onSurface, it.dbSpl, arcStroke) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        GaugeReadout(reading = reading)
    }
}

/**
 * The figure and its unit, or the reason there is no figure.
 *
 * The out-of-range states **replace** the number rather than annotating it. A badge beside a
 * plausible-looking value gets read past; the whole point of detecting clipping is that the number
 * underneath it is not a smaller version of the truth, it is a different number altogether.
 */
@Composable
private fun GaugeReadout(
    reading: State<SoundMeterState.Reading>,
    modifier: Modifier = Modifier,
) {
    // Quantised to a tenth of a dB before it reaches composition. `Level` is a data class, so
    // `derivedStateOf` only notifies when the *rounded* value changes — which is both the throttle
    // on this subtree and the deadband that stops the last digit blurring into a smear.
    val current by remember(reading) {
        derivedStateOf {
            when (val value = reading.value) {
                is SoundMeterState.Reading.Level ->
                    SoundMeterState.Reading.Level(
                        (value.dbSpl * TENTHS).roundToInt() / TENTHS,
                    )

                else -> value
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val display = current) {
            is SoundMeterState.Reading.Level -> {
                Text(
                    text = stringResource(R.string.soundmeter_level_value, display.dbSpl),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(NoiseBand.of(display.dbSpl).labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SoundMeterState.Reading.TooLoud -> OutOfRange(
                headlineRes = R.string.soundmeter_too_loud,
                detailRes = R.string.soundmeter_too_loud_detail,
                color = MaterialTheme.colorScheme.error,
            )

            SoundMeterState.Reading.TooQuiet -> OutOfRange(
                headlineRes = R.string.soundmeter_too_quiet,
                detailRes = R.string.soundmeter_too_quiet_detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SoundMeterState.Reading.Waiting -> Text(
                text = stringResource(R.string.soundmeter_waiting),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutOfRange(
    headlineRes: Int,
    detailRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(headlineRes),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(detailRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// region Drawing

private fun DrawScope.drawTrack(color: Color, stroke: Float) {
    drawArc(
        color = color,
        startAngle = START_ANGLE,
        sweepAngle = SWEEP_ANGLE,
        useCenter = false,
        topLeft = arcTopLeft(stroke),
        size = arcSize(stroke),
        style = Stroke(width = stroke),
    )
}

/** One segment per band, each spanning the fraction of the scale that band actually occupies. */
private fun DrawScope.drawBands(colours: List<Color>, stroke: Float) {
    var lowerDb = SoundMeterState.MIN_DISPLAY_DB

    NoiseBand.entries.forEachIndexed { index, band ->
        val upperDb = band.upperDb ?: SoundMeterState.MAX_DISPLAY_DB
        val start = START_ANGLE + fractionOf(lowerDb) * SWEEP_ANGLE
        val sweep = (fractionOf(upperDb) - fractionOf(lowerDb)) * SWEEP_ANGLE

        drawArc(
            color = colours[index % colours.size],
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft(stroke),
            size = arcSize(stroke),
            style = Stroke(width = stroke * BAND_STROKE_FRACTION),
        )

        lowerDb = upperDb
    }
}

/** The 85 dB mark, drawn whatever the reading — its position is what gives the arc meaning. */
private fun DrawScope.drawThreshold(color: Color, stroke: Float, length: Float) {
    val angle = START_ANGLE +
        fractionOf(SoundReference.EXPOSURE_THRESHOLD_DB) * SWEEP_ANGLE
    val radians = Math.toRadians(angle.toDouble())
    val radius = arcRadius(stroke)
    val centre = arcCentre()

    val inner = radius - stroke / 2f - length
    val outer = radius + stroke / 2f

    drawLine(
        color = color,
        start = Offset(
            centre.x + (inner * cos(radians)).toFloat(),
            centre.y + (inner * sin(radians)).toFloat(),
        ),
        end = Offset(
            centre.x + (outer * cos(radians)).toFloat(),
            centre.y + (outer * sin(radians)).toFloat(),
        ),
        strokeWidth = stroke * THRESHOLD_STROKE_FRACTION,
    )
}

private fun DrawScope.drawNeedle(color: Color, dbSpl: Double, stroke: Float) {
    val angle = START_ANGLE + fractionOf(dbSpl) * SWEEP_ANGLE
    val radians = Math.toRadians(angle.toDouble())
    val radius = arcRadius(stroke)
    val centre = arcCentre()

    drawCircle(
        color = color,
        radius = stroke * NEEDLE_RADIUS_FRACTION,
        center = Offset(
            centre.x + (radius * cos(radians)).toFloat(),
            centre.y + (radius * sin(radians)).toFloat(),
        ),
    )
}

/** Where [dbSpl] sits on the scale, 0..1, clamped so an out-of-scale value pins to an end. */
private fun fractionOf(dbSpl: Double): Float {
    val span = SoundMeterState.MAX_DISPLAY_DB - SoundMeterState.MIN_DISPLAY_DB
    return (((dbSpl - SoundMeterState.MIN_DISPLAY_DB) / span).toFloat()).coerceIn(0f, 1f)
}

private fun DrawScope.arcRadius(stroke: Float) = (size.width - stroke) / 2f

private fun DrawScope.arcCentre() = Offset(size.width / 2f, size.width / 2f)

private fun DrawScope.arcTopLeft(stroke: Float) = Offset(stroke / 2f, stroke / 2f)

private fun DrawScope.arcSize(stroke: Float) =
    Size(size.width - stroke, size.width - stroke)

// endregion

/** Half a circle, opening upwards: 180° round to 360°. */
private const val START_ANGLE = 180f
private const val SWEEP_ANGLE = 180f

/** Taller than a half-circle so the readout has room beneath the arc. */
private const val GAUGE_ASPECT = 1.55f

private const val BAND_STROKE_FRACTION = 0.45f
private const val THRESHOLD_STROKE_FRACTION = 0.28f
private const val NEEDLE_RADIUS_FRACTION = 0.62f

/** Rounding granularity for the displayed figure — a tenth of a dB. */
private const val TENTHS = 10.0
