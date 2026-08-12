package com.minion.scaffold.feature.speedometer.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.gnss.model.FixQuality
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.presentation.SpeedometerState
import kotlin.math.roundToInt

/**
 * The number, sized to be read from a car mount.
 *
 * ## Quantised before it reaches composition
 *
 * Fixes arrive at 1 Hz, which is slow enough that the recomposition cost hardly matters — but
 * rounding to whole units still earns its place. A speedometer showing 61.7 then 61.4 then 62.1
 * invites the eye to read the noise; rounding gives the display a deadband, the same reason the sound
 * meter quantises its decibels and the level its degrees.
 *
 * ## Searching is not zero
 *
 * A screen with no fix shows the satellite view rather than a confident 0, because those are entirely
 * different situations and a stationary-looking readout during a cold start is the sort of small lie
 * that costs a measuring tool its credibility.
 */
@Composable
internal fun SpeedReadout(
    reading: State<SpeedometerState.Reading>,
    speedUnit: SpeedUnit,
    fixQuality: FixQuality,
    modifier: Modifier = Modifier,
) {
    val whole by remember(reading, speedUnit) {
        derivedStateOf {
            (reading.value as? SpeedometerState.Reading.Live)
                ?.let { speedUnit.fromMetersPerSecond(it.speedMetersPerSecond).roundToInt() }
        }
    }
    val derived by remember(reading) {
        derivedStateOf {
            (reading.value as? SpeedometerState.Reading.Live)?.speedDerived == true
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.speedometer_spacing_tight),
        ),
    ) {
        Text(
            text = whole?.toString() ?: stringResource(R.string.speedometer_no_reading),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.speedometer_spacing_tight),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(speedUnit.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FixQualityChip(fixQuality)
        }

        if (derived) {
            Text(
                text = stringResource(R.string.speedometer_speed_derived),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * How much to trust the screen, in one word.
 *
 * A single indicator rather than an error bar on every figure: a speedometer is read in glances, and
 * "altitude 412 m ±18, speed 63 km/h ±2" is unreadable at arm's length. The metres are available in
 * the detail panel for anyone who asks a harder question.
 */
@Composable
private fun FixQualityChip(quality: FixQuality, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(quality.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = when (quality) {
            FixQuality.Good -> MaterialTheme.colorScheme.primary
            FixQuality.Usable -> MaterialTheme.colorScheme.onSurfaceVariant
            FixQuality.Poor -> MaterialTheme.colorScheme.error
            FixQuality.None -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@StringRes
internal fun SpeedUnit.labelRes(): Int = when (this) {
    SpeedUnit.KilometersPerHour -> R.string.speedometer_unit_kmh
    SpeedUnit.MilesPerHour -> R.string.speedometer_unit_mph
    SpeedUnit.Knots -> R.string.speedometer_unit_knots
}

@StringRes
private fun FixQuality.labelRes(): Int = when (this) {
    FixQuality.None -> R.string.speedometer_quality_none
    FixQuality.Poor -> R.string.speedometer_quality_poor
    FixQuality.Usable -> R.string.speedometer_quality_usable
    FixQuality.Good -> R.string.speedometer_quality_good
}
