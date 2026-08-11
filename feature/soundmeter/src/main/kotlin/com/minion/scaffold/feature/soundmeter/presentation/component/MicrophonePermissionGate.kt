package com.minion.scaffold.feature.soundmeter.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.soundmeter.R

/**
 * The microphone gate.
 *
 * `Denied` and `PermanentlyDenied` get different buttons because the recovery genuinely differs —
 * one is another request away and the other can only be undone in Settings. Offering the wrong one
 * either wastes a tap or sends someone hunting through Settings when a dialog would have done.
 *
 * The rationale states plainly that no audio is recorded or stored, mirroring the scanner's camera
 * wording. A microphone is the permission people hesitate over most, and the honest answer is short:
 * each block of samples becomes a number and is dropped, nothing is written anywhere, nothing leaves
 * the device. That sentence is a promise the implementation has to keep, not marketing — see
 * `AudioRecordSource`.
 *
 * Unlike the scanner, there is no fallback to offer. A QR tool without a camera can still decode a
 * pasted payload; a sound meter without a microphone has nothing to measure, so this says so rather
 * than inventing a lesser mode.
 */
@Composable
internal fun MicrophonePermissionGate(
    permission: PermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Unknown renders nothing at all: anything drawn here flashes behind the system dialog on the
    // way in, which reads as the screen failing before it has even asked.
    if (permission == PermissionState.Unknown || permission == PermissionState.Granted) return

    val spacing = dimensionResource(R.dimen.soundmeter_spacing)
    val blocked = permission == PermissionState.PermanentlyDenied

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(
                    if (blocked) {
                        R.string.soundmeter_permission_blocked
                    } else {
                        R.string.soundmeter_permission_rationale
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = if (blocked) onOpenSettings else onRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (blocked) {
                            R.string.soundmeter_permission_open_settings
                        } else {
                            R.string.soundmeter_permission_grant
                        },
                    ),
                )
            }
        }
    }
}
