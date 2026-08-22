package com.minion.scaffold.feature.speedometer.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.presentation.SpeedometerChrome
import com.minion.scaffold.feature.speedometer.presentation.SpeedometerIntent
import com.minion.scaffold.feature.speedometer.presentation.SpeedometerState

/**
 * The location gate, which has four outcomes rather than two.
 *
 * Denied and permanently denied are the familiar pair — one is another request away, the other needs
 * Settings. The two additions both matter:
 *
 *  - **Approximate granted.** Since Android 12 the system dialog offers Precise or Approximate and
 *    the user chooses. Approximate is coarsened to roughly a city block *and re-coarsened on every
 *    request*, so consecutive readings jump around a grid; a speed derived from them is not merely
 *    imprecise, it is meaningless. Treating this as a denial would tell someone who granted something
 *    that they granted nothing, so it gets its own explanation and an upgrade button.
 *  - **Location switched off entirely.** Nothing to do with permission, and the recovery is a
 *    different Settings screen. Telling someone to grant a permission they already granted is the
 *    kind of dead end that gets an app uninstalled.
 */
@Composable
internal fun LocationGate(
    chrome: SpeedometerChrome,
    onRequestPermission: () -> Unit,
    onIntent: (SpeedometerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val access = chrome.access
    // Unknown draws nothing at all: anything here flashes behind the system dialog on the way in,
    // which reads as the screen failing before it has even asked.
    if (access == SpeedometerState.LocationAccess.Unknown) return

    val message: Int
    val action: Int
    val onClick: () -> Unit

    when {
        access == SpeedometerState.LocationAccess.Approximate -> {
            message = R.string.speedometer_permission_approximate
            action = R.string.speedometer_permission_upgrade
            onClick = onRequestPermission
        }

        access == SpeedometerState.LocationAccess.Denied -> {
            message = R.string.speedometer_permission_rationale
            action = R.string.speedometer_permission_grant
            onClick = onRequestPermission
        }

        access == SpeedometerState.LocationAccess.PermanentlyDenied -> {
            message = R.string.speedometer_permission_blocked
            action = R.string.speedometer_permission_open_settings
            onClick = { onIntent(SpeedometerIntent.AppSettingsRequested) }
        }

        !chrome.providerEnabled -> {
            message = R.string.speedometer_provider_disabled
            action = R.string.speedometer_provider_open_settings
            onClick = { onIntent(SpeedometerIntent.LocationSettingsRequested) }
        }

        else -> return
    }

    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(action))
            }
        }
    }
}
