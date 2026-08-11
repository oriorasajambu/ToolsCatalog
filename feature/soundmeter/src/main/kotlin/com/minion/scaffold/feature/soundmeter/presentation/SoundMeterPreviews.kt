package com.minion.scaffold.feature.soundmeter.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.sound.model.SessionStats
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.presentation.component.HistoryChart
import com.minion.scaffold.feature.soundmeter.presentation.component.SessionPanel
import com.minion.scaffold.feature.soundmeter.presentation.component.SoundGauge
import kotlin.math.sin

/**
 * `internal`, not `private` — Showkase cannot call a private function, and the Compose convention
 * sets `skipPrivatePreviews`, so a private one would silently vanish from the catalog rather than
 * failing the build.
 */
@Preview
@Composable
internal fun SoundGaugeLevelPreview() {
    AppTheme {
        val reading = remember {
            mutableStateOf<SoundMeterState.Reading>(SoundMeterState.Reading.Level(68.4))
        }
        SoundGauge(reading = reading)
    }
}

/**
 * The state worth having a preview of at all: the number is gone, replaced by why it is gone.
 *
 * Rendering this beside the ordinary reading is the check that the two are visibly different — the
 * whole point of clipping detection is defeated if "too loud" looks like a slightly unusual number.
 */
@Preview
@Composable
internal fun SoundGaugeTooLoudPreview() {
    AppTheme {
        val reading = remember {
            mutableStateOf<SoundMeterState.Reading>(SoundMeterState.Reading.TooLoud)
        }
        SoundGauge(reading = reading)
    }
}

/** A trace with a gap in it, which is how an unmeasurable stretch has to look. */
@Preview
@Composable
internal fun HistoryChartPreview() {
    AppTheme {
        val history = remember {
            mutableStateOf(
                List(SoundMeterState.HISTORY_POINTS) { index ->
                    when {
                        index in GAP_RANGE -> null
                        else -> 62.0 + 24.0 * sin(index / WAVE_PERIOD)
                    }
                },
            )
        }
        HistoryChart(
            history = history,
            modifier = Modifier.padding(dimensionResource(R.dimen.soundmeter_spacing)),
        )
    }
}

@Preview
@Composable
internal fun SessionPanelPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.soundmeter_spacing)),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.soundmeter_spacing),
            ),
        ) {
            SessionPanel(
                stats = SessionStats(
                    minDbSpl = 41.2,
                    maxDbSpl = 92.7,
                    leqDbSpl = 68.4,
                    durationSeconds = 312.0,
                    secondsAboveThreshold = 47.0,
                    unmeasurableSeconds = 6.0,
                ),
                measuring = true,
            )
        }
    }
}

private val GAP_RANGE = 220..300
private const val WAVE_PERIOD = 40.0
