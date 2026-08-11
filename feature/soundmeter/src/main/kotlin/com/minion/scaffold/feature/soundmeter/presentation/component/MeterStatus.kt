package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.feature.soundmeter.R
import com.minion.scaffold.feature.soundmeter.domain.CaptureFailure
import com.minion.scaffold.feature.soundmeter.domain.CaptureQuality
import com.minion.scaffold.feature.soundmeter.presentation.MeterChrome

/**
 * The always-present lines under the gauge.
 *
 * **These are states, not notifications.** `:feature:level` arrived at this split the hard way: a
 * message that dismisses itself cannot represent a condition the user is still in, and a snackbar
 * saying "another app has the microphone" would vanish while the meter carried on showing a gauge
 * that meant nothing. Anything persistent belongs here; only genuinely one-shot events go to the
 * snackbar.
 *
 * The two permanent lines at the bottom are the feature's honesty budget, and they never go away.
 * The reading is approximate because the microphone's sensitivity is unknowable, and no amount of
 * adjusting the offset changes that — so there is no state in which claiming otherwise would be
 * true, and therefore no condition on showing it.
 */
@Composable
internal fun MeterStatus(
    chrome: MeterChrome,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.soundmeter_spacing_tight),
        ),
    ) {
        when {
            // Two failures, two messages. "Stopped responding" is accurate for a recorder that
            // died mid-session and misleading for a device that has no usable input at all.
            chrome.failure == CaptureFailure.Interrupted -> StatusLine(
                textRes = R.string.soundmeter_status_interrupted,
                color = MaterialTheme.colorScheme.error,
            )

            chrome.failure == CaptureFailure.Unavailable -> StatusLine(
                textRes = R.string.soundmeter_status_unavailable,
                color = MaterialTheme.colorScheme.error,
            )

            chrome.silenced -> StatusLine(
                textRes = R.string.soundmeter_status_silenced,
                color = MaterialTheme.colorScheme.error,
            )

            chrome.quality == CaptureQuality.Processed -> StatusLine(
                textRes = R.string.soundmeter_status_processed,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            chrome.quality == CaptureQuality.VoiceRecognition -> StatusLine(
                textRes = R.string.soundmeter_status_voice_recognition,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StatusLine(
            textRes = R.string.soundmeter_disclaimer_approximate,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusLine(
            textRes = R.string.soundmeter_disclaimer_not_certified,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusLine(
    @StringRes textRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
