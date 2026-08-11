package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.sound.model.SessionStats
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.R

/**
 * What the session came to: Min, LAeq, Max — and the caveats that make them mean something.
 *
 * **There is one average and it is called LAeq**, not "Avg". An arithmetic mean of decibel values is
 * not a quantity; averaging exponents gives a number that is stable, plausible and meaningless.
 * Naming it properly is what makes the value on screen match the one in every noise regulation, and
 * it is a small enough word to look up.
 *
 * The two footnotes appear only when they apply, and both change how the numbers should be read.
 * Time out of range says the Leq is an average over less than the whole session; time above the
 * exposure limit is the figure that actually matters for hearing, since a brief peak and a sustained
 * level are indistinguishable as a maximum.
 */
@Composable
internal fun SessionPanel(
    stats: SessionStats,
    measuring: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.soundmeter_spacing_tight),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.soundmeter_session_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        // Held, not paused: the live gauge above is still moving, so a label
                        // saying only "stopped" would look like the whole screen had frozen.
                        if (measuring) R.string.soundmeter_session_running
                        else R.string.soundmeter_session_held,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                StatCell(
                    labelRes = R.string.soundmeter_stat_min,
                    value = stats.minDbSpl,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    labelRes = R.string.soundmeter_stat_leq,
                    value = stats.leqDbSpl,
                    emphasised = true,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    labelRes = R.string.soundmeter_stat_max,
                    value = stats.maxDbSpl,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(
                    R.string.soundmeter_session_duration,
                    stats.durationSeconds.toInt() / SECONDS_PER_MINUTE,
                    stats.durationSeconds.toInt() % SECONDS_PER_MINUTE,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stats.secondsAboveThreshold > 0.0) {
                Text(
                    text = stringResource(
                        R.string.soundmeter_session_above_threshold,
                        SoundReference.EXPOSURE_THRESHOLD_DB.toInt(),
                        stats.secondsAboveThreshold.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (stats.unmeasurableSeconds > 0.0) {
                Text(
                    text = stringResource(
                        R.string.soundmeter_session_unmeasurable,
                        stats.unmeasurableSeconds.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One figure. An em dash rather than a zero when there is nothing to show — see [SessionStats]. */
@Composable
private fun StatCell(
    labelRes: Int,
    value: Double?,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false,
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
            text = value
                ?.let { stringResource(R.string.soundmeter_level_value, it) }
                ?: stringResource(R.string.soundmeter_stat_empty),
            style = if (emphasised) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
    }
}

private const val SECONDS_PER_MINUTE = 60
