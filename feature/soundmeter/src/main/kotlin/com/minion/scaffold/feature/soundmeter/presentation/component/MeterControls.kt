package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.sound.model.TimeWeighting
import com.minion.scaffold.core.sound.model.Weighting
import com.minion.scaffold.feature.soundmeter.R

/**
 * The two measurement modes, and the session controls.
 *
 * Both selectors live on the measuring screen because they are changed *while looking at the
 * reading* — the whole value of switching A to C is watching the number move. The calibration
 * offset deliberately does not: it is set once and then never touched, and a slider next to a live
 * number invites dragging until the number looks agreeable, which is not calibration.
 */
@Composable
internal fun MeterControls(
    weighting: Weighting,
    timeWeighting: TimeWeighting,
    measuring: Boolean,
    canMeasure: Boolean,
    hasSummary: Boolean,
    onWeightingChange: (Weighting) -> Unit,
    onTimeWeightingChange: (TimeWeighting) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(WEIGHTING_WEIGHT)) {
                Weighting.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == weighting,
                        onClick = { onWeightingChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, Weighting.entries.size),
                    ) {
                        Text(text = stringResource(entry.labelRes()))
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(TIME_WEIGHTING_WEIGHT)) {
                TimeWeighting.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == timeWeighting,
                        onClick = { onTimeWeightingChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index,
                            TimeWeighting.entries.size,
                        ),
                    ) {
                        Text(text = stringResource(entry.labelRes()))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Button(
                onClick = if (measuring) onStop else onStart,
                enabled = canMeasure,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(
                        if (measuring) R.string.soundmeter_stop else R.string.soundmeter_start,
                    ),
                )
            }

            OutlinedButton(
                onClick = onReset,
                // Enabled only when there is something to clear, so the control cannot be a no-op
                // that leaves the user wondering whether it worked.
                enabled = hasSummary,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.soundmeter_reset))
            }
        }

        OutlinedButton(
            onClick = onCopy,
            enabled = hasSummary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.soundmeter_copy_summary))
        }
    }
}

@StringRes
private fun Weighting.labelRes(): Int = when (this) {
    Weighting.A -> R.string.soundmeter_weighting_a
    Weighting.C -> R.string.soundmeter_weighting_c
    Weighting.Z -> R.string.soundmeter_weighting_z
}

@StringRes
private fun TimeWeighting.labelRes(): Int = when (this) {
    TimeWeighting.Fast -> R.string.soundmeter_time_fast
    TimeWeighting.Slow -> R.string.soundmeter_time_slow
}

/** Three options against two, so the rows read as equally weighted rather than equally wide. */
private const val WEIGHTING_WEIGHT = 3f
private const val TIME_WEIGHTING_WEIGHT = 2f
