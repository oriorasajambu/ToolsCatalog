package com.minion.scaffold.feature.speedometer.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.gnss.model.CoordinateFormatter
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.core.gnss.usecase.TripStats
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.presentation.SpeedometerState

/**
 * Altitude, rate of climb and position.
 *
 * The altitude carries a note saying which datum it is in. That is not pedantry: a receiver's raw
 * figure is a height above a mathematical ellipsoid and differs from sea level by tens of metres in
 * most of the world, so a hiker comparing against a trail sign needs to know which of the two they
 * are looking at.
 */
@Composable
internal fun PositionPanel(
    reading: SpeedometerState.Reading.Live,
    distanceUnit: DistanceUnit,
    coordinateFormat: com.minion.scaffold.core.gnss.model.CoordinateFormat,
    rateOfClimbMetersPerMinute: Double?,
    onCopy: () -> Unit,
    onOpenInMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.speedometer_spacing_tight),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.speedometer_altitude),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = reading.altitudeMeters?.let { meters ->
                        val converted = distanceUnit.altitudeFromMeters(meters)
                        when (distanceUnit) {
                            DistanceUnit.Metric ->
                                stringResource(R.string.speedometer_altitude_meters, converted)

                            DistanceUnit.Imperial ->
                                stringResource(R.string.speedometer_altitude_feet, converted)
                        }
                    } ?: stringResource(R.string.speedometer_altitude_unavailable),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = stringResource(R.string.speedometer_altitude_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Only where a pressure sensor exists. The barometer is precise for a *change* in height
            // and knows nothing absolute, so it contributes the rate and never the altitude itself.
            rateOfClimbMetersPerMinute?.let { rate ->
                Text(
                    text = stringResource(R.string.speedometer_rate_of_climb, rate),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.speedometer_position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = CoordinateFormatter.format(
                    reading.latitude,
                    reading.longitude,
                    coordinateFormat,
                ),
                style = MaterialTheme.typography.bodyMedium,
                // Monospace so the digits line up and can be read off one at a time.
                fontFamily = FontFamily.Monospace,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                TextButton(onClick = onCopy) {
                    Text(text = stringResource(R.string.speedometer_copy))
                }
                TextButton(onClick = onOpenInMaps) {
                    Text(text = stringResource(R.string.speedometer_open_in_maps))
                }
            }
        }
    }
}

/**
 * The trip, with every figure gated on movement.
 *
 * Distance is the integral of the measured speed rather than a sum of position steps — see
 * `AccumulateTripUseCase` for the two ways the obvious implementation is wrong.
 */
@Composable
internal fun TripPanel(
    trip: TripStats,
    measuring: Boolean,
    speedUnit: SpeedUnit,
    distanceUnit: DistanceUnit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.speedometer_trip_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (measuring) R.string.speedometer_trip_running
                        else R.string.speedometer_trip_held,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Stat(
                    labelRes = R.string.speedometer_trip_distance,
                    value = distanceText(trip.distanceMeters, distanceUnit),
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    labelRes = R.string.speedometer_trip_average,
                    value = trip.averageSpeedMetersPerSecond?.let { speedText(it, speedUnit) },
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    labelRes = R.string.speedometer_trip_max,
                    value = trip.maxSpeedMetersPerSecond?.let { speedText(it, speedUnit) },
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    labelRes = R.string.speedometer_trip_climb,
                    value = if (trip.hasMeasurement) {
                        altitudeText(trip.elevationGainMeters, distanceUnit)
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(
                    R.string.speedometer_trip_duration,
                    trip.durationSeconds.toInt() / SECONDS_PER_MINUTE,
                    trip.durationSeconds.toInt() % SECONDS_PER_MINUTE,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                Button(
                    onClick = if (measuring) onStop else onStart,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (measuring) R.string.speedometer_stop
                            else R.string.speedometer_start,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onReset,
                    // Enabled only when there is something to clear, so the control is never a no-op
                    // that leaves someone wondering whether it worked.
                    enabled = trip.hasMeasurement,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.speedometer_reset))
                }
            }
        }
    }
}

@Composable
private fun Stat(
    labelRes: Int,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value ?: stringResource(R.string.speedometer_stat_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun speedText(metersPerSecond: Double, unit: SpeedUnit): String =
    stringResource(R.string.speedometer_speed_value, unit.fromMetersPerSecond(metersPerSecond))

@Composable
private fun distanceText(meters: Double, unit: DistanceUnit): String {
    val converted = unit.journeyFromMeters(meters)
    return when (unit) {
        DistanceUnit.Metric -> stringResource(R.string.speedometer_distance_km, converted)
        DistanceUnit.Imperial -> stringResource(R.string.speedometer_distance_miles, converted)
    }
}

@Composable
private fun altitudeText(meters: Double, unit: DistanceUnit): String {
    val converted = unit.altitudeFromMeters(meters)
    return when (unit) {
        DistanceUnit.Metric -> stringResource(R.string.speedometer_altitude_meters, converted)
        DistanceUnit.Imperial -> stringResource(R.string.speedometer_altitude_feet, converted)
    }
}

private const val SECONDS_PER_MINUTE = 60
